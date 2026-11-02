package com.wsnsa.payment.qpay.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.math.BigDecimal;
import java.util.List;

/**
 * POST /v2/invoice хүсэлт. Энгийн (simple) нэхэмжлэхийн хувьд зөвхөн invoice_code, sender_invoice_no,
 * invoice_receiver_code, invoice_description, amount, callback_url талбаруудыг бөглөнө.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record InvoiceRequest(
        String invoiceCode,
        String senderInvoiceNo,
        String senderBranchCode,
        String senderStaffCode,
        String invoiceReceiverCode,
        ReceiverData invoiceReceiverData,
        String invoiceDescription,
        Boolean enableExpiry,
        Boolean calculateVat,
        String taxCustomerCode,
        Boolean allowPartial,
        BigDecimal minimumAmount,
        Boolean allowExceed,
        BigDecimal maximumAmount,
        BigDecimal amount,
        String callbackUrl,
        String note,
        List<Line> lines) {

    public static InvoiceRequest simple(String invoiceCode, String senderInvoiceNo, String receiverCode,
            String description, String branchCode, BigDecimal amount, String callbackUrl) {
        return new InvoiceRequest(invoiceCode, senderInvoiceNo, branchCode, null, receiverCode, null,
                description, null, null, null, null, null, null, null, amount, callbackUrl, null, null);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ReceiverData(String register, String name, String email, String phone) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Line(
            String taxProductCode,
            String lineDescription,
            BigDecimal lineQuantity,
            BigDecimal lineUnitPrice,
            String note,
            List<Discount> discounts,
            List<Surcharge> surcharges,
            List<Tax> taxes) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Discount(String discountCode, String description, BigDecimal amount, String note) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Surcharge(String surchargeCode, String description, BigDecimal amount, String note) {
    }

    /** tax_code: VAT (НӨАТ) эсвэл CITY_TAX (Нийслэлийн татвар). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Tax(String taxCode, String description, BigDecimal amount, String note) {
    }
}
