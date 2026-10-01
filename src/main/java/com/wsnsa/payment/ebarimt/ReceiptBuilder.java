package com.wsnsa.payment.ebarimt;

import com.wsnsa.payment.common.ApiException;
import com.wsnsa.payment.merchant.EbarimtSettings;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * {@link ReceiptSpec} + merchant тохиргооноос PosAPI хүсэлт угсарна.
 */
public final class ReceiptBuilder {

    static final String DEFAULT_ITEM_NAME = "Бараа, үйлчилгээ";

    private ReceiptBuilder() {
    }

    /** Merchant тохиргоо болон spec-ийг шалгана. Алдаатай бол {@link ApiException}. */
    public static void validate(EbarimtSettings s, ReceiptSpec spec, BigDecimal amount) {
        List<String> missing = new ArrayList<>();
        if (blank(s.getPosApiUrl())) missing.add("posApiUrl");
        if (blank(s.getMerchantTin())) missing.add("merchantTin");
        if (blank(s.getPosNo())) missing.add("posNo");
        if (blank(spec.districtCode()) && blank(s.getDistrictCode())) missing.add("districtCode");
        if (blank(spec.branchNo()) && blank(s.getBranchNo())) missing.add("branchNo");
        boolean itemsLackCode = spec.items() == null || spec.items().isEmpty()
                || spec.items().stream().anyMatch(i -> blank(i.classificationCode()));
        if (itemsLackCode && blank(s.getClassificationCode())) missing.add("classificationCode");
        if (!missing.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "ebarimt_not_configured",
                    "Merchant ebarimt settings missing: " + String.join(", ", missing));
        }
        if (spec.typeOrDefault() == ReceiptType.B2B_RECEIPT && blank(spec.customerTin())) {
            throw ApiException.badRequest("customer_tin_required", "B2B receipt requires customerTin");
        }
        if (spec.items() != null && !spec.items().isEmpty()) {
            BigDecimal sum = BigDecimal.ZERO;
            for (ReceiptItemSpec i : spec.items()) {
                sum = sum.add(lineTotal(i));
            }
            if (sum.compareTo(amount.setScale(2, RoundingMode.HALF_UP)) != 0) {
                throw ApiException.badRequest("receipt_amount_mismatch",
                        "Receipt items total " + sum.toPlainString() + " does not equal amount " + amount.toPlainString());
            }
        }
    }

    public record Built(PosApiModels.CreateReceipt request, BigDecimal totalAmount, BigDecimal totalVat, BigDecimal totalCityTax) {
    }

    /**
     * @param fallbackName мөр заагаагүй үед ашиглах нэр (нэхэмжлэхийн тайлбар)
     * @param consumerNo   шалгагдсан иргэний дугаар (хүчингүй бол null)
     */
    public static Built build(EbarimtSettings s, ReceiptSpec spec, BigDecimal amount, String fallbackName, String consumerNo) {
        List<ReceiptItemSpec> items = spec.items();
        if (items == null || items.isEmpty()) {
            String name = !blank(s.getDefaultItemName()) ? s.getDefaultItemName()
                    : !blank(fallbackName) ? fallbackName : DEFAULT_ITEM_NAME;
            items = List.of(new ReceiptItemSpec(name, null, null, null, null, BigDecimal.ONE, amount, null, null));
        }

        TaxType defaultTax = s.getTaxType() == null ? TaxType.VAT_ABLE : s.getTaxType();
        Map<TaxType, List<PosApiModels.Item>> grouped = new EnumMap<>(TaxType.class);
        Map<TaxType, BigDecimal[]> sums = new EnumMap<>(TaxType.class);

        for (ReceiptItemSpec i : items) {
            TaxType tt = i.taxType() == null ? defaultTax : i.taxType();
            TaxCalculator.Tax tax = TaxCalculator.compute(lineTotal(i), tt, s.getCityTaxRate());
            grouped.computeIfAbsent(tt, k -> new ArrayList<>()).add(new PosApiModels.Item(
                    i.name(),
                    blank(i.barCode()) ? null : i.barCode(),
                    blank(i.barCodeType()) ? "UNDEFINED" : i.barCodeType(),
                    blank(i.classificationCode()) ? s.getClassificationCode() : i.classificationCode(),
                    i.taxProductCode(),
                    blank(i.measureUnit()) ? (blank(s.getMeasureUnit()) ? "ш" : s.getMeasureUnit()) : i.measureUnit(),
                    i.quantity(),
                    i.unitPrice(),
                    tax.vat(),
                    tax.cityTax(),
                    tax.total(),
                    null));
            BigDecimal[] acc = sums.computeIfAbsent(tt, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            acc[0] = acc[0].add(tax.total());
            acc[1] = acc[1].add(tax.vat());
            acc[2] = acc[2].add(tax.cityTax());
        }

        List<PosApiModels.SubReceipt> subs = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO, vat = BigDecimal.ZERO, city = BigDecimal.ZERO;
        for (Map.Entry<TaxType, List<PosApiModels.Item>> e : grouped.entrySet()) {
            BigDecimal[] acc = sums.get(e.getKey());
            subs.add(new PosApiModels.SubReceipt(acc[0], acc[1], acc[2], e.getKey().name(), s.getMerchantTin(), null, null, e.getValue()));
            total = total.add(acc[0]);
            vat = vat.add(acc[1]);
            city = city.add(acc[2]);
        }

        PaymentCode code = spec.paymentCode() == null ? PaymentCode.PAYMENT_CARD : spec.paymentCode();
        ReceiptType type = spec.typeOrDefault();
        PosApiModels.CreateReceipt req = new PosApiModels.CreateReceipt(
                total, vat, city,
                blank(spec.districtCode()) ? s.getDistrictCode() : spec.districtCode(),
                s.getMerchantTin(),
                blank(spec.branchNo()) ? s.getBranchNo() : spec.branchNo(),
                s.getPosNo(),
                type == ReceiptType.B2B_RECEIPT ? spec.customerTin() : null,
                type == ReceiptType.B2C_RECEIPT ? consumerNo : null,
                type.name(),
                null, null, null,
                subs,
                List.of(new PosApiModels.Payment(code.name(), "PAID", total, null)));
        return new Built(req, total, vat, city);
    }

    static BigDecimal lineTotal(ReceiptItemSpec i) {
        return i.unitPrice().multiply(i.quantity()).setScale(2, RoundingMode.HALF_UP);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
