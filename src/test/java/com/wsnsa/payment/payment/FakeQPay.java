package com.wsnsa.payment.payment;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;

/** Тестэд ашиглах QPay-ийн хуурамч сервер. */
class FakeQPay extends Dispatcher {

    final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    final AtomicInteger tokenRequests = new AtomicInteger();
    final AtomicInteger invoiceCounter = new AtomicInteger();
    volatile String checkResponse = "{\"count\":0,\"paid_amount\":0,\"rows\":[]}";
    volatile MockResponse invoiceFailure;
    volatile MockResponse cancelResponse;

    @Override
    public MockResponse dispatch(RecordedRequest request) {
        requests.add(request);
        String path = request.getPath() == null ? "" : request.getPath();
        String method = request.getMethod();
        if (path.equals("/v2/auth/token")) {
            tokenRequests.incrementAndGet();
            long exp = System.currentTimeMillis() / 1000 + 3600;
            return json(200, """
                    {"token_type":"bearer","access_token":"test-access","expires_in":%d,
                     "refresh_token":"test-refresh","refresh_expires_in":%d}
                    """.formatted(exp, exp + 3600));
        }
        if (!"Bearer test-access".equals(request.getHeader("Authorization"))) {
            return json(401, "{\"error\":\"NO_CREDENDIALS\",\"message\":\"NO_CREDENDIALS\"}");
        }
        if (path.equals("/v2/invoice") && method.equals("POST")) {
            if (invoiceFailure != null) {
                return invoiceFailure;
            }
            int n = invoiceCounter.incrementAndGet();
            return json(200, """
                    {"invoice_id":"invoice-%d","qr_text":"qr-%d","qr_image":"iVBORw0KGgo=",
                     "qPay_shortUrl":"https://s.qpay.mn/abc%d",
                     "qPay_deeplink":[{"name":"Khan bank","description":"Хаан банк",
                       "logo":"https://qpay.mn/q/logo/khanbank.png","link":"khanbank://q?qPay_QRcode=qr-%d"}]}
                    """.formatted(n, n, n, n));
        }
        if (path.startsWith("/v2/invoice/") && method.equals("DELETE")) {
            return cancelResponse != null ? cancelResponse : json(200, "{}");
        }
        if (path.equals("/v2/payment/check")) {
            return json(200, checkResponse);
        }
        if (path.startsWith("/v2/payment/refund/")) {
            return json(200, "{}");
        }
        if (path.equals("/v2/ebarimt/create")) {
            return json(200, """
                    {"id":"ebarimt-1","g_payment_id":"qpay-payment-1","amount":"1500","vat_amount":"136.36",
                     "ebarimt_qr_data":"2518521013","ebarimt_lottery":"QQ 69281365","ebarimt_status":"REGISTERED"}
                    """);
        }
        return json(404, "{\"error\":\"NOT_FOUND\",\"message\":\"" + method + " " + path + "\"}");
    }

    static String paid(String paymentId, String amount, String type) {
        return """
                {"count":1,"paid_amount":%s,"rows":[{"payment_id":"%s","payment_status":"PAID",
                 "payment_amount":"%s","trx_fee":"0","payment_currency":"MNT","payment_wallet":"Хаан банк апп",
                 "payment_type":"%s"}]}
                """.formatted(amount, paymentId, amount, type);
    }

    RecordedRequest last(String pathPrefix) {
        for (int i = requests.size() - 1; i >= 0; i--) {
            if (requests.get(i).getPath().startsWith(pathPrefix)) {
                return requests.get(i);
            }
        }
        throw new AssertionError("No request to " + pathPrefix);
    }

    static MockResponse json(int status, String body) {
        return new MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json")
                .setBody(body);
    }
}
