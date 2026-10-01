package com.wsnsa.payment.ebarimt;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReceiptResponse(
        String id,
        String externalRef,
        String invoiceId,
        ReceiptType type,
        ReceiptStatus status,
        String ddtd,
        String lottery,
        String qrData,
        BigDecimal totalAmount,
        BigDecimal totalVat,
        BigDecimal totalCityTax,
        String customerTin,
        String consumerNo,
        Instant printedAt,
        Instant returnedAt,
        int attempts,
        String lastError,
        Instant createdAt
) {
    public static ReceiptResponse of(Receipt r) {
        return new ReceiptResponse(r.getId(), r.getExternalRef(), r.getInvoiceId(), r.getType(), r.getStatus(),
                r.getDdtd(), r.getLottery(), r.getQrData(), r.getTotalAmount(), r.getTotalVat(), r.getTotalCityTax(),
                r.getCustomerTin(), r.getConsumerNo(), r.getPrintedAt(), r.getReturnedAt(), r.getAttempts(),
                r.getLastError(), r.getCreatedAt());
    }
}
