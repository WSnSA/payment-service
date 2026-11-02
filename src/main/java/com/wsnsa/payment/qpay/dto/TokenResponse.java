package com.wsnsa.payment.qpay.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * /v2/auth/token болон /v2/auth/refresh хариу.
 * QPay баримт бичигт {@code expires_in}, {@code refresh_expires_in} нь unix timestamp (секунд) гэж заасан.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TokenResponse(
        String tokenType,
        String accessToken,
        Long expiresIn,
        String refreshToken,
        Long refreshExpiresIn,
        String scope) {

    @Override
    public String toString() {
        // Токеныг лог руу гаргахгүй
        return "TokenResponse{tokenType=" + tokenType + ", expiresIn=" + expiresIn
                + ", refreshExpiresIn=" + refreshExpiresIn + ", scope=" + scope + "}";
    }
}
