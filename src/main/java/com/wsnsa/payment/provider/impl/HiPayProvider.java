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
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HiPay checkout API.
 *
 * <p>Тохиргоо: {@code baseUrl} (https://api.hipay.mn), {@code token}, {@code entityId}.
 */
@Component
@RequiredArgsConstructor
public class HiPayProvider implements PaymentProvider {

    private static final String NAME = "HiPay";

    private final HttpSupport http;

    @Override
    public ProviderType type() {
        return ProviderType.HIPAY;
    }

    @Override
    public ProviderInvoice createInvoice(ProviderInvoiceRequest req) {
        ProviderConfig cfg = req.context().config();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("entityId", cfg.require("entityId"));
        body.put("amount", String.valueOf(HttpSupport.wholeAmount(req.amount())));
        body.put("currency", "MNT");
        body.put("details", req.description() == null ? "" : req.description());
        body.put("qrData", true);
        body.put("redirect_uri", req.context().callbackUrl());

        JsonNode res = http.exchange(NAME, c -> c.post()
                .uri(cfg.baseUrl() + "/checkout")
                .headers(h -> h.setBearerAuth(cfg.require("token")))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body));

        String checkoutId = HttpSupport.requireText(NAME, res, "checkoutId");
        String deeplink = "hipaywallet://checkout/" + checkoutId;
        if (req.context().redirectUrl() != null) {
            deeplink += "/" + req.context().redirectUrl();
        }
        return ProviderInvoice.builder()
                .providerInvoiceId(checkoutId)
                .qrText(HttpSupport.text(res, "qrData"))
                .deeplink(deeplink)
                .build();
    }

    @Override
    public PaymentStatus checkStatus(ProviderContext ctx, String invoiceId, String providerInvoiceId) {
        ProviderConfig cfg = ctx.config();
        JsonNode res = http.exchange(NAME, c -> c.get()
                .uri(cfg.baseUrl() + "/checkout/get/" + providerInvoiceId)
                .headers(h -> h.setBearerAuth(cfg.require("token")))
                .accept(MediaType.APPLICATION_JSON));
        String status = HttpSupport.text(res, "status");
        if ("paid".equalsIgnoreCase(status)) {
            String paymentId = HttpSupport.text(res, "paymentId");
            return PaymentStatus.paid(paymentId != null ? paymentId : providerInvoiceId, HttpSupport.decimal(res, "amount"));
        }
        if ("canceled".equalsIgnoreCase(status) || "cancelled".equalsIgnoreCase(status) || "failed".equalsIgnoreCase(status)) {
            return PaymentStatus.failed(status);
        }
        return PaymentStatus.pending();
    }
}
