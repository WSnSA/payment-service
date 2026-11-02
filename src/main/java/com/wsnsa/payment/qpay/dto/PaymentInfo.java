package com.wsnsa.payment.qpay.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.math.BigDecimal;

/** GET /v2/payment/{payment_id} хариу. */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PaymentInfo(
        String paymentId,
        String paymentStatus,
        BigDecimal paymentAmount,
        BigDecimal paymentFee,
        String paymentCurrency,
        String paymentDate,
        String paymentWallet,
        String transactionType,
        String objectType,
        String objectId) {
}
