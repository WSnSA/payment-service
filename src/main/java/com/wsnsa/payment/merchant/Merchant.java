package com.wsnsa.payment.merchant;

import com.wsnsa.payment.common.BaseEntity;
import com.wsnsa.payment.common.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Энэ сервисийг ашиглах клиент систем (жишээ нь USUG kiosk, өөр вэб дэлгүүр).
 */
@Getter
@Setter
@Entity
@Table(name = "merchants")
public class Merchant extends BaseEntity {

    @Column(name = "code", length = 50, nullable = false, unique = true)
    private String code;

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    /** API түлхүүрийн SHA-256. Түлхүүр өөрөө хадгалагдахгүй. */
    @Column(name = "api_key_hash", length = 64, nullable = false, unique = true)
    private String apiKeyHash;

    /** Үйл явдлын мэдэгдэл (invoice.paid г.м) хүлээн авах URL. */
    @Column(name = "webhook_url", length = 500)
    private String webhookUrl;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "webhook_secret", length = 500, nullable = false)
    private String webhookSecret;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Embedded
    private EbarimtSettings ebarimt = new EbarimtSettings();
}
