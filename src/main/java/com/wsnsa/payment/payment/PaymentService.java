package com.wsnsa.payment.payment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wsnsa.payment.qpay.QPayApiException;
import com.wsnsa.payment.qpay.QPayClient;
import com.wsnsa.payment.qpay.QPayProperties;
import com.wsnsa.payment.qpay.dto.EbarimtCreateRequest;
import com.wsnsa.payment.qpay.dto.EbarimtResponse;
import com.wsnsa.payment.qpay.dto.InvoiceRequest;
import com.wsnsa.payment.qpay.dto.InvoiceResponse;
import com.wsnsa.payment.qpay.dto.PaymentCancelRequest;
import com.wsnsa.payment.qpay.dto.PaymentCheckRequest;
import com.wsnsa.payment.qpay.dto.PaymentCheckResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Төлбөрийн бизнес логик. QPay-руу хийх HTTP дуудлагууд DB transaction-ий гадна явагдана.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final String DEFAULT_RECEIVER_CODE = "terminal";
    private static final String CURRENCY = "MNT";

    private final PaymentRepository repository;
    private final QPayClient qpay;
    private final QPayProperties properties;
    private final TransactionTemplate tx;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PaymentService(PaymentRepository repository, QPayClient qpay, QPayProperties properties,
            TransactionTemplate tx, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.qpay = qpay;
        this.properties = properties;
        this.tx = tx;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** Төлбөр үүсгэж, QPay-д нэхэмжлэх (QR) үүсгэнэ. */
    public Payment create(CreatePaymentCommand command) {
        // sender_invoice_no: давтагдашгүй, тусгай тэмдэгтгүй, 45-аас ихгүй тэмдэгт
        String id = UUID.randomUUID().toString().replace("-", "");
        Payment payment = repository.save(new Payment(id, command.orderId(), command.amount(), CURRENCY,
                command.description(), clock.instant()));

        InvoiceRequest request = buildInvoiceRequest(id, command);
        InvoiceResponse response;
        try {
            response = qpay.createInvoice(request);
        } catch (QPayApiException e) {
            log.warn("QPay invoice creation failed for payment {}: {}", id, e.toString());
            payment.invoiceFailed(e.getError() + ": " + e.getMessage(), clock.instant());
            repository.save(payment);
            throw e;
        }
        if (response == null || response.invoiceId() == null) {
            payment.invoiceFailed("QPay returned no invoice_id", clock.instant());
            repository.save(payment);
            throw new QPayApiException(502, "INVALID_INVOICE_RESPONSE", "QPay returned no invoice_id");
        }

        payment.invoiceCreated(response.invoiceId(), response.qrText(), response.qrImage(), response.shortUrl(),
                toJson(response.deeplinks()), clock.instant());
        Payment saved = repository.save(payment);
        log.info("QPay invoice {} created for payment {} ({} {})", response.invoiceId(), id, command.amount(),
                CURRENCY);
        return saved;
    }

    public Payment get(String id) {
        return repository.findById(id).orElseThrow(() -> new PaymentNotFoundException(id));
    }

    public List<Payment> findByOrderId(String orderId) {
        return repository.findByOrderIdOrderByCreatedAtDesc(orderId);
    }

    /**
     * QPay callback ирэх эсвэл гараар шалгах үед дуудагдана. Callback-ийн агуулгад итгэхгүй,
     * заавал QPay payment/check API-гаар баталгаажуулна.
     */
    public Payment syncWithQPay(String id) {
        Payment payment = get(id);
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return payment;
        }

        PaymentCheckResponse check = qpay.checkPayment(PaymentCheckRequest.forInvoice(payment.getQpayInvoiceId()));
        List<PaymentCheckResponse.Row> paidRows = check == null ? List.of()
                : check.safeRows().stream().filter(PaymentCheckResponse.Row::isPaid).toList();
        BigDecimal paid = paidRows.stream()
                .map(PaymentCheckResponse.Row::paymentAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (paid.compareTo(payment.getAmount()) >= 0) {
            PaymentCheckResponse.Row row = paidRows.getFirst();
            Payment updated = update(id, p -> {
                if (p.getStatus() == PaymentStatus.PENDING) {
                    p.markPaid(row.paymentId(), paid, row.paymentType(), clock.instant());
                }
            });
            log.info("Payment {} is PAID (qpay payment_id={}, amount={})", id, row.paymentId(), paid);
            return maybeCreateEbarimt(updated);
        }
        if (paid.signum() > 0
                && (payment.getPaidAmount() == null || paid.compareTo(payment.getPaidAmount()) != 0)) {
            log.info("Payment {} partially paid: {} / {}", id, paid, payment.getAmount());
            return update(id, p -> p.paidAmountUpdated(paid, clock.instant()));
        }
        return payment;
    }

    /** Төлөгдөөгүй нэхэмжлэхийг цуцлана. */
    public Payment cancel(String id) {
        Payment payment = get(id);
        if (payment.getStatus() == PaymentStatus.CANCELLED) {
            return payment;
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new InvalidPaymentStateException(
                    "Only PENDING payments can be cancelled, current status: " + payment.getStatus());
        }
        try {
            qpay.cancelInvoice(payment.getQpayInvoiceId());
        } catch (QPayApiException e) {
            if ("INVOICE_PAID".equals(e.getError())) {
                syncWithQPay(id);
                throw new InvalidPaymentStateException("Invoice is already paid, use refund instead");
            }
            if (!"INVOICE_ALREADY_CANCELED".equals(e.getError())) {
                throw e;
            }
        }
        log.info("Payment {} cancelled (invoice {})", id, payment.getQpayInvoiceId());
        return update(id, p -> p.markCancelled(clock.instant()));
    }

    /** Төлөгдсөн төлбөрийг буцаана (QPay зөвхөн картын гүйлгээнд зөвшөөрнө). */
    public Payment refund(String id, String note) {
        Payment payment = get(id);
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            return payment;
        }
        if (payment.getStatus() != PaymentStatus.PAID) {
            throw new InvalidPaymentStateException(
                    "Only PAID payments can be refunded, current status: " + payment.getStatus());
        }
        qpay.refundPayment(payment.getQpayPaymentId(), new PaymentCancelRequest(null, note));
        log.info("Payment {} refunded (qpay payment_id={})", id, payment.getQpayPaymentId());
        return update(id, p -> p.markRefunded(clock.instant()));
    }

    /** Төлөгдсөн төлбөрт и-баримт үүсгэнэ. Аль хэдийн үүссэн бол байгааг нь буцаана. */
    public Payment createEbarimt(String id, EbarimtReceiverType receiverType, String receiver) {
        Payment payment = get(id);
        if (payment.getEbarimtId() != null) {
            return payment;
        }
        if (payment.getStatus() != PaymentStatus.PAID) {
            throw new InvalidPaymentStateException(
                    "E-barimt can only be created for PAID payments, current status: " + payment.getStatus());
        }
        EbarimtResponse ebarimt = qpay.createEbarimt(new EbarimtCreateRequest(payment.getQpayPaymentId(),
                receiverType.name(), blankToNull(receiver), null));
        if (ebarimt == null || ebarimt.id() == null) {
            throw new QPayApiException(502, "INVALID_EBARIMT_RESPONSE", "QPay returned no e-barimt id");
        }
        log.info("E-barimt {} created for payment {}", ebarimt.id(), id);
        return update(id, p -> p.ebarimtCreated(ebarimt.id(), ebarimt.ebarimtStatus(), ebarimt.ebarimtLottery(),
                ebarimt.ebarimtQrData(), clock.instant()));
    }

    private Payment maybeCreateEbarimt(Payment payment) {
        if (!properties.ebarimt().autoCreate() || payment.getEbarimtId() != null) {
            return payment;
        }
        try {
            return createEbarimt(payment.getId(),
                    EbarimtReceiverType.valueOf(properties.ebarimt().receiverType()), null);
        } catch (RuntimeException e) {
            // Төлбөр амжилттай болсон тул и-баримтын алдаа төлбөрийн урсгалыг зогсоохгүй.
            log.error("Automatic e-barimt creation failed for payment {}", payment.getId(), e);
            return payment;
        }
    }

    private InvoiceRequest buildInvoiceRequest(String id, CreatePaymentCommand command) {
        String receiverCode = command.receiverCode() != null && !command.receiverCode().isBlank()
                ? command.receiverCode() : DEFAULT_RECEIVER_CODE;
        String branchCode = blankToNull(properties.branchCode());
        String callbackUrl = callbackUrl(id);

        boolean simple = (command.lines() == null || command.lines().isEmpty()) && command.receiver() == null;
        if (simple) {
            return InvoiceRequest.simple(properties.invoiceCode(), id, receiverCode, command.description(),
                    branchCode, command.amount(), callbackUrl);
        }

        CreatePaymentCommand.Receiver r = command.receiver();
        InvoiceRequest.ReceiverData receiverData = r == null
                ? new InvoiceRequest.ReceiverData(null, null, null, null)
                : new InvoiceRequest.ReceiverData(r.register(), r.name(), r.email(), r.phone());
        List<InvoiceRequest.Line> lines = command.lines() == null ? null : command.lines().stream()
                .map(l -> new InvoiceRequest.Line(l.taxProductCode(), l.description(), l.quantity(),
                        l.unitPrice(), l.note(), null, null, null))
                .toList();
        return new InvoiceRequest(properties.invoiceCode(), id, branchCode, null, receiverCode, receiverData,
                command.description(), null, null, null, false, null, false, null, command.amount(), callbackUrl,
                null, lines);
    }

    String callbackUrl(String id) {
        return UriComponentsBuilder.fromUriString(properties.callbackBaseUrl())
                .path("/api/v1/qpay/callback")
                .queryParam("payment_id", id)
                .build()
                .toUriString();
    }

    /** Entity-г transaction дотор дахин уншиж өөрчилнө; зэрэг өөрчлөлт гарвал нэг удаа дахин оролдоно. */
    private Payment update(String id, Consumer<Payment> change) {
        for (int attempt = 0; ; attempt++) {
            try {
                return tx.execute(status -> {
                    Payment p = get(id);
                    change.accept(p);
                    return repository.saveAndFlush(p);
                });
            } catch (ObjectOptimisticLockingFailureException e) {
                if (attempt >= 1) {
                    throw e;
                }
                log.debug("Concurrent update of payment {}, retrying", id);
            }
        }
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
