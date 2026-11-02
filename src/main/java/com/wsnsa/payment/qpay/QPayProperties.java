package com.wsnsa.payment.qpay;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * QPay merchant API v2 тохиргоо.
 *
 * @param baseUrl          Sandbox: https://merchant-sandbox.qpay.mn, Production: https://merchant.qpay.mn
 * @param clientId         QPay-ээс олгосон username (client_id)
 * @param clientSecret     QPay-ээс олгосон password (client_secret)
 * @param invoiceCode      QPay-ээс олгосон нэхэмжлэхийн код (invoice_code)
 * @param callbackBaseUrl  QPay-ээс callback дуудах манай сервисийн гаднаас хандах хаяг
 * @param branchCode       Салбарын код (sender_branch_code), заавал биш
 * @param connectTimeout   Холболтын timeout
 * @param readTimeout      Хариу хүлээх timeout
 * @param tokenExpirySkew  Токены хугацаа дуусахаас хэр өмнө шинэчлэх вэ
 * @param ebarimt          И-баримтын тохиргоо
 */
@Validated
@ConfigurationProperties(prefix = "qpay")
public record QPayProperties(
        @NotBlank @DefaultValue("https://merchant-sandbox.qpay.mn") String baseUrl,
        @NotBlank String clientId,
        @NotBlank String clientSecret,
        @NotBlank String invoiceCode,
        @NotBlank String callbackBaseUrl,
        String branchCode,
        @DefaultValue("10s") Duration connectTimeout,
        @DefaultValue("30s") Duration readTimeout,
        @DefaultValue("60s") Duration tokenExpirySkew,
        @DefaultValue Ebarimt ebarimt) {

    /**
     * @param autoCreate    Төлбөр төлөгдмөгц и-баримтыг автоматаар үүсгэх эсэх
     * @param receiverType  Автоматаар үүсгэх үеийн хүлээн авагчийн төрөл (CITIZEN / COMPANY)
     */
    public record Ebarimt(
            @DefaultValue("false") boolean autoCreate,
            @DefaultValue("CITIZEN") String receiverType) {
    }
}
