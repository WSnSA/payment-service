package com.wsnsa.payment.payment;

import com.wsnsa.payment.common.BaseEntity;
import com.wsnsa.payment.provider.ProviderType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "invoices", uniqueConstraints = {
        @UniqueConstraint(name = "uk_invoices_merchant_ref", columnNames = {"merchant_id", "external_ref"}),
        @UniqueConstraint(name = "uk_invoices_provider_ref", columnNames = {"provider", "provider_invoice_id"})
})
public class Invoice extends BaseEntity {

    @Column(name = "merchant_id", length = 36, nullable = false)
    private String merchantId;

    /** Merchant-ийн өөрийн захиалга/гүйлгээний дугаар. Давхар нэхэмжлэхээс сэргийлнэ. */
    @Column(name = "external_ref", length = 100, nullable = false)
    private String externalRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", length = 30, nullable = false)
    private ProviderType provider;

    @Column(name = "amount", precision = 18, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "currency", length = 3, nullable = false)
    private String currency = "MNT";

    @Column(name = "description", length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private InvoiceStatus status = InvoiceStatus.PENDING;

    @Column(name = "provider_invoice_id", length = 100)
    private String providerInvoiceId;

    @Column(name = "provider_txn_id", length = 100)
    private String providerTxnId;

    @Column(name = "qr_text")
    private String qrText;

    @Column(name = "qr_image")
    private String qrImage;

    @Column(name = "short_url", length = 500)
    private String shortUrl;

    @Column(name = "deeplink", length = 1000)
    private String deeplink;

    @Column(name = "payment_url", length = 1000)
    private String paymentUrl;

    /** QPay-ийн банкны апп жагсаалт (JSON). */
    @Column(name = "deeplinks")
    private String deeplinksJson;

    /** Төлбөрийн дараа хэрэглэгчийг буцаах merchant-ийн хуудас. */
    @Column(name = "return_url", length = 1000)
    private String returnUrl;

    /** Merchant-ийн нэмэлт өгөгдөл (JSON), webhook-д буцаагдана. */
    @Column(name = "metadata")
    private String metadataJson;

    /** Төлөгдсөний дараа гаргах И-баримтын өгөгдөл (JSON, {@code ReceiptSpec}). */
    @Column(name = "receipt_spec")
    private String receiptSpecJson;

    @Column(name = "paid_amount", precision = 18, scale = 2)
    private BigDecimal paidAmount;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "status_message", length = 500)
    private String statusMessage;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
