package com.wsnsa.payment.qpay.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** POST /v2/payment/check. object_type: INVOICE, QR, ITEM. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PaymentCheckRequest(String objectType, String objectId, Offset offset) {

    public static PaymentCheckRequest forInvoice(String invoiceId) {
        return new PaymentCheckRequest("INVOICE", invoiceId, Offset.firstPage());
    }
}
