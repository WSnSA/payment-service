package com.wsnsa.payment.qpay.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.math.BigDecimal;

/** /v2/ebarimt/create болон GET /v2/ebarimt/{id} хариу. */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record EbarimtResponse(
        String id,
        @JsonProperty("ebarimt_receiver_type") @JsonAlias("ebarim_receiver_type") String ebarimtReceiverType,
        String ebarimtReceiver,
        @JsonProperty("g_payment_id") String paymentId,
        BigDecimal amount,
        BigDecimal vatAmount,
        BigDecimal cityTaxAmount,
        String ebarimtQrData,
        String ebarimtLottery,
        String ebarimtStatus,
        String ebarimtStatusDate) {
}
