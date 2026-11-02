package com.wsnsa.payment.qpay.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record InvoiceResponse(
        String invoiceId,
        String qrText,
        String qrImage,
        @JsonProperty("qPay_shortUrl") String shortUrl,
        @JsonProperty("qPay_deeplink") List<Deeplink> deeplinks) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Deeplink(String name, String description, String logo, String link) {
    }
}
