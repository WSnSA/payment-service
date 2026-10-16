package com.wsnsa.payment.ebarimt;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class TaxCalculatorTest {

    @Test
    void vatIncludedNoCityTax() {
        TaxCalculator.Tax t = TaxCalculator.compute(new BigDecimal("1100"), TaxType.VAT_ABLE, BigDecimal.ZERO);
        assertThat(t.vat()).isEqualByComparingTo("100.00");
        assertThat(t.cityTax()).isEqualByComparingTo("0.00");
        assertThat(t.total()).isEqualByComparingTo("1100.00");
    }

    @Test
    void vatAndCityTaxIncluded() {
        // 1000 net + 100 НӨАТ + 20 НХАТ(2%) = 1120
        TaxCalculator.Tax t = TaxCalculator.compute(new BigDecimal("1120"), TaxType.VAT_ABLE, new BigDecimal("2"));
        assertThat(t.vat()).isEqualByComparingTo("100.00");
        assertThat(t.cityTax()).isEqualByComparingTo("20.00");
    }

    @Test
    void vatFreeOnlyCityTax() {
        TaxCalculator.Tax t = TaxCalculator.compute(new BigDecimal("1010"), TaxType.VAT_FREE, BigDecimal.ONE);
        assertThat(t.vat()).isEqualByComparingTo("0");
        assertThat(t.cityTax()).isEqualByComparingTo("10.00");
    }

    @Test
    void roundsToTwoDecimals() {
        TaxCalculator.Tax t = TaxCalculator.compute(new BigDecimal("1000"), TaxType.VAT_ABLE, BigDecimal.ZERO);
        assertThat(t.vat()).isEqualByComparingTo("90.91");
    }
}
