package com.wsnsa.payment.merchant;

import com.wsnsa.payment.common.BaseEntity;
import com.wsnsa.payment.common.EncryptedStringConverter;
import com.wsnsa.payment.provider.ProviderType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Merchant-ийн нэг provider-ийн гэрээний мэдээлэл (merchant id, token, нууц үг...).
 * {@code config} нь JSON обьект бөгөөд DB-д шифрлэгдэж хадгалагдана.
 */
@Getter
@Setter
@Entity
@Table(name = "merchant_providers",
        uniqueConstraints = @UniqueConstraint(columnNames = {"merchant_id", "provider"}))
public class MerchantProviderConfig extends BaseEntity {

    @Column(name = "merchant_id", length = 36, nullable = false)
    private String merchantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", length = 30, nullable = false)
    private ProviderType provider;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "config", nullable = false)
    private String config;
}
