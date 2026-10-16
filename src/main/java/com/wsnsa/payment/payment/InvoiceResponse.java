package com.wsnsa.payment.payment;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.wsnsa.payment.provider.DeepLink;
import com.wsnsa.payment.provider.ProviderType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record InvoiceResponse(
        String id,
        String externalRef,
        ProviderType provider,
        InvoiceStatus status,
        BigDecimal amount,
        String currency,
        String description,
        String providerInvoiceId,
        String providerTxnId,
        String qrText,
        String qrImage,
        String shortUrl,
        String deeplink,
        String paymentUrl,
        List<DeepLink> deeplinks,
        BigDecimal paidAmount,
        Instant paidAt,
        Instant expiresAt,
        Instant createdAt,
        String statusMessage,
        Map<String, Object> metadata,
        String receiptId
) {
}
