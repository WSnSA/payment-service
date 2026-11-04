package com.wsnsa.payment.qpay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.http.client.ClientHttpResponse;

final class QPayErrors {

    private QPayErrors() {
    }

    /**
     * QPay-ийн алдааны хариуг ({"error": "...", "message": "..."}) {@link QPayApiException} болгоно.
     */
    static QPayApiException fromResponse(String operation, ClientHttpResponse response, ObjectMapper mapper)
            throws IOException {
        int status = response.getStatusCode().value();
        String body;
        try (InputStream in = response.getBody()) {
            body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        String error = "HTTP_" + status;
        String message = body;
        try {
            JsonNode node = mapper.readTree(body);
            if (node != null && node.isObject()) {
                if (node.hasNonNull("error")) {
                    error = node.get("error").asText();
                }
                if (node.hasNonNull("message")) {
                    message = node.get("message").asText();
                }
            }
        } catch (IOException ignored) {
            // JSON биш хариу - body-г хэвээр нь message болгоно
        }
        if (message == null || message.isBlank()) {
            message = error;
        }
        return new QPayApiException(status, error, operation + " failed: " + message);
    }
}
