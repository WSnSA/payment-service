package com.wsnsa.payment.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wsnsa.payment.payment.Payment;
import com.wsnsa.payment.payment.PaymentStatus;
import com.wsnsa.payment.qpay.dto.InvoiceResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentResponse(
        String id,
        String orderId,
        PaymentStatus status,
        BigDecimal amount,
        String currency,
        String description,
        BigDecimal paidAmount,
        String paymentType,
        Qpay qpay,
        Ebarimt ebarimt,
        String errorMessage,
        Instant createdAt,
        Instant paidAt) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Qpay(String invoiceId, String paymentId, String qrText, String qrImage, String shortUrl,
            List<InvoiceResponse.Deeplink> deeplinks) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Ebarimt(String id, String status, String lottery, String qrData) {
    }

    private static final TypeReference<List<InvoiceResponse.Deeplink>> DEEPLINKS = new TypeReference<>() {
    };

    public static PaymentResponse from(Payment p, ObjectMapper mapper) {
        Qpay qpay = p.getQpayInvoiceId() == null ? null
                : new Qpay(p.getQpayInvoiceId(), p.getQpayPaymentId(), p.getQrText(), p.getQrImage(),
                        p.getShortUrl(), readDeeplinks(p.getDeeplinks(), mapper));
        Ebarimt ebarimt = p.getEbarimtId() == null ? null
                : new Ebarimt(p.getEbarimtId(), p.getEbarimtStatus(), p.getEbarimtLottery(), p.getEbarimtQrData());
        return new PaymentResponse(p.getId(), p.getOrderId(), p.getStatus(), p.getAmount(), p.getCurrency(),
                p.getDescription(), p.getPaidAmount(), p.getPaymentType(), qpay, ebarimt, p.getErrorMessage(),
                p.getCreatedAt(), p.getPaidAt());
    }

    private static List<InvoiceResponse.Deeplink> readDeeplinks(String json, ObjectMapper mapper) {
        if (json == null) {
            return null;
        }
        try {
            return mapper.readValue(json, DEEPLINKS);
        } catch (Exception e) {
            return null;
        }
    }
}
