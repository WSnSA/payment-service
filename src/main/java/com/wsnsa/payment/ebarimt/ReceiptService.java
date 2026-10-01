package com.wsnsa.payment.ebarimt;

import com.wsnsa.payment.common.ApiException;
import com.wsnsa.payment.common.JsonSupport;
import com.wsnsa.payment.merchant.EbarimtSettings;
import com.wsnsa.payment.merchant.Merchant;
import com.wsnsa.payment.merchant.MerchantRepository;
import com.wsnsa.payment.notify.EventTypes;
import com.wsnsa.payment.notify.NotificationService;
import com.wsnsa.payment.payment.Invoice;
import com.wsnsa.payment.provider.ProviderException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * И-баримт үүсгэх, PosAPI руу илгээх, буцаах.
 *
 * <p>Баримт эхлээд PENDING төлөвтэй хадгалагдаж, {@link #issue} нь PosAPI руу илгээнэ.
 * Амжилтгүй бол {@link ReceiptJobs} дахин оролдоно.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptService {

    static final Duration[] BACKOFF = {
            Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15),
            Duration.ofHours(1), Duration.ofHours(3), Duration.ofHours(6)
    };
    static final ZoneId POSAPI_ZONE = ZoneId.of("Asia/Ulaanbaatar");
    private static final DateTimeFormatter POSAPI_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ReceiptRepository receipts;
    private final MerchantRepository merchants;
    private final PosApiClient posApi;
    private final EbarimtInfoClient infoClient;
    private final NotificationService notifications;
    private final JsonSupport json;
    private final TransactionTemplate tx;

    /** Нэхэмжлэх үүсгэх үед: merchant PER_PAYMENT горимтой бол spec-ийг шалгана. */
    public void validateForInvoice(Merchant m, ReceiptSpec spec, BigDecimal amount) {
        if (m.getEbarimt().getMode() != EbarimtMode.PER_PAYMENT) {
            if (spec != null && spec.isEnabled() && (spec.items() != null || spec.customerTin() != null)) {
                throw ApiException.badRequest("ebarimt_per_payment_disabled",
                        "Merchant ebarimt mode is " + m.getEbarimt().getMode() + "; receipt data is not accepted on invoices");
            }
            return;
        }
        ReceiptSpec s = spec == null ? ReceiptSpec.empty() : spec;
        if (s.isEnabled()) {
            ReceiptBuilder.validate(m.getEbarimt(), s, amount);
        }
    }

    /**
     * Нэхэмжлэх төлөгдсөн transaction дотор дуудагдана. PER_PAYMENT горимд PENDING баримт үүсгэнэ.
     *
     * @return үүссэн баримт эсвэл null
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Receipt createForInvoice(Merchant m, Invoice invoice) {
        if (m.getEbarimt().getMode() != EbarimtMode.PER_PAYMENT) return null;
        ReceiptSpec spec = json.read(invoice.getReceiptSpecJson(), ReceiptSpec.class);
        if (spec == null) spec = ReceiptSpec.empty();
        if (!spec.isEnabled()) return null;
        if (receipts.findByInvoiceId(invoice.getId()).isPresent()) return null;

        Receipt r = newReceipt(m.getId(), invoice.getExternalRef(), spec, invoice.getAmount());
        r.setInvoiceId(invoice.getId());
        return receipts.save(r);
    }

    /** Нэхэмжлэхгүй баримт. Үүсгээд шууд нэг удаа илгээхийг оролдоно. */
    public ReceiptResponse createStandalone(String merchantId, CreateReceiptRequest request) {
        Merchant m = merchant(merchantId);
        if (m.getEbarimt().getMode() == EbarimtMode.DISABLED) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "ebarimt_disabled", "Ebarimt is disabled for this merchant");
        }
        var existing = receipts.findByMerchantIdAndExternalRef(merchantId, request.externalRef());
        if (existing.isPresent()) {
            Receipt r = existing.get();
            if (r.getTotalAmount().compareTo(request.amount()) != 0) {
                throw ApiException.conflict("duplicate_external_ref", "Receipt with this externalRef already exists with a different amount");
            }
            return ReceiptResponse.of(r);
        }
        ReceiptBuilder.validate(m.getEbarimt(), request.receipt(), request.amount());

        String id;
        try {
            id = tx.execute(s -> receipts.save(newReceipt(merchantId, request.externalRef(), request.receipt(), request.amount())).getId());
        } catch (DataIntegrityViolationException e) {
            Receipt r = receipts.findByMerchantIdAndExternalRef(merchantId, request.externalRef()).orElseThrow(() -> e);
            return ReceiptResponse.of(r);
        }
        issue(id);
        return ReceiptResponse.of(receipts.findById(id).orElseThrow());
    }

    public ReceiptResponse get(String merchantId, String id) {
        return ReceiptResponse.of(receipts.findByIdAndMerchantId(id, merchantId)
                .orElseThrow(() -> ApiException.notFound("Receipt")));
    }

    public ReceiptResponse getByExternalRef(String merchantId, String externalRef) {
        return ReceiptResponse.of(receipts.findByMerchantIdAndExternalRef(merchantId, externalRef)
                .orElseThrow(() -> ApiException.notFound("Receipt")));
    }

    /** FAILED баримтыг дахин оролдох дараалалд оруулна. */
    public ReceiptResponse retry(String merchantId, String id) {
        tx.executeWithoutResult(s -> {
            Receipt r = receipts.findByIdAndMerchantId(id, merchantId).orElseThrow(() -> ApiException.notFound("Receipt"));
            if (r.getStatus() != ReceiptStatus.FAILED) {
                throw ApiException.conflict("invalid_state", "Only FAILED receipts can be retried (current: " + r.getStatus() + ")");
            }
            r.setStatus(ReceiptStatus.PENDING);
            r.setAttempts(0);
            r.setNextAttemptAt(null);
        });
        issue(id);
        return get(merchantId, id);
    }

    /**
     * PosAPI руу илгээнэ. Зэрэг дуудагдвал зөвхөн нэг нь (PENDING→ISSUING) эрх авна.
     */
    public void issue(String receiptId) {
        Receipt claimed = tx.execute(s -> {
            Receipt r = receipts.findForUpdate(receiptId).orElse(null);
            if (r == null || r.getStatus() != ReceiptStatus.PENDING) return null;
            r.setStatus(ReceiptStatus.ISSUING);
            r.setAttempts(r.getAttempts() + 1);
            return r;
        });
        if (claimed == null) return;

        Merchant m = merchant(claimed.getMerchantId());
        EbarimtSettings settings = m.getEbarimt();
        ReceiptSpec spec = json.read(claimed.getSpecJson(), ReceiptSpec.class);

        PosApiModels.CreateReceiptResponse res;
        ReceiptBuilder.Built built;
        try {
            String consumerNo = spec.consumerNo();
            if (consumerNo != null && !infoClient.isValidConsumer(consumerNo)) {
                log.info("Receipt {}: consumerNo not found in Easy Register, issuing without it", receiptId);
                consumerNo = null;
            }
            built = ReceiptBuilder.build(settings, spec, claimed.getTotalAmount(), null, consumerNo);
            res = posApi.createReceipt(settings, built.request());
        } catch (ProviderException | ApiException e) {
            fail(receiptId, e.getMessage());
            return;
        } catch (RuntimeException e) {
            log.error("Receipt {} issue failed unexpectedly", receiptId, e);
            fail(receiptId, e.getClass().getSimpleName() + ": " + e.getMessage());
            return;
        }

        if (res == null || !"SUCCESS".equals(res.status()) || res.id() == null) {
            fail(receiptId, "PosAPI " + (res == null ? "empty response" : res.status() + ": " + res.message()));
            return;
        }

        final PosApiModels.CreateReceiptResponse ok = res;
        final ReceiptBuilder.Built issued = built;
        tx.executeWithoutResult(s -> {
            Receipt r = receipts.findForUpdate(receiptId).orElseThrow();
            r.setStatus(ReceiptStatus.ISSUED);
            r.setDdtd(ok.id());
            r.setPosId(ok.posId());
            r.setLottery(ok.lottery());
            r.setQrData(ok.qrData());
            r.setPosApiDate(ok.date());
            r.setPrintedAt(parseDate(ok.date()));
            r.setTotalVat(issued.totalVat());
            r.setTotalCityTax(issued.totalCityTax());
            r.setLastError(null);
            r.setNextAttemptAt(null);
            notifications.enqueue(r.getMerchantId(), EventTypes.RECEIPT_ISSUED, eventData(r));
        });
    }

    private void fail(String receiptId, String error) {
        tx.executeWithoutResult(s -> {
            Receipt r = receipts.findForUpdate(receiptId).orElseThrow();
            r.setLastError(error == null ? null : error.length() > 1000 ? error.substring(0, 1000) : error);
            if (r.getAttempts() >= BACKOFF.length) {
                r.setStatus(ReceiptStatus.FAILED);
                r.setNextAttemptAt(null);
                notifications.enqueue(r.getMerchantId(), EventTypes.RECEIPT_FAILED, eventData(r));
                log.warn("Receipt {} failed permanently: {}", receiptId, error);
            } else {
                r.setStatus(ReceiptStatus.PENDING);
                r.setNextAttemptAt(Instant.now().plus(BACKOFF[r.getAttempts() - 1]));
                log.info("Receipt {} attempt {} failed, retrying: {}", receiptId, r.getAttempts(), error);
            }
        });
    }

    /** Баримт буцаах (PosAPI DELETE). */
    public ReceiptResponse returnReceipt(String merchantId, String id) {
        Receipt r = receipts.findByIdAndMerchantId(id, merchantId).orElseThrow(() -> ApiException.notFound("Receipt"));
        if (r.getStatus() == ReceiptStatus.RETURNED) return ReceiptResponse.of(r);
        if (r.getStatus() != ReceiptStatus.ISSUED) {
            throw ApiException.conflict("invalid_state", "Only ISSUED receipts can be returned (current: " + r.getStatus() + ")");
        }
        Merchant m = merchant(merchantId);
        posApi.deleteReceipt(m.getEbarimt(), new PosApiModels.DeleteReceipt(r.getDdtd(), r.getPosApiDate()));
        tx.executeWithoutResult(s -> {
            Receipt locked = receipts.findForUpdate(id).orElseThrow();
            locked.setStatus(ReceiptStatus.RETURNED);
            locked.setReturnedAt(Instant.now());
            notifications.enqueue(merchantId, EventTypes.RECEIPT_RETURNED, eventData(locked));
        });
        return get(merchantId, id);
    }

    private Receipt newReceipt(String merchantId, String externalRef, ReceiptSpec spec, BigDecimal amount) {
        Receipt r = new Receipt();
        r.setMerchantId(merchantId);
        r.setExternalRef(externalRef);
        r.setType(spec.typeOrDefault());
        r.setCustomerTin(spec.customerTin());
        r.setConsumerNo(spec.consumerNo());
        r.setTotalAmount(amount);
        r.setSpecJson(json.write(spec));
        r.setStatus(ReceiptStatus.PENDING);
        return r;
    }

    static Map<String, Object> eventData(Receipt r) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("receiptId", r.getId());
        d.put("externalRef", r.getExternalRef());
        d.put("invoiceId", r.getInvoiceId());
        d.put("status", r.getStatus().name());
        d.put("ddtd", r.getDdtd());
        d.put("lottery", r.getLottery());
        d.put("qrData", r.getQrData());
        d.put("totalAmount", r.getTotalAmount());
        d.put("totalVat", r.getTotalVat());
        d.put("totalCityTax", r.getTotalCityTax());
        d.put("printedAt", r.getPrintedAt() == null ? null : r.getPrintedAt().toString());
        d.put("error", r.getLastError());
        return d;
    }

    private static Instant parseDate(String date) {
        if (date == null) return Instant.now();
        try {
            return LocalDateTime.parse(date, POSAPI_DATE).atZone(POSAPI_ZONE).toInstant();
        } catch (DateTimeParseException e) {
            return Instant.now();
        }
    }

    private Merchant merchant(String id) {
        return merchants.findById(id).orElseThrow(() -> ApiException.notFound("Merchant"));
    }
}
