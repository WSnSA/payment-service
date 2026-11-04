package com.wsnsa.payment.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payment")
public class Payment {

    /** Манай талын дахин давтагдашгүй дугаар. QPay-д sender_invoice_no болж очно. */
    @Id
    private String id;

    private String orderId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String currency;

    @Column(nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    private String qpayInvoiceId;
    private String qpayPaymentId;
    private BigDecimal paidAmount;
    private String paymentType;

    @Column(columnDefinition = "TEXT")
    private String qrText;

    @Column(columnDefinition = "TEXT")
    private String qrImage;

    private String shortUrl;

    /** QPay-ийн банкны апп-уудын deeplink жагсаалт (JSON). */
    @Column(columnDefinition = "TEXT")
    private String deeplinks;

    private String ebarimtId;
    private String ebarimtStatus;
    private String ebarimtLottery;

    @Column(columnDefinition = "TEXT")
    private String ebarimtQrData;

    private String errorMessage;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant paidAt;

    @Version
    private Long version;

    protected Payment() {
    }

    public Payment(String id, String orderId, BigDecimal amount, String currency, String description, Instant now) {
        this.id = id;
        this.orderId = orderId;
        this.amount = amount;
        this.currency = currency;
        this.description = description;
        this.status = PaymentStatus.NEW;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void invoiceCreated(String invoiceId, String qrText, String qrImage, String shortUrl, String deeplinks,
            Instant now) {
        this.qpayInvoiceId = invoiceId;
        this.qrText = qrText;
        this.qrImage = qrImage;
        this.shortUrl = shortUrl;
        this.deeplinks = deeplinks;
        this.status = PaymentStatus.PENDING;
        this.updatedAt = now;
    }

    public void invoiceFailed(String error, Instant now) {
        this.status = PaymentStatus.FAILED;
        this.errorMessage = truncate(error, 500);
        this.updatedAt = now;
    }

    public void paidAmountUpdated(BigDecimal paidAmount, Instant now) {
        this.paidAmount = paidAmount;
        this.updatedAt = now;
    }

    public void markPaid(String qpayPaymentId, BigDecimal paidAmount, String paymentType, Instant now) {
        this.status = PaymentStatus.PAID;
        this.qpayPaymentId = qpayPaymentId;
        this.paidAmount = paidAmount;
        this.paymentType = paymentType;
        this.paidAt = now;
        this.updatedAt = now;
    }

    public void markCancelled(Instant now) {
        this.status = PaymentStatus.CANCELLED;
        this.updatedAt = now;
    }

    public void markRefunded(Instant now) {
        this.status = PaymentStatus.REFUNDED;
        this.updatedAt = now;
    }

    public void ebarimtCreated(String ebarimtId, String ebarimtStatus, String lottery, String qrData, Instant now) {
        this.ebarimtId = ebarimtId;
        this.ebarimtStatus = ebarimtStatus;
        this.ebarimtLottery = lottery;
        this.ebarimtQrData = qrData;
        this.updatedAt = now;
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    public String getId() { return id; }
    public String getOrderId() { return orderId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getDescription() { return description; }
    public PaymentStatus getStatus() { return status; }
    public String getQpayInvoiceId() { return qpayInvoiceId; }
    public String getQpayPaymentId() { return qpayPaymentId; }
    public BigDecimal getPaidAmount() { return paidAmount; }
    public String getPaymentType() { return paymentType; }
    public String getQrText() { return qrText; }
    public String getQrImage() { return qrImage; }
    public String getShortUrl() { return shortUrl; }
    public String getDeeplinks() { return deeplinks; }
    public String getEbarimtId() { return ebarimtId; }
    public String getEbarimtStatus() { return ebarimtStatus; }
    public String getEbarimtLottery() { return ebarimtLottery; }
    public String getEbarimtQrData() { return ebarimtQrData; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getPaidAt() { return paidAt; }
    public Long getVersion() { return version; }
}
