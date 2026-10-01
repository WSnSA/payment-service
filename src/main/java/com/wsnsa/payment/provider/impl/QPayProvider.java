package com.wsnsa.payment.provider.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.wsnsa.payment.provider.DeepLink;
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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * QPay v2 merchant API.
 *
 * <p>Тохиргоо: {@code baseUrl} (https://merchant.qpay.mn/v2), {@code username}, {@code password},
 * {@code invoiceCode}, {@code invoiceReceiverCode} (сонголттой).
 */
@Component
@RequiredArgsConstructor
public class QPayProvider implements PaymentProvider {

    private static final String NAME = "QPay";

    private final HttpSupport http;
    private final TokenCache tokens;

    @Override
    public ProviderType type() {
        return ProviderType.QPAY;
    }

    @Override
    public ProviderInvoice createInvoice(ProviderInvoiceRequest req) {
        ProviderConfig cfg = req.context().config();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("invoice_code", cfg.require("invoiceCode"));
        body.put("sender_invoice_no", req.invoiceId());
        body.put("invoice_receiver_code", cfg.get("invoiceReceiverCode", "terminal"));
        body.put("invoice_description", req.description());
        body.put("amount", HttpSupport.wholeAmount(req.amount()));
        body.put("callback_url", req.context().callbackUrl());

        JsonNode res = withToken(req.context(), token -> http.exchange(NAME, c -> c.post()
                .uri(cfg.baseUrl() + "/invoice")
                .headers(h -> h.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)));

        List<DeepLink> links = new ArrayList<>();
        JsonNode urls = res.get("urls");
        if (urls != null && urls.isArray()) {
            for (JsonNode u : urls) {
                links.add(new DeepLink(HttpSupport.text(u, "name"), HttpSupport.text(u, "description"),
                        HttpSupport.text(u, "logo"), HttpSupport.text(u, "link")));
            }
        }
        return ProviderInvoice.builder()
                .providerInvoiceId(HttpSupport.requireText(NAME, res, "invoice_id"))
                .qrText(HttpSupport.text(res, "qr_text"))
                .qrImage(HttpSupport.text(res, "qr_image"))
                .shortUrl(HttpSupport.text(res, "qPay_shortUrl"))
                .deeplinks(links)
                .build();
    }

    @Override
    public PaymentStatus checkStatus(ProviderContext ctx, String invoiceId, String providerInvoiceId) {
        ProviderConfig cfg = ctx.config();
        Map<String, Object> body = Map.of(
                "object_type", "INVOICE",
                "object_id", providerInvoiceId,
                "offset", Map.of("page_number", 1, "page_limit", 100));

        JsonNode res = withToken(ctx, token -> http.exchange(NAME, c -> c.post()
                .uri(cfg.baseUrl() + "/payment/check")
                .headers(h -> h.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)));

        JsonNode rows = res.get("rows");
        if (rows == null || !rows.isArray() || rows.isEmpty()) {
            return PaymentStatus.pending();
        }
        BigDecimal paid = BigDecimal.ZERO;
        String paymentId = null;
        for (JsonNode row : rows) {
            if ("PAID".equals(HttpSupport.text(row, "payment_status"))) {
                BigDecimal a = HttpSupport.decimal(row, "payment_amount");
                if (a != null) paid = paid.add(a);
                if (paymentId == null) paymentId = HttpSupport.text(row, "payment_id");
            }
        }
        if (paymentId == null) {
            return PaymentStatus.pending();
        }
        BigDecimal total = HttpSupport.decimal(res, "paid_amount");
        return PaymentStatus.paid(paymentId, total != null ? total : paid);
    }

    @Override
    public void cancel(ProviderContext ctx, String providerInvoiceId) {
        ProviderConfig cfg = ctx.config();
        withToken(ctx, token -> http.exchange(NAME, c -> c.delete()
                .uri(cfg.baseUrl() + "/invoice/" + providerInvoiceId)
                .headers(h -> h.setBearerAuth(token))));
    }

    private JsonNode withToken(ProviderContext ctx, Function<String, JsonNode> call) {
        String key = "QPAY:" + ctx.merchantId();
        try {
            return call.apply(tokens.get(key, () -> authenticate(ctx.config())));
        } catch (HttpSupport.UnauthorizedException e) {
            tokens.invalidate(key);
            return call.apply(tokens.get(key, () -> authenticate(ctx.config())));
        }
    }

    private TokenCache.Token authenticate(ProviderConfig cfg) {
        JsonNode res = http.exchange(NAME, c -> c.post()
                .uri(cfg.baseUrl() + "/auth/token")
                .headers(h -> h.setBasicAuth(cfg.require("username"), cfg.require("password"))));
        String token = HttpSupport.requireText(NAME, res, "access_token");
        return new TokenCache.Token(token, ttl(res.path("expires_in").asLong(0)));
    }

    /** QPay {@code expires_in}-ийг заримдаа epoch секундээр буцаадаг. */
    static Duration ttl(long expiresIn) {
        if (expiresIn <= 0) return Duration.ofMinutes(10);
        if (expiresIn > 1_000_000_000L) {
            Duration d = Duration.between(Instant.now(), Instant.ofEpochSecond(expiresIn));
            return d.isNegative() ? Duration.ZERO : d;
        }
        return Duration.ofSeconds(expiresIn);
    }
}
