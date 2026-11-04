package com.wsnsa.payment.qpay.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * POST /v2/ebarimt/create.
 * ebarimt_receiver_type: CITIZEN (иргэн) / COMPANY (ААН).
 * ebarimt_receiver: иргэн бол И-баримт апп-д бүртгэлтэй утас, ААН бол регистр.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record EbarimtCreateRequest(String paymentId, String ebarimtReceiverType, String ebarimtReceiver,
        String callbackUrl) {
}
