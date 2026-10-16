package com.wsnsa.payment.ebarimt;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * НӨАТ, НХАТ-ийг үнэд багтсан дүнгээс задлан тооцоолно.
 *
 * <pre>
 * VAT_ABLE: total = net * (1 + 0.10 + c)  →  vat = total * 10 / (110 + C), city = total * C / (110 + C)
 * бусад:    total = net * (1 + c)         →  vat = 0,                     city = total * C / (100 + C)
 * </pre>
 * (C = НХАТ хувь, жишээ нь 1 эсвэл 2)
 */
public final class TaxCalculator {

    private static final BigDecimal TEN = BigDecimal.TEN;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal HUNDRED_TEN = BigDecimal.valueOf(110);

    private TaxCalculator() {
    }

    public record Tax(BigDecimal total, BigDecimal vat, BigDecimal cityTax) {
    }

    public static Tax compute(BigDecimal total, TaxType taxType, BigDecimal cityTaxRate) {
        BigDecimal c = cityTaxRate == null ? BigDecimal.ZERO : cityTaxRate;
        BigDecimal t = total.setScale(2, RoundingMode.HALF_UP);
        if (taxType == TaxType.VAT_ABLE) {
            BigDecimal denom = HUNDRED_TEN.add(c);
            BigDecimal vat = t.multiply(TEN).divide(denom, 2, RoundingMode.HALF_UP);
            BigDecimal city = t.multiply(c).divide(denom, 2, RoundingMode.HALF_UP);
            return new Tax(t, vat, city);
        }
        BigDecimal city = t.multiply(c).divide(HUNDRED.add(c), 2, RoundingMode.HALF_UP);
        return new Tax(t, BigDecimal.ZERO.setScale(2), city);
    }
}
