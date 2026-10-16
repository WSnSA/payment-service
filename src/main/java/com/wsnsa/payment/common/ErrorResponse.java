package com.wsnsa.payment.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(String code, String message, Map<String, String> details) {

    public ErrorResponse(String code, String message) {
        this(code, message, Map.of());
    }
}
