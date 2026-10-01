package com.wsnsa.payment.ebarimt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * PosAPI 3.0 ({@code /rest/receipt}) хүсэлт, хариуны бүтэц.
 */
public final class PosApiModels {

    private PosApiModels() {
    }

    /**
     * @param districtCode Баримт хэвлэсэн орон нутгийн код
     * @param branchNo     Салбарын дугаар (0-999)
     * @param customerTin  Худалдан авагч байгууллагын ТТД (B2B)
     * @param consumerNo   Худалдан авагч иргэний ebarimt бүртгэлийн дугаар (B2C)
     * @param inactiveId   Засварлах/буцаах баримтын ДДТД
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CreateReceipt(
            BigDecimal totalAmount,
            @JsonProperty("totalVAT") BigDecimal totalVat,
            BigDecimal totalCityTax,
            String districtCode,
            String merchantTin,
            String branchNo,
            String posNo,
            String customerTin,
            String consumerNo,
            String type,
            String inactiveId,
            String invoiceId,
            String reportMonth,
            List<SubReceipt> receipts,
            List<Payment> payments
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SubReceipt(
            BigDecimal totalAmount,
            @JsonProperty("totalVAT") BigDecimal totalVat,
            BigDecimal totalCityTax,
            String taxType,
            String merchantTin,
            String bankAccountNo,
            Map<String, Object> data,
            List<Item> items
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Item(
            String name,
            String barCode,
            String barCodeType,
            String classificationCode,
            String taxProductCode,
            String measureUnit,
            BigDecimal qty,
            BigDecimal unitPrice,
            @JsonProperty("totalVAT") BigDecimal totalVat,
            BigDecimal totalCityTax,
            BigDecimal totalAmount,
            Map<String, Object> data
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Payment(String code, String status, BigDecimal paidAmount, Map<String, Object> data) {
    }

    /**
     * @param id     ДДТД (33 оронтой)
     * @param status SUCCESS | ERROR | PAYMENT
     * @param date   "yyyy-MM-dd HH:mm:ss"
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CreateReceiptResponse(
            String id,
            String posId,
            String status,
            String message,
            String qrData,
            String lottery,
            String date,
            Boolean easy
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DeleteReceipt(String id, String date) {
    }
}
