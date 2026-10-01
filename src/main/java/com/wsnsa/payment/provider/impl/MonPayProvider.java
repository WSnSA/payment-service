package com.wsnsa.payment.provider.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.wsnsa.payment.provider.HttpSupport;
import com.wsnsa.payment.provider.PaymentProvider;
import com.wsnsa.payment.provider.PaymentStatus;
import com.wsnsa.payment.provider.ProviderConfig;
import com.wsnsa.payment.provider.ProviderContext;
import com.wsnsa.payment.provider.ProviderException;
import com.wsnsa.payment.provider.ProviderInvoice;
import com.wsnsa.payment.provider.ProviderInvoiceRequest;
import com.wsnsa.payment.provider.ProviderType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MonPay branch invoice API.
 *
 * <p>Тохиргоо: {@code baseUrl}, {@code token}, {@code receiver}.
 */
@Component
@RequiredArgsConstructor
public class MonPayProvider implements PaymentProvider {

    private static final String NAME = "MonPay";

    private final HttpSupport http;

    @Override
    public ProviderType type() {
        return ProviderType.MONPAY;
    }

    @Override
    public ProviderInvoice createInvoice(ProviderInvoiceRequest req) {
        ProviderConfig cfg = req.context().config();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("redirectUri", req.context().redirectUrl() != null ? req.context().redirectUrl() : req.context().callbackUrl());
        body.put("receiver", cfg.require("receiver"));
        body.put("amount", HttpSupport.wholeAmount(req.amount()));
        body.put("invoiceType", "P2B");
        body.put("description", req.description() == null ? "" : req.description());
        body.put("clientServiceUrl", req.context().callbackUrl());

        JsonNode res = http.exchange(NAME, c -> c.post()
                .uri(cfg.baseUrl() + "/invoice")
                .headers(h -> h.setBearerAuth(cfg.require("token")))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body));

        JsonNode result = res.get("result");
        if (result == null || !result.isObject()) {
            throw new ProviderException(NAME + " response missing 'result': " + HttpSupport.text(res, "info"));
        }
        return ProviderInvoice.builder()
                .providerInvoiceId(HttpSupport.requireText(NAME, result, "id"))
                .deeplink(HttpSupport.text(result, "redirectUri"))
                .build();
    }

    @Override
    public PaymentStatus checkStatus(ProviderContext ctx, String invoiceId, String providerInvoiceId) {
        ProviderConfig cfg = ctx.config();
        JsonNode res = http.exchange(NAME, c -> c.get()
                .uri(cfg.baseUrl() + "/invoice/" + providerInvoiceId)
                .headers(h -> h.setBearerAuth(cfg.require("token")))
                .accept(MediaType.APPLICATION_JSON));
        JsonNode result = res.path("result");
        String status = HttpSupport.text(result, "status");
        if ("SUCCESS".equalsIgnoreCase(status) || "PAID".equalsIgnoreCase(status)) {
            String txn = HttpSupport.text(result, "transactionId");
            return PaymentStatus.paid(txn != null ? txn : providerInvoiceId, HttpSupport.decimal(result, "amount"));
        }
        if ("FAILED".equalsIgnoreCase(status) || "CANCELLED".equalsIgnoreCase(status) || "EXPIRED".equalsIgnoreCase(status)) {
            return PaymentStatus.failed(status);
        }
        return PaymentStatus.pending();
    }
}
