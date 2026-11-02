package com.wsnsa.payment.qpay;

import static com.wsnsa.payment.qpay.QPayTestSupport.json;
import static com.wsnsa.payment.qpay.QPayTestSupport.token;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wsnsa.payment.qpay.dto.EbarimtCreateRequest;
import com.wsnsa.payment.qpay.dto.EbarimtResponse;
import com.wsnsa.payment.qpay.dto.InvoiceRequest;
import com.wsnsa.payment.qpay.dto.InvoiceResponse;
import com.wsnsa.payment.qpay.dto.PaymentCancelRequest;
import com.wsnsa.payment.qpay.dto.PaymentCheckRequest;
import com.wsnsa.payment.qpay.dto.PaymentCheckResponse;
import java.math.BigDecimal;
import java.time.Instant;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QPayClientTest {

    private static final long NOW = 1_700_000_000L;
    private final ObjectMapper mapper = new ObjectMapper();

    private MockWebServer server;
    private QPayClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        QPayProperties properties = QPayTestSupport.properties(server);
        QPayTokenManager tokens = QPayTestSupport.tokenManager(properties,
                new MutableClock(Instant.ofEpochSecond(NOW)));
        client = new QPayClient(QPayTestSupport.restClient(properties), tokens);
        server.enqueue(token("access-1", NOW + 3600, "refresh-1", NOW + 7200));
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void createsSimpleInvoice() throws Exception {
        server.enqueue(json(200, """
                {"invoice_id":"d50f49f2-9032-4a74-8929-530531f28f63","qr_text":"0002010102",
                 "qr_image":"iVBORw0KGgo=","qPay_shortUrl":"https://s.qpay.mn/z1lKnIO5T",
                 "qPay_deeplink":[{"name":"Khan bank","description":"Хаан банк",
                   "logo":"https://qpay.mn/q/logo/khanbank.png","link":"khanbank://q?qPay_QRcode=0002"}]}
                """));

        InvoiceResponse response = client.createInvoice(InvoiceRequest.simple("TEST_INVOICE", "1234567", "terminal",
                "test", null, new BigDecimal("100"), "https://pay.example.mn/cb?payment_id=1234567"));

        assertThat(response.invoiceId()).isEqualTo("d50f49f2-9032-4a74-8929-530531f28f63");
        assertThat(response.shortUrl()).isEqualTo("https://s.qpay.mn/z1lKnIO5T");
        assertThat(response.deeplinks()).singleElement()
                .satisfies(d -> assertThat(d.link()).startsWith("khanbank://"));

        server.takeRequest(); // token
        RecordedRequest request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).isEqualTo("/v2/invoice");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer access-1");
        JsonNode body = mapper.readTree(request.getBody().readUtf8());
        assertThat(body.get("invoice_code").asText()).isEqualTo("TEST_INVOICE");
        assertThat(body.get("sender_invoice_no").asText()).isEqualTo("1234567");
        assertThat(body.get("invoice_receiver_code").asText()).isEqualTo("terminal");
        assertThat(body.get("invoice_description").asText()).isEqualTo("test");
        assertThat(body.get("amount").decimalValue()).isEqualByComparingTo("100");
        assertThat(body.get("callback_url").asText()).isEqualTo("https://pay.example.mn/cb?payment_id=1234567");
        assertThat(body.has("sender_branch_code")).isFalse(); // null талбар явуулахгүй
        assertThat(body.has("lines")).isFalse();
    }

    @Test
    void retriesOnceWithFreshTokenOn401() throws Exception {
        server.enqueue(json(401, "{\"error\":\"NO_CREDENDIALS\",\"message\":\"NO_CREDENDIALS\"}"));
        server.enqueue(token("access-2", NOW + 3600, "refresh-2", NOW + 7200)); // refresh хариу
        server.enqueue(json(200, "{\"count\":0,\"paid_amount\":0,\"rows\":[]}"));

        PaymentCheckResponse response = client.checkPayment(PaymentCheckRequest.forInvoice("inv-1"));

        assertThat(response.count()).isZero();
        server.takeRequest(); // token
        assertThat(server.takeRequest().getHeader("Authorization")).isEqualTo("Bearer access-1");
        server.takeRequest(); // refresh/token
        RecordedRequest retry = server.takeRequest();
        assertThat(retry.getPath()).isEqualTo("/v2/payment/check");
        assertThat(retry.getHeader("Authorization")).isEqualTo("Bearer access-2");
        JsonNode body = mapper.readTree(retry.getBody().readUtf8());
        assertThat(body.get("object_type").asText()).isEqualTo("INVOICE");
        assertThat(body.get("object_id").asText()).isEqualTo("inv-1");
        assertThat(body.get("offset").get("page_number").asInt()).isEqualTo(1);
        assertThat(body.get("offset").get("page_limit").asInt()).isEqualTo(100);
    }

    @Test
    void parsesQPayErrorBody() {
        server.enqueue(json(422, "{\"error\":\"INVOICE_NOTFOUND\",\"message\":\"Invoice is not found!\"}"));

        assertThatThrownBy(() -> client.cancelInvoice("missing"))
                .isInstanceOfSatisfying(QPayApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(422);
                    assertThat(e.getError()).isEqualTo("INVOICE_NOTFOUND");
                    assertThat(e.getMessage()).contains("Invoice is not found!");
                });
    }

    @Test
    void refundSendsDeleteWithJsonBody() throws Exception {
        server.enqueue(json(200, "{}"));

        client.refundPayment("8e25b4d5", new PaymentCancelRequest(null, "butsaalt"));

        server.takeRequest();
        RecordedRequest request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("DELETE");
        assertThat(request.getPath()).isEqualTo("/v2/payment/refund/8e25b4d5");
        assertThat(request.getBody().readUtf8()).isEqualTo("{\"note\":\"butsaalt\"}");
    }

    @Test
    void createsEbarimt() throws Exception {
        server.enqueue(json(200, """
                {"id":"f699a4be","ebarim_receiver_type":"CITIZEN","g_payment_id":"493622150113497",
                 "amount":"100","vat_amount":"9.09","city_tax_amount":"0","ebarimt_qr_data":"2518521013",
                 "ebarimt_lottery":"QQ 69281365","ebarimt_status":"REGISTERED"}
                """));

        EbarimtResponse response = client.createEbarimt(
                new EbarimtCreateRequest("493622150113497", "CITIZEN", "88614450", null));

        assertThat(response.id()).isEqualTo("f699a4be");
        assertThat(response.ebarimtReceiverType()).isEqualTo("CITIZEN");
        assertThat(response.paymentId()).isEqualTo("493622150113497");
        assertThat(response.vatAmount()).isEqualByComparingTo("9.09");
        assertThat(response.ebarimtLottery()).isEqualTo("QQ 69281365");
        server.takeRequest();
        JsonNode body = mapper.readTree(server.takeRequest().getBody().readUtf8());
        assertThat(body.get("payment_id").asText()).isEqualTo("493622150113497");
        assertThat(body.get("ebarimt_receiver_type").asText()).isEqualTo("CITIZEN");
        assertThat(body.get("ebarimt_receiver").asText()).isEqualTo("88614450");
    }

    @Test
    void connectionFailureBecomesUnavailableError() throws Exception {
        server.shutdown();

        assertThatThrownBy(() -> client.getPayment("x"))
                .isInstanceOfSatisfying(QPayApiException.class,
                        e -> assertThat(e.getError()).isEqualTo(QPayApiException.UNAVAILABLE));
    }
}
