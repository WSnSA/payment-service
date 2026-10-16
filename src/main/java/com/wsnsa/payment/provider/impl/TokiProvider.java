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
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Toki third-party pay API.
 *
 * <p>Тохиргоо: {@code baseUrl}, {@code merchantId}, {@code token} (merchant-ийн Basic token),
 * {@code apiKey}, {@code basicUsername}, {@code basicPassword}.
 *
 * <p>Toki-ийн orderId нь манай нэхэмжлэхийн id. Webhook ирэхэд body-д итгэхгүй,
 * {@code /status} endpoint-оор шалгана.
 */
@Component
@RequiredArgsConstructor
public class TokiProvider implements PaymentProvider {

    private static final String NAME = "Toki";
    private static final Set<String> PAID = Set.of("SUCCESS", "SUCCEED", "COMPLETED", "PAID");
    private static final Set<String> FAILED = Set.of("FAILED", "FAILURE", "CANCELLED", "CANCELED", "REJECTED", "EXPIRED");

    private final HttpSupport http;

    @Override
    public ProviderType type() {
        return ProviderType.TOKI;
    }

    @Override
    public ProviderInvoice createInvoice(ProviderInvoiceRequest req) {
        ProviderConfig cfg = req.context().config();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("successUrl", req.context().callbackUrl());
        body.put("failureUrl", req.context().callbackUrl());
        body.put("orderId", req.invoiceId());
        body.put("merchantId", cfg.require("merchantId"));
        body.put("amount", HttpSupport.wholeAmount(req.amount()));
        body.put("notes", req.description() == null ? "" : req.description());
        body.put("authorization", "Basic " + cfg.require("token"));

        JsonNode res = http.exchange(NAME, c -> c.post()
                .uri(cfg.baseUrl() + "/deeplink")
                .headers(h -> headers(h, cfg))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body));

        JsonNode data = res.path("data");
        return ProviderInvoice.builder()
                .providerInvoiceId(req.invoiceId())
                .deeplink(HttpSupport.requireText(NAME, data, "deeplink"))
                .build();
    }

    @Override
    public PaymentStatus checkStatus(ProviderContext ctx, String invoiceId, String providerInvoiceId) {
        ProviderConfig cfg = ctx.config();
        JsonNode res = http.exchange(NAME, c -> c.get()
                .uri(cfg.baseUrl() + "/status?requestId={id}", providerInvoiceId)
                .headers(h -> headers(h, cfg)));

        JsonNode data = res.has("data") && res.get("data").isObject() ? res.get("data") : res;
        String status = HttpSupport.text(data, "status");
        if (status == null) return PaymentStatus.pending();
        String s = status.toUpperCase(Locale.ROOT);
        if (PAID.contains(s)) {
            String txn = HttpSupport.text(data, "requestId");
            return PaymentStatus.paid(txn != null ? txn : providerInvoiceId, HttpSupport.decimal(data, "amount"));
        }
        if (FAILED.contains(s)) return PaymentStatus.failed(status);
        return PaymentStatus.pending();
    }

    private static void headers(HttpHeaders h, ProviderConfig cfg) {
        h.setBasicAuth(cfg.get("basicUsername", "third_pary_pay"), cfg.get("basicPassword", "third_pary_pay"));
        h.add("api_key", cfg.get("apiKey", "third_party_pay"));
    }
}
