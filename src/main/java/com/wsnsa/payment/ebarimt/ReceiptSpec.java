package com.wsnsa.payment.ebarimt;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Нэхэмжлэхтэй хамт ирэх И-баримтын өгөгдөл. Бүх талбар сонголттой:
 * мөр заагаагүй бол merchant-ийн default нэр/ангиллаар нэхэмжлэхийн дүнгээр нэг мөр гарна.
 *
 * @param enabled     false бол энэ нэхэмжлэхэд баримт гаргахгүй
 * @param customerTin B2B үед худалдан авагч байгууллагын ТТД
 * @param consumerNo  B2C үед иргэний ebarimt бүртгэлийн 8 оронтой дугаар
 * @param districtCode, branchNo merchant-ийн default-ийг дарж бичих (салбар бүрээр)
 */
public record ReceiptSpec(
        Boolean enabled,
        ReceiptType type,
        @Pattern(regexp = "\\d{11}|\\d{14}") String customerTin,
        @Pattern(regexp = "\\d{8}") String consumerNo,
        PaymentCode paymentCode,
        @Size(max = 10) String districtCode,
        @Size(max = 10) String branchNo,
        @Valid @Size(max = 500) List<ReceiptItemSpec> items
) {
    public boolean isEnabled() {
        return enabled == null || enabled;
    }

    public ReceiptType typeOrDefault() {
        return type == null ? ReceiptType.B2C_RECEIPT : type;
    }

    public static ReceiptSpec empty() {
        return new ReceiptSpec(true, null, null, null, null, null, null, null);
    }
}
