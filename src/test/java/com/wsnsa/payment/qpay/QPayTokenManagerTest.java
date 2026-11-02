package com.wsnsa.payment.qpay;

import static com.wsnsa.payment.qpay.QPayTestSupport.json;
import static com.wsnsa.payment.qpay.QPayTestSupport.token;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QPayTokenManagerTest {

    private static final long NOW = 1_700_000_000L;

    private MockWebServer server;
    private MutableClock clock;
    private QPayTokenManager manager;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        clock = new MutableClock(Instant.ofEpochSecond(NOW));
        manager = QPayTestSupport.tokenManager(QPayTestSupport.properties(server), clock);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void obtainsTokenWithBasicAuthAndCachesIt() throws Exception {
        server.enqueue(token("access-1", NOW + 3600, "refresh-1", NOW + 7200));

        assertThat(manager.getAccessToken()).isEqualTo("access-1");
        assertThat(manager.getAccessToken()).isEqualTo("access-1");

        assertThat(server.getRequestCount()).isEqualTo(1);
        RecordedRequest request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).isEqualTo("/v2/auth/token");
        String expected = "Basic " + Base64.getEncoder().encodeToString("TEST_MERCHANT:secret".getBytes());
        assertThat(request.getHeader("Authorization")).isEqualTo(expected);
    }

    @Test
    void refreshesWithRefreshTokenWhenAccessTokenExpires() throws Exception {
        server.enqueue(token("access-1", NOW + 3600, "refresh-1", NOW + 7200));
        server.enqueue(token("access-2", NOW + 7200, "refresh-2", NOW + 10800));

        manager.getAccessToken();
        clock.advance(Duration.ofSeconds(3600 - 30)); // skew (60s)-ийн дотор орсон

        assertThat(manager.getAccessToken()).isEqualTo("access-2");
        server.takeRequest();
        RecordedRequest refresh = server.takeRequest();
        assertThat(refresh.getPath()).isEqualTo("/v2/auth/refresh");
        assertThat(refresh.getHeader("Authorization")).isEqualTo("Bearer refresh-1");
    }

    @Test
    void requestsNewTokenWhenRefreshFails() throws Exception {
        server.enqueue(token("access-1", NOW + 3600, "refresh-1", NOW + 7200));
        server.enqueue(json(401, "{\"error\":\"NO_CREDENDIALS\",\"message\":\"NO_CREDENDIALS\"}"));
        server.enqueue(token("access-3", NOW + 7200, "refresh-3", NOW + 10800));

        manager.getAccessToken();
        clock.advance(Duration.ofHours(1));

        assertThat(manager.getAccessToken()).isEqualTo("access-3");
        server.takeRequest();
        assertThat(server.takeRequest().getPath()).isEqualTo("/v2/auth/refresh");
        assertThat(server.takeRequest().getPath()).isEqualTo("/v2/auth/token");
    }

    @Test
    void requestsNewTokenWhenRefreshTokenAlsoExpired() throws Exception {
        server.enqueue(token("access-1", NOW + 3600, "refresh-1", NOW + 7200));
        server.enqueue(token("access-2", NOW + 20000, "refresh-2", NOW + 30000));

        manager.getAccessToken();
        clock.advance(Duration.ofHours(3));

        assertThat(manager.getAccessToken()).isEqualTo("access-2");
        server.takeRequest();
        assertThat(server.takeRequest().getPath()).isEqualTo("/v2/auth/token");
    }

    @Test
    void invalidateForcesRenewalOnlyForTheStaleToken() {
        server.enqueue(token("access-1", NOW + 3600, "refresh-1", NOW + 7200));
        server.enqueue(token("access-2", NOW + 3600, "refresh-2", NOW + 7200));

        manager.getAccessToken();
        manager.invalidate("some-other-token");
        assertThat(manager.getAccessToken()).isEqualTo("access-1");

        manager.invalidate("access-1");
        assertThat(manager.getAccessToken()).isEqualTo("access-2");
    }

    @Test
    void wrongCredentialsSurfaceAsQPayError() {
        server.enqueue(json(401, "{\"error\":\"AUTHENTICATION_FAILED\",\"message\":\"AUTHENTICATION_FAILED\"}"));

        assertThatThrownBy(manager::getAccessToken)
                .isInstanceOfSatisfying(QPayApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(401);
                    assertThat(e.getError()).isEqualTo("AUTHENTICATION_FAILED");
                });
    }

    @Test
    void supportsExpiresInAsDurationAndAsTimestamp() {
        assertThat(QPayTokenManager.toEpochSeconds(NOW + 100, NOW, Duration.ZERO)).isEqualTo(NOW + 100);
        assertThat(QPayTokenManager.toEpochSeconds(3600L, NOW, Duration.ZERO)).isEqualTo(NOW + 3600);
        assertThat(QPayTokenManager.toEpochSeconds(null, NOW, Duration.ofHours(1))).isEqualTo(NOW + 3600);
    }
}
