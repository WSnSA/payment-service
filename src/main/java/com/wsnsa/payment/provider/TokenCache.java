package com.wsnsa.payment.provider;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Provider-ийн OAuth access token-уудыг merchant тус бүрээр cache-лэнэ.
 */
@Component
public class TokenCache {

    private static final Duration SAFETY_MARGIN = Duration.ofSeconds(60);

    private final Map<String, Entry> tokens = new ConcurrentHashMap<>();

    public record Token(String value, Duration ttl) {
    }

    private record Entry(String value, Instant expiresAt) {
    }

    public String get(String key, Supplier<Token> loader) {
        Entry e = tokens.get(key);
        if (e != null && Instant.now().isBefore(e.expiresAt)) {
            return e.value;
        }
        return tokens.compute(key, (k, existing) -> {
            if (existing != null && Instant.now().isBefore(existing.expiresAt)) return existing;
            Token t = loader.get();
            Duration ttl = t.ttl() == null ? Duration.ofMinutes(10) : t.ttl().minus(SAFETY_MARGIN);
            if (ttl.isNegative()) ttl = Duration.ZERO;
            return new Entry(t.value(), Instant.now().plus(ttl));
        }).value;
    }

    public void invalidate(String key) {
        tokens.remove(key);
    }
}
