package com.wsnsa.payment.qpay.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PaymentCheckResponse(Integer count, BigDecimal paidAmount, List<Row> rows) {

    public List<Row> safeRows() {
        return rows == null ? List.of() : rows;
    }

    /** payment_status: NEW, FAILED, PAID, PARTIAL, REFUNDED. payment_type: P2P, CARD. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Row(
            String paymentId,
            String paymentStatus,
            BigDecimal paymentAmount,
            BigDecimal trxFee,
            String paymentCurrency,
            String paymentWallet,
            String paymentType,
            String paymentDate) {

        public boolean isPaid() {
            return "PAID".equalsIgnoreCase(paymentStatus);
        }
    }
}
