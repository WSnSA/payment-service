package com.wsnsa.payment.provider.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.wsnsa.payment.common.Crypto;
import com.wsnsa.payment.provider.HttpSupport;
import com.wsnsa.payment.provider.PaymentProvider;
import com.wsnsa.payment.provider.PaymentStatus;
import com.wsnsa.payment.provider.ProviderConfig;
import com.wsnsa.payment.provider.ProviderContext;
import com.wsnsa.payment.provider.ProviderInvoice;
import com.wsnsa.payment.provider.ProviderInvoiceRequest;
import com.wsnsa.payment.provider.ProviderType;
import org.springframework.http.MediaType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Голомт банкны e-commerce API (карт болон SocialPay).
 *
 * <p>Тохиргоо: {@code baseUrl} (https://ecommerce.golomtbank.com/api), {@code token},
 * {@code hmacKey}, {@code paymentPageUrl} (сонголттой, default https://ecommerce.golomtbank.com/payment/mn/).
 *
 * <p>Golomt-ийн transactionId нь манай нэхэмжлэхийн id байна.
 */
abstract class GolomtEcommerceProvider implements PaymentProvider {

    private static final String NAME = "Golomt";

    private final HttpSupport http;
    private final boolean social;

    GolomtEcommerceProvider(HttpSupport http, boolean social) {
        this.http = http;
        this.social = social;
    }

    @Override
    public ProviderInvoice createInvoice(ProviderInvoiceRequest req) {
        ProviderConfig cfg = req.context().config();
        String amount = String.valueOf(HttpSupport.wholeAmount(req.amount()));
        String returnType = "POST";
        String callback = req.context().callbackUrl();
        String checksum = Crypto.hmacSha256Hex(cfg.require("hmacKey"), req.invoiceId() + amount + returnType + callback);

        Map<String, String> body = new LinkedHashMap<>();
        body.put("amount", amount);
        body.put("callback", callback);
        body.put("checksum", checksum);
        body.put("genToken", "N");
        body.put("returnType", returnType);
        body.put("transactionId", req.invoiceId());
        if (social) body.put("socialDeeplink", "Y");

        JsonNode res = http.exchange(NAME, c -> c.post()
                .uri(cfg.baseUrl() + "/invoice")
                .headers(h -> h.setBearerAuth(cfg.require("token")))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body));

        String invoice = HttpSupport.requireText(NAME, res, "invoice");
        String pageBase = cfg.get("paymentPageUrl", "https://ecommerce.golomtbank.com/payment/mn/");
        return ProviderInvoice.builder()
                .providerInvoiceId(invoice)
                .paymentUrl(pageBase + invoice)
                .deeplink(social ? HttpSupport.text(res, "socialDeeplink") : null)
                .build();
    }

    @Override
    public PaymentStatus checkStatus(ProviderContext ctx, String invoiceId, String providerInvoiceId) {
        ProviderConfig cfg = ctx.config();
        String checksum = Crypto.hmacSha256Hex(cfg.require("hmacKey"), invoiceId + invoiceId);
        Map<String, String> body = Map.of("checksum", checksum, "transactionId", invoiceId);

        JsonNode res = http.exchange(NAME, c -> c.post()
                .uri(cfg.baseUrl() + "/inquiry")
                .headers(h -> h.setBearerAuth(cfg.require("token")))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body));

        String errorCode = HttpSupport.text(res, "errorCode");
        if ("000".equals(errorCode)) {
            return PaymentStatus.paid(invoiceId, HttpSupport.decimal(res, "amount"));
        }
        if (errorCode == null || errorCode.isBlank()) {
            return PaymentStatus.pending();
        }
        // Golomt: 000 амжилттай, бусад код нь төлбөр амжилтгүй болсныг илэрхийлнэ.
        return PaymentStatus.failed(errorCode + ":" + HttpSupport.text(res, "errorDesc"));
    }
}
