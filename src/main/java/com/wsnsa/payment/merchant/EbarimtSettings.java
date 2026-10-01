package com.wsnsa.payment.merchant;

import com.wsnsa.payment.ebarimt.EbarimtMode;
import com.wsnsa.payment.ebarimt.TaxType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Merchant-ийн И-баримтын тохиргоо. PosAPI нь ТТД бүрт тусдаа суусан байдаг тул URL нь merchant тус бүрт.
 */
@Getter
@Setter
@Embeddable
public class EbarimtSettings {

    @Enumerated(EnumType.STRING)
    @Column(name = "ebarimt_mode", length = 20, nullable = false)
    private EbarimtMode mode = EbarimtMode.DISABLED;

    /** PosAPI 3.0 хаяг, жишээ нь http://10.0.0.5:7080 */
    @Column(name = "ebarimt_posapi_url", length = 255)
    private String posApiUrl;

    /** Баримт олгогчийн ТТД. */
    @Column(name = "ebarimt_merchant_tin", length = 20)
    private String merchantTin;

    @Column(name = "ebarimt_pos_no", length = 20)
    private String posNo;

    /** Аймаг/дүүргийн код (2 оронтой). */
    @Column(name = "ebarimt_district_code", length = 10)
    private String districtCode;

    /** Салбарын дугаар (0-999). */
    @Column(name = "ebarimt_branch_no", length = 10)
    private String branchNo;

    /** Бараа үйлчилгээний нэгдсэн ангиллын код (7 оронтой) — бараанд заагаагүй бол. */
    @Column(name = "ebarimt_classification_code", length = 10)
    private String classificationCode;

    @Column(name = "ebarimt_default_item_name", length = 255)
    private String defaultItemName;

    @Column(name = "ebarimt_measure_unit", length = 20)
    private String measureUnit;

    @Enumerated(EnumType.STRING)
    @Column(name = "ebarimt_tax_type", length = 20)
    private TaxType taxType = TaxType.VAT_ABLE;

    /** НХАТ хувь (0, 1, 2). Үнэд багтсан гэж тооцно. */
    @Column(name = "ebarimt_city_tax_rate", precision = 5, scale = 2)
    private BigDecimal cityTaxRate = BigDecimal.ZERO;
}
