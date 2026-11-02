package com.wsnsa.payment.qpay;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.springframework.web.client.RestClient;

final class QPayTestSupport {

    private QPayTestSupport() {
    }

    static QPayProperties properties(MockWebServer server) {
        return new QPayProperties(server.url("/").toString().replaceAll("/$", ""), "TEST_MERCHANT", "secret",
                "TEST_INVOICE", "https://pay.example.mn", null, Duration.ofSeconds(2), Duration.ofSeconds(2),
                Duration.ofSeconds(60), new QPayProperties.Ebarimt(false, "CITIZEN"));
    }

    static RestClient restClient(QPayProperties properties) {
        return new QPayConfig().qpayRestClient(RestClient.builder(), properties, new ObjectMapper());
    }

    static QPayTokenManager tokenManager(QPayProperties properties, Clock clock) {
        return new QPayTokenManager(restClient(properties), properties, clock);
    }

    static MockResponse json(int status, String body) {
        return new MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body);
    }

    static MockResponse token(String access, long expiresAt, String refresh, long refreshExpiresAt) {
        return json(200, """
                {"token_type":"bearer","access_token":"%s","expires_in":%d,
                 "refresh_token":"%s","refresh_expires_in":%d,"scope":"get_token",
                 "not-before-policy":"0","session_state":"prod"}
                """.formatted(access, expiresAt, refresh, refreshExpiresAt));
    }
}
