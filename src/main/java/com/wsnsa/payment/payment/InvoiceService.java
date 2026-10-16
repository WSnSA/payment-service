package com.wsnsa.payment.payment;

import com.fasterxml.jackson.core.type.TypeReference;
import com.wsnsa.payment.common.ApiException;
import com.wsnsa.payment.common.JsonSupport;
import com.wsnsa.payment.config.AppProperties;
import com.wsnsa.payment.ebarimt.Receipt;
import com.wsnsa.payment.ebarimt.ReceiptRepository;
import com.wsnsa.payment.ebarimt.ReceiptService;
import com.wsnsa.payment.merchant.Merchant;
import com.wsnsa.payment.merchant.MerchantRepository;
import com.wsnsa.payment.notify.EventTypes;
import com.wsnsa.payment.notify.NotificationService;
import com.wsnsa.payment.provider.DeepLink;
import com.wsnsa.payment.provider.PaymentProvider;
import com.wsnsa.payment.provider.PaymentStatus;
import com.wsnsa.payment.provider.ProviderContext;
import com.wsnsa.payment.provider.ProviderException;
import com.wsnsa.payment.provider.ProviderInvoice;
import com.wsnsa.payment.provider.ProviderInvoiceRequest;
import com.wsnsa.payment.provider.ProviderRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Нэхэмжлэх үүсгэх, provider-оос төлөв шалгах, төлбөрийг баталгаажуулах.
 *
 * <p>Provider руу хийх HTTP дуудлага DB transaction-аас гадуур явна; төлөв өөрчлөх нь
 * мөрийг түгжиж (SELECT ... FOR UPDATE) хийгдэх тул webhook, poll, гар шалгалт зэрэг
 * ирсэн ч нэг нэхэмжлэх нэг л удаа PAID болно.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository invoices;
    private final MerchantRepository merchants;
    private final ReceiptRepository receipts;
    private final ProviderRegistry registry;
    private final ProviderContextFactory contexts;
    private final ReceiptService receiptService;
    private final NotificationService notifications;
    private final JsonSupport json;
    private final AppProperties props;
    private final TransactionTemplate tx;

    public InvoiceResponse create(String merchantId, CreateInvoiceRequest req) {
        Merchant merchant = merchants.findById(merchantId).orElseThrow(() -> ApiException.notFound("Merchant"));

        var existing = invoices.findByMerchantIdAndExternalRef(merchantId, req.externalRef());
        if (existing.isPresent()) {
            return sameOrConflict(existing.get(), req);
        }

        PaymentProvider provider = registry.get(req.provider());
        receiptService.validateForInvoice(merchant, req.receipt(), req.amount());

        Invoice invoice = new Invoice();
        invoice.setMerchantId(merchantId);
        invoice.setExternalRef(req.externalRef());
        invoice.setProvider(req.provider());
        invoice.setAmount(req.amount());
        invoice.setDescription(req.description() == null || req.description().isBlank() ? req.externalRef() : req.description());
        invoice.setReturnUrl(req.returnUrl());
        invoice.setMetadataJson(json.write(req.metadata()));
        invoice.setReceiptSpecJson(json.write(req.receipt()));
        invoice.setExpiresAt(Instant.now().plus(props.invoiceTtl()));

        // Provider тохиргоо байгаа эсэхийг хадгалахаас өмнө шалгана.
        contexts.build(merchantId, req.provider(), "check", false);

        String id;
        try {
            id = tx.execute(s -> invoices.saveAndFlush(invoice).getId());
        } catch (DataIntegrityViolationException e) {
            Invoice raced = invoices.findByMerchantIdAndExternalRef(merchantId, req.externalRef()).orElseThrow(() -> e);
            return sameOrConflict(raced, req);
        }

        ProviderContext ctx = contexts.forInvoice(invoice);
        ProviderInvoice created;
        try {
            created = provider.createInvoice(new ProviderInvoiceRequest(ctx, id, req.amount(), invoice.getDescription(), req.options()));
        } catch (ProviderException e) {
            tx.executeWithoutResult(s -> invoices.findForUpdate(id).ifPresent(i -> {
                i.setStatus(InvoiceStatus.FAILED);
                i.setStatusMessage(truncate("Provider invoice creation failed: " + e.getMessage()));
            }));
            throw e;
        }

        Invoice saved = tx.execute(s -> {
            Invoice i = invoices.findForUpdate(id).orElseThrow();
            i.setProviderInvoiceId(created.providerInvoiceId());
            i.setQrText(created.qrText());
            i.setQrImage(created.qrImage());
            i.setShortUrl(created.shortUrl());
            i.setDeeplink(created.deeplink());
            i.setPaymentUrl(created.paymentUrl());
            i.setDeeplinksJson(created.deeplinks() == null || created.deeplinks().isEmpty() ? null : json.write(created.deeplinks()));
            return i;
        });
        return toResponse(saved);
    }

    public InvoiceResponse get(String merchantId, String id) {
        return toResponse(invoices.findByIdAndMerchantId(id, merchantId).orElseThrow(() -> ApiException.notFound("Invoice")));
    }

    public InvoiceResponse getByExternalRef(String merchantId, String externalRef) {
        return toResponse(invoices.findByMerchantIdAndExternalRef(merchantId, externalRef)
                .orElseThrow(() -> ApiException.notFound("Invoice")));
    }

    /** Merchant гараар шалгуулах. */
    public InvoiceResponse check(String merchantId, String id) {
        invoices.findByIdAndMerchantId(id, merchantId).orElseThrow(() -> ApiException.notFound("Invoice"));
        return toResponse(refresh(id));
    }

    public InvoiceResponse cancel(String merchantId, String id) {
        Invoice invoice = invoices.findByIdAndMerchantId(id, merchantId).orElseThrow(() -> ApiException.notFound("Invoice"));
        // Цуцлахаас өмнө төлөгдсөн эсэхийг шалгана.
        invoice = refresh(id);
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw ApiException.conflict("already_paid", "Invoice is already paid");
        }
        if (invoice.getStatus() == InvoiceStatus.PENDING && invoice.getProviderInvoiceId() != null) {
            try {
                registry.get(invoice.getProvider()).cancel(contexts.forInvoice(invoice), invoice.getProviderInvoiceId());
            } catch (ProviderException e) {
                log.warn("Provider cancel failed for invoice {}: {}", id, e.getMessage());
            }
        }
        Invoice result = tx.execute(s -> {
            Invoice i = invoices.findForUpdate(id).orElseThrow();
            if (i.getStatus() == InvoiceStatus.PENDING) {
                i.setStatus(InvoiceStatus.CANCELLED);
            }
            return i;
        });
        return toResponse(result);
    }

    /**
     * Provider-оос төлөв шалгаж, өөрчлөгдсөн бол хадгална. Webhook, poll, гар шалгалт бүгд үүгээр орно.
     */
    public Invoice refresh(String invoiceId) {
        Invoice invoice = invoices.findById(invoiceId).orElseThrow(() -> ApiException.notFound("Invoice"));
        if (invoice.getStatus().isFinal() || invoice.getProviderInvoiceId() == null) {
            return invoice;
        }
        PaymentProvider provider = registry.get(invoice.getProvider());
        PaymentStatus status = provider.checkStatus(contexts.forInvoice(invoice), invoice.getId(), invoice.getProviderInvoiceId());
        return apply(invoiceId, status);
    }

    Invoice apply(String invoiceId, PaymentStatus status) {
        return tx.execute(s -> {
            Invoice i = invoices.findForUpdate(invoiceId).orElseThrow();
            if (i.getStatus().isFinal()) return i;

            switch (status.state()) {
                case PAID -> {
                    BigDecimal paid = status.paidAmount() != null ? status.paidAmount() : i.getAmount();
                    if (paid.compareTo(i.getAmount()) < 0) {
                        log.warn("Invoice {} underpaid: expected {}, provider reports {}", i.getId(), i.getAmount(), paid);
                        i.setStatusMessage(truncate("Underpaid: provider reports " + paid.toPlainString()));
                        return i;
                    }
                    if (i.getStatus() != InvoiceStatus.PENDING) {
                        log.warn("Invoice {} was {} but provider confirms payment; marking PAID", i.getId(), i.getStatus());
                    }
                    i.setStatus(InvoiceStatus.PAID);
                    i.setPaidAt(Instant.now());
                    i.setPaidAmount(paid);
                    i.setProviderTxnId(status.providerTxnId());
                    i.setStatusMessage(null);

                    Merchant m = merchants.findById(i.getMerchantId()).orElseThrow();
                    Receipt receipt = receiptService.createForInvoice(m, i);
                    Map<String, Object> data = eventData(i);
                    data.put("receiptId", receipt == null ? null : receipt.getId());
                    notifications.enqueue(i.getMerchantId(), EventTypes.INVOICE_PAID, data);
                    log.info("Invoice {} PAID via {} (txn {})", i.getId(), i.getProvider(), status.providerTxnId());
                }
                case FAILED -> {
                    if (i.getStatus() == InvoiceStatus.PENDING) {
                        i.setStatus(InvoiceStatus.FAILED);
                        i.setStatusMessage(truncate(status.message()));
                        notifications.enqueue(i.getMerchantId(), EventTypes.INVOICE_FAILED, eventData(i));
                    }
                }
                case PENDING -> {
                    // өөрчлөлтгүй
                }
            }
            return i;
        });
    }

    /** Хугацаа нь дууссан PENDING нэхэмжлэхийг (сүүлчийн удаа шалгаад) EXPIRED болгоно. */
    public void expire(String invoiceId) {
        Invoice i;
        try {
            i = refresh(invoiceId);
        } catch (ProviderException e) {
            log.warn("Could not check invoice {} before expiring: {}", invoiceId, e.getMessage());
            return;
        }
        if (i.getStatus() != InvoiceStatus.PENDING) return;
        tx.executeWithoutResult(s -> {
            Invoice locked = invoices.findForUpdate(invoiceId).orElseThrow();
            if (locked.getStatus() == InvoiceStatus.PENDING) {
                locked.setStatus(InvoiceStatus.EXPIRED);
                notifications.enqueue(locked.getMerchantId(), EventTypes.INVOICE_EXPIRED, eventData(locked));
            }
        });
    }

    private InvoiceResponse sameOrConflict(Invoice existing, CreateInvoiceRequest req) {
        if (existing.getProvider() != req.provider() || existing.getAmount().compareTo(req.amount()) != 0) {
            throw ApiException.conflict("duplicate_external_ref",
                    "Invoice with this externalRef already exists with a different provider or amount");
        }
        return toResponse(existing);
    }

    Map<String, Object> eventData(Invoice i) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("invoiceId", i.getId());
        d.put("externalRef", i.getExternalRef());
        d.put("provider", i.getProvider().name());
        d.put("status", i.getStatus().name());
        d.put("amount", i.getAmount());
        d.put("paidAmount", i.getPaidAmount());
        d.put("providerTxnId", i.getProviderTxnId());
        d.put("paidAt", i.getPaidAt() == null ? null : i.getPaidAt().toString());
        d.put("message", i.getStatusMessage());
        d.put("metadata", readMetadata(i));
        return d;
    }

    public InvoiceResponse toResponse(Invoice i) {
        List<DeepLink> links = json.read(i.getDeeplinksJson(), new TypeReference<List<DeepLink>>() {
        });
        String receiptId = i.getStatus() == InvoiceStatus.PAID
                ? receipts.findByInvoiceId(i.getId()).map(Receipt::getId).orElse(null) : null;
        return new InvoiceResponse(i.getId(), i.getExternalRef(), i.getProvider(), i.getStatus(), i.getAmount(),
                i.getCurrency(), i.getDescription(), i.getProviderInvoiceId(), i.getProviderTxnId(), i.getQrText(),
                i.getQrImage(), i.getShortUrl(), i.getDeeplink(), i.getPaymentUrl(), links, i.getPaidAmount(),
                i.getPaidAt(), i.getExpiresAt(), i.getCreatedAt(), i.getStatusMessage(), readMetadata(i), receiptId);
    }

    private Map<String, Object> readMetadata(Invoice i) {
        return json.read(i.getMetadataJson(), new TypeReference<Map<String, Object>>() {
        });
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() > 500 ? s.substring(0, 500) : s;
    }
}
