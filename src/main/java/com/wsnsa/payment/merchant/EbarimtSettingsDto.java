package com.wsnsa.payment.merchant;

import com.wsnsa.payment.ebarimt.EbarimtMode;
import com.wsnsa.payment.ebarimt.TaxType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record EbarimtSettingsDto(
        EbarimtMode mode,
        @Size(max = 255) @Pattern(regexp = "^https?://.*", message = "must be an http(s) URL") String posApiUrl,
        @Pattern(regexp = "\\d{11}|\\d{14}", message = "must be 11 or 14 digits") String merchantTin,
        @Size(max = 20) String posNo,
        @Size(max = 10) String districtCode,
        @Size(max = 10) String branchNo,
        @Pattern(regexp = "\\d{7}", message = "must be 7 digits") String classificationCode,
        @Size(max = 255) String defaultItemName,
        @Size(max = 20) String measureUnit,
        TaxType taxType,
        @DecimalMin("0") @DecimalMax("10") BigDecimal cityTaxRate
) {
    public static EbarimtSettingsDto of(EbarimtSettings s) {
        return new EbarimtSettingsDto(s.getMode(), s.getPosApiUrl(), s.getMerchantTin(), s.getPosNo(), s.getDistrictCode(),
                s.getBranchNo(), s.getClassificationCode(), s.getDefaultItemName(), s.getMeasureUnit(), s.getTaxType(),
                s.getCityTaxRate());
    }

    public void applyTo(EbarimtSettings s) {
        if (mode != null) s.setMode(mode);
        s.setPosApiUrl(posApiUrl);
        s.setMerchantTin(merchantTin);
        s.setPosNo(posNo);
        s.setDistrictCode(districtCode);
        s.setBranchNo(branchNo);
        s.setClassificationCode(classificationCode);
        s.setDefaultItemName(defaultItemName);
        s.setMeasureUnit(measureUnit);
        s.setTaxType(taxType == null ? TaxType.VAT_ABLE : taxType);
        s.setCityTaxRate(cityTaxRate == null ? BigDecimal.ZERO : cityTaxRate);
    }
}
