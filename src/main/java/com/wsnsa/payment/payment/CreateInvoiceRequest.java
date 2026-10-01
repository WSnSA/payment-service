package com.wsnsa.payment.payment;

import com.wsnsa.payment.ebarimt.ReceiptSpec;
import com.wsnsa.payment.provider.ProviderType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Map;

/**
 * @param externalRef Merchant-ийн давтагдашгүй дугаар. Ижил дугаараар дахин дуудвал өмнөх нэхэмжлэхийг буцаана.
 * @param options     Provider-ийн нэмэлт параметр (жишээ нь DigiPay: {@code userToken})
 * @param metadata    Webhook-д буцааж өгөх дурын өгөгдөл
 * @param receipt     Төлөгдсөний дараа гаргах И-баримт (merchant PER_PAYMENT горимтой үед)
 */
public record CreateInvoiceRequest(
        @NotNull ProviderType provider,
        @NotBlank @Size(max = 100) String externalRef,
        @NotNull @DecimalMin(value = "1") @Digits(integer = 16, fraction = 0, message = "MNT amount must be a whole number") BigDecimal amount,
        @Size(max = 255) String description,
        @Size(max = 1000) @Pattern(regexp = "^https?://.*", message = "must be an http(s) URL") String returnUrl,
        Map<String, String> options,
        Map<String, Object> metadata,
        @Valid ReceiptSpec receipt
) {
}
