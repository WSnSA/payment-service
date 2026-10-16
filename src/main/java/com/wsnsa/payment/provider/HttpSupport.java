package com.wsnsa.payment.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.function.Function;

/**
 * Provider адаптеруудын нийтлэг HTTP туслах.
 */
@Component
public class HttpSupport {

    private final RestClient restClient;
    private final ObjectMapper mapper;

    public HttpSupport(RestClient restClient, ObjectMapper mapper) {
        this.restClient = restClient;
        this.mapper = mapper;
    }

    public RestClient client() {
        return restClient;
    }

    /**
     * Хүсэлт явуулж хариуг JsonNode болгоно. 4xx/5xx үед {@link UnauthorizedException} (401) эсвэл
     * {@link ProviderException} шиднэ.
     */
    public JsonNode exchange(String provider, Function<RestClient, RestClient.RequestHeadersSpec<?>> spec) {
        try {
            String body = spec.apply(restClient).retrieve().body(String.class);
            return parse(provider, body);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatusCode.valueOf(401))) {
                throw new UnauthorizedException(provider + " returned 401");
            }
            throw new ProviderException(provider + " HTTP " + e.getStatusCode().value() + ": " + errorMessage(e.getResponseBodyAsString()), e);
        } catch (org.springframework.web.client.ResourceAccessException e) {
            throw new ProviderException(provider + " unreachable: " + e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            // Merchant-ийн тохиргоонд буруу baseUrl (жишээ нь scheme-гүй) байвал 500 биш provider_error буцаана.
            throw new ProviderException(provider + " has an invalid URL in its configuration: " + e.getMessage(), e);
        }
    }

    private JsonNode parse(String provider, String body) {
        if (body == null || body.isBlank()) return mapper.nullNode();
        try {
            return mapper.readTree(body);
        } catch (IOException e) {
            throw new ProviderException(provider + " returned non-JSON response");
        }
    }

    private String errorMessage(String body) {
        if (body == null || body.isBlank()) return "";
        try {
            JsonNode n = mapper.readTree(body);
            for (String f : new String[]{"message", "info", "description", "error_description", "error"}) {
                if (n.hasNonNull(f) && n.get(f).isValueNode()) return n.get(f).asText();
            }
        } catch (IOException ignored) {
            // JSON биш
        }
        return body.length() > 300 ? body.substring(0, 300) : body;
    }

    public static String text(JsonNode node, String field) {
        JsonNode v = node == null ? null : node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    public static String requireText(String provider, JsonNode node, String field) {
        String v = text(node, field);
        if (v == null || v.isBlank()) {
            throw new ProviderException(provider + " response missing '" + field + "'");
        }
        return v;
    }

    public static BigDecimal decimal(JsonNode node, String field) {
        JsonNode v = node == null ? null : node.get(field);
        if (v == null || v.isNull()) return null;
        try {
            return new BigDecimal(v.asText());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** MNT дүнг бүхэл тоогоор (provider-ууд ихэвчлэн бутархай хүлээж авдаггүй). */
    public static long wholeAmount(BigDecimal amount) {
        return amount.setScale(0, java.math.RoundingMode.UNNECESSARY).longValueExact();
    }

    public static class UnauthorizedException extends ProviderException {
        public UnauthorizedException(String message) {
            super(message);
        }
    }
}
