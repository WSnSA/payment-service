package com.wsnsa.payment.ebarimt;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Баримтын нэг мөр. {@code unitPrice} нь НӨАТ, НХАТ багтсан үнэ.
 *
 * @param classificationCode Бараа үйлчилгээний нэгдсэн ангиллын 7 оронтой код (хоосон бол merchant-ийн default)
 * @param taxType            Хоосон бол merchant-ийн default
 * @param taxProductCode     VAT_FREE / VAT_ZERO үед татварын 3 оронтой код
 */
public record ReceiptItemSpec(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 50) String barCode,
        @Pattern(regexp = "UNDEFINED|GS1|ISBN") String barCodeType,
        @Pattern(regexp = "\\d{7}") String classificationCode,
        @Size(max = 20) String measureUnit,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
        @NotNull @DecimalMin(value = "0") BigDecimal unitPrice,
        TaxType taxType,
        @Pattern(regexp = "\\d{3}") String taxProductCode
) {
}
