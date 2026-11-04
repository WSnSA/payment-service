package com.wsnsa.payment.qpay.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** DELETE /v2/payment/cancel/{id} болон /v2/payment/refund/{id} хүсэлтийн бие. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PaymentCancelRequest(String callbackUrl, String note) {
}
