package com.wsnsa.payment.web;

import com.wsnsa.payment.payment.CreatePaymentCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * Төлбөр үүсгэх хүсэлт. {@code lines} эсвэл {@code receiver} өгөгдвөл QPay-д дэлгэрэнгүй нэхэмжлэх,
 * эс бөгөөс энгийн (simple) нэхэмжлэх үүснэ.
 */
public record CreatePaymentRequest(
        @Size(max = 100) String orderId,
        @NotNull @DecimalMin(value = "1") @Digits(integer = 16, fraction = 2) BigDecimal amount,
        @NotBlank @Size(max = 255) String description,
        @Size(max = 45) String receiverCode,
        @Valid Receiver receiver,
        @Valid List<Line> lines) {

    public record Receiver(
            @Size(max = 20) String register,
            @Size(max = 100) String name,
            @Size(max = 255) String email,
            @Size(max = 20) String phone) {
    }

    public record Line(
            @Size(max = 45) String taxProductCode,
            @NotBlank @Size(max = 255) String description,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
            @NotNull @DecimalMin(value = "0") BigDecimal unitPrice,
            @Size(max = 100) String note) {
    }

    public CreatePaymentCommand toCommand() {
        return new CreatePaymentCommand(
                orderId,
                amount,
                description,
                receiverCode,
                receiver == null ? null
                        : new CreatePaymentCommand.Receiver(receiver.register(), receiver.name(), receiver.email(),
                                receiver.phone()),
                lines == null ? null : lines.stream()
                        .map(l -> new CreatePaymentCommand.Line(l.taxProductCode(), l.description(), l.quantity(),
                                l.unitPrice(), l.note()))
                        .toList());
    }
}
