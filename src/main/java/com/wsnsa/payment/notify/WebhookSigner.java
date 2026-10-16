package com.wsnsa.payment.notify;

import com.wsnsa.payment.common.Crypto;

/**
 * Merchant руу илгээх webhook-ийн гарын үсэг.
 *
 * <p>Header: {@code X-Payment-Signature: t=<unix seconds>,v1=<hex HMAC-SHA256(secret, t + "." + body)>}
 * Merchant тал ижил аргаар тооцоолж, мөн {@code t} хэт хуучин эсэхийг (жишээ нь 5 мин) шалгана.
 */
public final class WebhookSigner {

    public static final String HEADER = "X-Payment-Signature";

    private WebhookSigner() {
    }

    public static String sign(String secret, long timestamp, String body) {
        return "t=" + timestamp + ",v1=" + Crypto.hmacSha256Hex(secret, timestamp + "." + body);
    }

    public static boolean verify(String secret, String header, String body, long nowSeconds, long toleranceSeconds) {
        if (header == null) return false;
        Long t = null;
        String v1 = null;
        for (String part : header.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length != 2) continue;
            if (kv[0].equals("t")) {
                try {
                    t = Long.parseLong(kv[1]);
                } catch (NumberFormatException e) {
                    return false;
                }
            } else if (kv[0].equals("v1")) {
                v1 = kv[1];
            }
        }
        if (t == null || v1 == null || Math.abs(nowSeconds - t) > toleranceSeconds) return false;
        return Crypto.constantTimeEquals(v1, Crypto.hmacSha256Hex(secret, t + "." + body));
    }
}
