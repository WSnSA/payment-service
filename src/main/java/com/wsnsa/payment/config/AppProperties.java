package com.wsnsa.payment.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Сервисийн үндсэн тохиргоо ({@code payment.*}).
 *
 * @param publicBaseUrl  Гаднаас хандах үндсэн URL. Provider-уудын callback URL үүнээс үүснэ.
 * @param adminApiKey    {@code /admin/**} endpoint-уудын түлхүүр ({@code X-Admin-Key}).
 * @param encryptionKey  Provider-ийн нууц тохиргоо, webhook secret-ийг шифрлэх AES-256 түлхүүр (base64, 32 байт).
 * @param invoiceTtl     Нэхэмжлэх хүчинтэй байх хугацаа. Хэтэрвэл EXPIRED болно.
 * @param sandboxEnabled SANDBOX provider-ийг идэвхжүүлэх (зөвхөн тест/интеграцид).
 * @param schedulingEnabled Webhook илгээх, баримт гаргах, poll хийх job-уудыг энэ instance дээр ажиллуулах эсэх.
 * @param http           Гадагш хийх HTTP дуудлагын timeout.
 */
@Validated
@ConfigurationProperties(prefix = "payment")
public record AppProperties(
        @NotBlank String publicBaseUrl,
        @NotBlank String adminApiKey,
        @NotBlank String encryptionKey,
        Duration invoiceTtl,
        boolean sandboxEnabled,
        boolean schedulingEnabled,
        Http http
) {
    public AppProperties {
        if (invoiceTtl == null) invoiceTtl = Duration.ofHours(24);
        if (http == null) http = new Http(null, null);
        publicBaseUrl = publicBaseUrl == null ? null : publicBaseUrl.replaceAll("/+$", "");
    }

    public record Http(Duration connectTimeout, Duration readTimeout) {
        public Http {
            if (connectTimeout == null) connectTimeout = Duration.ofSeconds(10);
            if (readTimeout == null) readTimeout = Duration.ofSeconds(30);
        }
    }
}
