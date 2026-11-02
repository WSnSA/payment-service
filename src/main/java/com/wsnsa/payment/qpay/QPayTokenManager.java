package com.wsnsa.payment.qpay;

import com.wsnsa.payment.qpay.dto.TokenResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * QPay access token-ийг санах ойд хадгалж, хугацаа дуусахад refresh token-оор сунгана.
 *
 * <p>QPay-ийн анхааруулга: "Токен хугацаа дуусахаас өмнө ойр ойрхон олон дахин токен авч болохгүй."
 * Тиймээс токеныг нэг л удаа авч, хугацаа дуусах үед л шинэчилнэ.
 */
@Component
public class QPayTokenManager {

    private static final Logger log = LoggerFactory.getLogger(QPayTokenManager.class);

    /** Үүнээс их утгатай expires_in-ийг unix timestamp гэж үзнэ, бага бол секундын тоо гэж үзнэ. */
    private static final long EPOCH_THRESHOLD = 1_000_000_000L;

    private final RestClient restClient;
    private final QPayProperties properties;
    private final Clock clock;
    private final ReentrantLock lock = new ReentrantLock();

    private String accessToken;
    private long accessExpiresAt;
    private String refreshToken;
    private long refreshExpiresAt;

    public QPayTokenManager(RestClient qpayRestClient, QPayProperties properties, Clock clock) {
        this.restClient = qpayRestClient;
        this.properties = properties;
        this.clock = clock;
    }

    /** Хүчинтэй access token буцаана. Шаардлагатай бол refresh эсвэл шинээр авна. */
    public String getAccessToken() {
        lock.lock();
        try {
            long now = now();
            long skew = properties.tokenExpirySkew().toSeconds();
            if (accessToken != null && now < accessExpiresAt - skew) {
                return accessToken;
            }
            if (refreshToken != null && now < refreshExpiresAt - skew) {
                try {
                    store(requestRefresh(refreshToken));
                    log.info("QPay access token refreshed");
                    return accessToken;
                } catch (QPayApiException e) {
                    log.warn("QPay token refresh failed ({}), requesting a new token", e.getError());
                }
            }
            store(requestNewToken());
            log.info("QPay access token obtained");
            return accessToken;
        } finally {
            lock.unlock();
        }
    }

    /**
     * QPay 401 буцаасан үед дуудна. Зөвхөн тухайн (хуучирсан) токеныг хүчингүй болгоно -
     * өөр thread аль хэдийн шинэчилсэн бол дахин авахгүй.
     */
    public void invalidate(String token) {
        lock.lock();
        try {
            if (token != null && token.equals(accessToken)) {
                accessToken = null;
                accessExpiresAt = 0;
            }
        } finally {
            lock.unlock();
        }
    }

    private TokenResponse requestNewToken() {
        try {
            return restClient.post()
                    .uri("/v2/auth/token")
                    .headers(h -> h.setBasicAuth(properties.clientId(), properties.clientSecret()))
                    .retrieve()
                    .body(TokenResponse.class);
        } catch (ResourceAccessException e) {
            throw new QPayApiException("QPay token request failed: " + e.getMessage(), e);
        }
    }

    private TokenResponse requestRefresh(String token) {
        try {
            return restClient.post()
                    .uri("/v2/auth/refresh")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(TokenResponse.class);
        } catch (ResourceAccessException e) {
            throw new QPayApiException("QPay token refresh failed: " + e.getMessage(), e);
        }
    }

    private void store(TokenResponse response) {
        if (response == null || response.accessToken() == null) {
            throw new QPayApiException(502, "INVALID_TOKEN_RESPONSE", "QPay returned an empty token response");
        }
        long now = now();
        this.accessToken = response.accessToken();
        this.accessExpiresAt = toEpochSeconds(response.expiresIn(), now, Duration.ofHours(1));
        if (response.refreshToken() != null) {
            this.refreshToken = response.refreshToken();
            this.refreshExpiresAt = toEpochSeconds(response.refreshExpiresIn(), now, Duration.ofHours(1));
        }
    }

    static long toEpochSeconds(Long value, long now, Duration fallback) {
        if (value == null || value <= 0) {
            return now + fallback.toSeconds();
        }
        return value >= EPOCH_THRESHOLD ? value : now + value;
    }

    private long now() {
        return clock.instant().getEpochSecond();
    }
}
