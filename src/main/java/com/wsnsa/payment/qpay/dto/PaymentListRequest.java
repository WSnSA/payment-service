package com.wsnsa.payment.qpay.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * POST /v2/payment/list. object_type: MERCHANT, INVOICE (object_id = invoice_code), QR.
 * start_date / end_date формат: "yyyy-MM-dd HH:mm:ss".
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PaymentListRequest(String objectType, String objectId, String startDate, String endDate,
        Offset offset) {
}
