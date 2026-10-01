package com.wsnsa.payment.ebarimt;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Нэхэмжлэхгүйгээр шууд баримт гаргах (жишээ нь бэлэн төлбөр, сарын нэгтгэл).
 *
 * @param externalRef Merchant-ийн давтагдашгүй дугаар (давхар баримтаас сэргийлнэ)
 * @param amount      Нийт дүн. {@code receipt.items} өгсөн бол тэдгээрийн нийлбэртэй тэнцүү байх ёстой.
 */
public record CreateReceiptRequest(
        @NotBlank @Size(max = 100) String externalRef,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal amount,
        @NotNull @Valid ReceiptSpec receipt
) {
}
