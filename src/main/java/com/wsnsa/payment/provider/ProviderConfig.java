package com.wsnsa.payment.provider;

import java.util.Map;

/**
 * Merchant-ийн provider тохиргоо (шифр тайлагдсан).
 */
public record ProviderConfig(ProviderType provider, Map<String, String> values) {

    public String require(String key) {
        String v = values.get(key);
        if (v == null || v.isBlank()) {
            throw new ProviderException(provider + " config '" + key + "' is not set");
        }
        return v;
    }

    public String get(String key, String defaultValue) {
        String v = values.get(key);
        return v == null || v.isBlank() ? defaultValue : v;
    }

    /** Төгсгөлийн '/'-гүй base URL. */
    public String baseUrl() {
        return require("baseUrl").replaceAll("/+$", "");
    }
}
