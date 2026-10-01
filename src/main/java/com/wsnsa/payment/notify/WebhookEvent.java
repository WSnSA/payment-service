package com.wsnsa.payment.notify;

import com.wsnsa.payment.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Merchant руу илгээх мэдэгдлийн outbox. Бизнес өөрчлөлттэй нэг transaction-д бичигдэж,
 * {@link WebhookDispatcher} дахин оролдлоготойгоор илгээнэ.
 */
@Getter
@Setter
@Entity
@Table(name = "webhook_events")
public class WebhookEvent extends BaseEntity {

    public enum Status {PENDING, DELIVERED, FAILED}

    @Column(name = "merchant_id", length = 36, nullable = false)
    private String merchantId;

    @Column(name = "event_type", length = 50, nullable = false)
    private String eventType;

    @Column(name = "payload", nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private Status status = Status.PENDING;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_status_code")
    private Integer lastStatusCode;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "delivered_at")
    private Instant deliveredAt;
}
