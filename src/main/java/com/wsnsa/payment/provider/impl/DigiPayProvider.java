package com.wsnsa.payment.provider.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.wsnsa.payment.provider.HttpSupport;
import com.wsnsa.payment.provider.PaymentProvider;
import com.wsnsa.payment.provider.PaymentStatus;
import com.wsnsa.payment.provider.ProviderConfig;
import com.wsnsa.payment.provider.ProviderContext;
import com.wsnsa.payment.provider.ProviderInvoice;
import com.wsnsa.payment.provider.ProviderInvoiceRequest;
import com.wsnsa.payment.provider.ProviderType;
import com.wsnsa.payment.provider.TokenCache;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Хаан банкны DigiPay superapp API.
 *
 * <p>Тохиргоо: {@code baseUrl} (https://api-wallet.khanbank.com), {@code clientId}, {@code clientSecret},
 * {@code merchantId}, {@code merchantName}, {@code terminalId}.
 *
 * <p>Сонголт {@code userToken}: DigiPay mini app дотроос хэрэглэгчийн token-оор нэхэмжлэх үүсгэх үед.
 */
@Component
@RequiredArgsConstructor
public class DigiPayProvider implements PaymentProvider {

    private static final String NAME = "DigiPay";

    private final HttpSupport http;
    private final TokenCache tokens;

    @Override
    public ProviderType type() {
        return ProviderType.DIGIPAY;
    }

    @Override
    public ProviderInvoice createInvoice(ProviderInvoiceRequest req) {
        ProviderConfig cfg = req.context().config();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", HttpSupport.wholeAmount(req.amount()));
        body.put("merchantId", cfg.require("merchantId"));
        body.put("merchantName", cfg.get("merchantName", "MERCHANT"));
        body.put("terminalId", cfg.require("terminalId"));
        body.put("webHookUrl", req.context().callbackUrl());

        Function<String, JsonNode> call = token -> http.exchange(NAME, c -> c.post()
                .uri(cfg.baseUrl() + "/v3/superapp/save/invoice")
                .headers(h -> h.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body));

        String userToken = req.option("userToken");
        JsonNode res = userToken != null && !userToken.isBlank() ? call.apply(userToken) : withAppToken(req.context(), call);

        return ProviderInvoice.builder()
                .providerInvoiceId(HttpSupport.requireText(NAME, res, "invoiceId"))
                .deeplink(HttpSupport.text(res, "redirectUrl"))
                .build();
    }

    @Override
    public PaymentStatus checkStatus(ProviderContext ctx, String invoiceId, String providerInvoiceId) {
        ProviderConfig cfg = ctx.config();
        JsonNode res = withAppToken(ctx, token -> http.exchange(NAME, c -> c.get()
                .uri(cfg.baseUrl() + "/v3/superapp/check/invoice?invoiceId={id}", providerInvoiceId)
                .headers(h -> h.setBearerAuth(token))));
        String status = HttpSupport.text(res, "status");
        if ("SUCCESS".equalsIgnoreCase(status) || "PAID".equalsIgnoreCase(status)) {
            String txn = HttpSupport.text(res, "txnId");
            return PaymentStatus.paid(txn != null ? txn : providerInvoiceId, HttpSupport.decimal(res, "amount"));
        }
        if ("FAILED".equalsIgnoreCase(status) || "CANCELLED".equalsIgnoreCase(status)) {
            return PaymentStatus.failed(status);
        }
        return PaymentStatus.pending();
    }

    private JsonNode withAppToken(ProviderContext ctx, Function<String, JsonNode> call) {
        String key = "DIGIPAY:" + ctx.merchantId();
        try {
            return call.apply(tokens.get(key, () -> authenticate(ctx.config())));
        } catch (HttpSupport.UnauthorizedException e) {
            tokens.invalidate(key);
            return call.apply(tokens.get(key, () -> authenticate(ctx.config())));
        }
    }

    private TokenCache.Token authenticate(ProviderConfig cfg) {
        JsonNode res = http.exchange(NAME, c -> c.post()
                .uri(cfg.baseUrl() + "/v1/wallet/auth/token?grant_type=client_credentials")
                .headers(h -> h.setBasicAuth(cfg.require("clientId"), cfg.require("clientSecret"))));
        long expiresIn = res.path("expires_in").asLong(0);
        return new TokenCache.Token(HttpSupport.requireText(NAME, res, "access_token"),
                expiresIn > 0 ? Duration.ofSeconds(expiresIn) : null);
    }
}
