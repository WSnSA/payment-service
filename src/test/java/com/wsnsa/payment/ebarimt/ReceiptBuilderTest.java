package com.wsnsa.payment.ebarimt;

import com.wsnsa.payment.common.ApiException;
import com.wsnsa.payment.merchant.EbarimtSettings;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReceiptBuilderTest {

    private static EbarimtSettings settings() {
        EbarimtSettings s = new EbarimtSettings();
        s.setMode(EbarimtMode.PER_PAYMENT);
        s.setPosApiUrl("http://posapi");
        s.setMerchantTin("12345678901");
        s.setPosNo("10012345");
        s.setDistrictCode("25");
        s.setBranchNo("001");
        s.setClassificationCode("3600100");
        s.setDefaultItemName("Ус");
        s.setMeasureUnit("литр");
        return s;
    }

    @Test
    void defaultsToSingleItemWithMerchantDefaults() {
        ReceiptBuilder.Built b = ReceiptBuilder.build(settings(), ReceiptSpec.empty(), new BigDecimal("5500"), "desc", "12345678");
        PosApiModels.CreateReceipt r = b.request();
        assertThat(r.type()).isEqualTo("B2C_RECEIPT");
        assertThat(r.consumerNo()).isEqualTo("12345678");
        assertThat(r.customerTin()).isNull();
        assertThat(r.totalAmount()).isEqualByComparingTo("5500");
        assertThat(r.totalVat()).isEqualByComparingTo("500");
        assertThat(r.receipts()).hasSize(1);
        PosApiModels.Item item = r.receipts().get(0).items().get(0);
        assertThat(item.name()).isEqualTo("Ус");
        assertThat(item.classificationCode()).isEqualTo("3600100");
        assertThat(item.measureUnit()).isEqualTo("литр");
        assertThat(r.payments().get(0).code()).isEqualTo("PAYMENT_CARD");
        assertThat(r.payments().get(0).paidAmount()).isEqualByComparingTo("5500");
    }

    @Test
    void groupsItemsByTaxType() {
        ReceiptSpec spec = new ReceiptSpec(true, ReceiptType.B2B_RECEIPT, "12345678901", null, PaymentCode.CASH, null, null, List.of(
                new ReceiptItemSpec("A", null, null, null, null, new BigDecimal("2"), new BigDecimal("550"), null, null),
                new ReceiptItemSpec("B", null, null, "1111111", null, BigDecimal.ONE, new BigDecimal("300"), TaxType.VAT_FREE, "305")));
        ReceiptBuilder.validate(settings(), spec, new BigDecimal("1400"));
        PosApiModels.CreateReceipt r = ReceiptBuilder.build(settings(), spec, new BigDecimal("1400"), null, null).request();
        assertThat(r.type()).isEqualTo("B2B_RECEIPT");
        assertThat(r.customerTin()).isEqualTo("12345678901");
        assertThat(r.receipts()).hasSize(2);
        assertThat(r.totalAmount()).isEqualByComparingTo("1400");
        assertThat(r.totalVat()).isEqualByComparingTo("100");
        assertThat(r.payments().get(0).code()).isEqualTo("CASH");
    }

    @Test
    void rejectsItemTotalMismatch() {
        ReceiptSpec spec = new ReceiptSpec(true, null, null, null, null, null, null, List.of(
                new ReceiptItemSpec("A", null, null, null, null, BigDecimal.ONE, new BigDecimal("100"), null, null)));
        assertThatThrownBy(() -> ReceiptBuilder.validate(settings(), spec, new BigDecimal("200")))
                .isInstanceOf(ApiException.class).hasMessageContaining("does not equal");
    }

    @Test
    void rejectsB2bWithoutTinAndMissingSettings() {
        ReceiptSpec b2b = new ReceiptSpec(true, ReceiptType.B2B_RECEIPT, null, null, null, null, null, null);
        assertThatThrownBy(() -> ReceiptBuilder.validate(settings(), b2b, BigDecimal.TEN))
                .isInstanceOf(ApiException.class).hasMessageContaining("customerTin");

        EbarimtSettings empty = new EbarimtSettings();
        assertThatThrownBy(() -> ReceiptBuilder.validate(empty, ReceiptSpec.empty(), BigDecimal.TEN))
                .isInstanceOf(ApiException.class).hasMessageContaining("posApiUrl");
    }
}
