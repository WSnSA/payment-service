package com.wsnsa.payment.ebarimt;

import com.wsnsa.payment.common.BaseEntity;
import com.wsnsa.payment.common.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
@Table(name = "receipts", uniqueConstraints = {
        @UniqueConstraint(name = "uk_receipts_merchant_ref", columnNames = {"merchant_id", "external_ref"}),
        @UniqueConstraint(name = "uk_receipts_invoice", columnNames = {"invoice_id"})
})
public class Receipt extends BaseEntity {

    @Column(name = "merchant_id", length = 36, nullable = false)
    private String merchantId;

    /** Нэхэмжлэхээс автоматаар үүссэн бол. */
    @Column(name = "invoice_id", length = 36)
    private String invoiceId;

    @Column(name = "external_ref", length = 100, nullable = false)
    private String externalRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 20, nullable = false)
    private ReceiptType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private ReceiptStatus status = ReceiptStatus.PENDING;

    @Column(name = "customer_tin", length = 20)
    private String customerTin;

    @Column(name = "consumer_no", length = 20)
    private String consumerNo;

    @Column(name = "total_amount", precision = 18, scale = 2, nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "total_vat", precision = 18, scale = 2)
    private BigDecimal totalVat;

    @Column(name = "total_city_tax", precision = 18, scale = 2)
    private BigDecimal totalCityTax;

    /** {@link ReceiptSpec} (JSON). */
    @Column(name = "spec", nullable = false)
    private String specJson;

    /** ДДТД (PosAPI-ийн id, 33 оронтой). */
    @Column(name = "ddtd", length = 50)
    private String ddtd;

    @Column(name = "pos_id", length = 50)
    private String posId;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "lottery", length = 500)
    private String lottery;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "qr_data")
    private String qrData;

    @Column(name = "printed_at")
    private Instant printedAt;

    /** PosAPI-д илгээсэн огноо (буцаалтад яг энэ утгыг дамжуулна). */
    @Column(name = "posapi_date", length = 30)
    private String posApiDate;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "returned_at")
    private Instant returnedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
