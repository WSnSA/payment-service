package com.wsnsa.payment.payment;

import java.math.BigDecimal;
import java.util.List;

public record CreatePaymentCommand(
        String orderId,
        BigDecimal amount,
        String description,
        String receiverCode,
        Receiver receiver,
        List<Line> lines) {

    public record Receiver(String register, String name, String email, String phone) {
    }

    public record Line(String taxProductCode, String description, BigDecimal quantity, BigDecimal unitPrice,
            String note) {
    }
}
