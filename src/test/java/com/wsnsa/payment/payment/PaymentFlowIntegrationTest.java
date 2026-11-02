package com.wsnsa.payment.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentFlowIntegrationTest {

    static final MockWebServer qpayServer = new MockWebServer();
    static final FakeQPay fakeQPay = new FakeQPay();

    static {
        qpayServer.setDispatcher(fakeQPay);
        try {
            qpayServer.start();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void qpay(DynamicPropertyRegistry registry) {
        registry.add("qpay.base-url", () -> qpayServer.url("/").toString().replaceAll("/$", ""));
    }

    @AfterAll
    static void shutdown() throws IOException {
        qpayServer.shutdown();
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @BeforeEach
    void reset() {
        fakeQPay.checkResponse = "{\"count\":0,\"paid_amount\":0,\"rows\":[]}";
        fakeQPay.invoiceFailure = null;
        fakeQPay.cancelResponse = null;
    }

    @Test
    void fullPaymentFlow_createCallbackPaidEbarimt() throws Exception {
        JsonNode created = createPayment("""
                {"orderId":"ORDER-1","amount":1500,"description":"Order 1"}
                """);
        String id = created.get("id").asText();
        assertThat(created.get("status").asText()).isEqualTo("PENDING");
        assertThat(created.at("/qpay/qrText").asText()).startsWith("qr-");
        assertThat(created.at("/qpay/shortUrl").asText()).startsWith("https://s.qpay.mn/");
        assertThat(created.at("/qpay/deeplinks/0/link").asText()).startsWith("khanbank://");

        JsonNode invoiceBody = mapper.readTree(fakeQPay.last("/v2/invoice").getBody().readUtf8());
        assertThat(invoiceBody.get("invoice_code").asText()).isEqualTo("TEST_INVOICE");
        assertThat(invoiceBody.get("sender_invoice_no").asText()).isEqualTo(id);
        assertThat(invoiceBody.get("invoice_receiver_code").asText()).isEqualTo("terminal");
        assertThat(invoiceBody.get("amount").decimalValue()).isEqualByComparingTo("1500");
        assertThat(invoiceBody.get("callback_url").asText())
                .isEqualTo("https://pay.example.mn/api/v1/qpay/callback?payment_id=" + id);

        // Төлөгдөөгүй байхад callback ирвэл PENDING хэвээр
        mvc.perform(get("/api/v1/qpay/callback").param("payment_id", id))
                .andExpect(status().isOk())
                .andExpect(content().string("SUCCESS"));
        mvc.perform(get("/api/v1/payments/{id}", id)).andExpect(jsonPath("$.status").value("PENDING"));

        // Төлөгдсөний дараа callback -> payment/check -> PAID
        fakeQPay.checkResponse = FakeQPay.paid("qpay-payment-1", "1500", "P2P");
        mvc.perform(get("/api/v1/qpay/callback").param("payment_id", id))
                .andExpect(status().isOk())
                .andExpect(content().string("SUCCESS"));

        JsonNode checkBody = mapper.readTree(fakeQPay.last("/v2/payment/check").getBody().readUtf8());
        assertThat(checkBody.get("object_type").asText()).isEqualTo("INVOICE");
        assertThat(checkBody.get("object_id").asText()).isEqualTo(created.at("/qpay/invoiceId").asText());

        mvc.perform(get("/api/v1/payments/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paidAmount").value(1500))
                .andExpect(jsonPath("$.paymentType").value("P2P"))
                .andExpect(jsonPath("$.qpay.paymentId").value("qpay-payment-1"));

        // Давхар callback ирсэн ч дахин check хийхгүй
        int before = fakeQPay.requests.size();
        mvc.perform(get("/api/v1/qpay/callback").param("payment_id", id)).andExpect(status().isOk());
        assertThat(fakeQPay.requests.size()).isEqualTo(before);

        // И-баримт
        mvc.perform(post("/api/v1/payments/{id}/ebarimt", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiverType\":\"CITIZEN\",\"receiver\":\"88614450\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ebarimt.id").value("ebarimt-1"))
                .andExpect(jsonPath("$.ebarimt.lottery").value("QQ 69281365"));
        JsonNode ebarimtBody = mapper.readTree(fakeQPay.last("/v2/ebarimt/create").getBody().readUtf8());
        assertThat(ebarimtBody.get("payment_id").asText()).isEqualTo("qpay-payment-1");
        assertThat(ebarimtBody.get("ebarimt_receiver_type").asText()).isEqualTo("CITIZEN");

        mvc.perform(get("/api/v1/payments").param("orderId", "ORDER-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id));

        // Token зөвхөн нэг удаа авсан байх ёстой (QPay-ийн шаардлага)
        assertThat(fakeQPay.tokenRequests.get()).isEqualTo(1);
    }

    @Test
    void manualCheckAndRefund() throws Exception {
        String id = createPayment("{\"amount\":2000,\"description\":\"Card order\"}").get("id").asText();
        fakeQPay.checkResponse = FakeQPay.paid("card-payment-1", "2000", "CARD");

        mvc.perform(post("/api/v1/payments/{id}/check", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        mvc.perform(post("/api/v1/payments/{id}/refund", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"butsaalt\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));
        assertThat(fakeQPay.last("/v2/payment/refund/").getPath()).isEqualTo("/v2/payment/refund/card-payment-1");
    }

    @Test
    void partialPaymentStaysPending() throws Exception {
        String id = createPayment("{\"amount\":3000,\"description\":\"Partial\"}").get("id").asText();
        fakeQPay.checkResponse = FakeQPay.paid("p-1", "1000", "P2P");

        mvc.perform(post("/api/v1/payments/{id}/check", id))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.paidAmount").value(1000));
    }

    @Test
    void cancelPendingInvoice() throws Exception {
        JsonNode created = createPayment("{\"amount\":500,\"description\":\"To cancel\"}");
        String id = created.get("id").asText();

        mvc.perform(post("/api/v1/payments/{id}/cancel", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(fakeQPay.last("/v2/invoice/").getMethod()).isEqualTo("DELETE");
        assertThat(fakeQPay.last("/v2/invoice/").getPath())
                .isEqualTo("/v2/invoice/" + created.at("/qpay/invoiceId").asText());

        // Цуцлагдсан төлбөрийг буцаах боломжгүй
        mvc.perform(post("/api/v1/payments/{id}/refund", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVALID_PAYMENT_STATE"));
    }

    @Test
    void cancelOfAlreadyPaidInvoiceSyncsStatus() throws Exception {
        String id = createPayment("{\"amount\":700,\"description\":\"Paid meanwhile\"}").get("id").asText();
        fakeQPay.cancelResponse = FakeQPay.json(400, "{\"error\":\"INVOICE_PAID\",\"message\":\"INVOICE_PAID\"}");
        fakeQPay.checkResponse = FakeQPay.paid("p-700", "700", "P2P");

        mvc.perform(post("/api/v1/payments/{id}/cancel", id)).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/payments/{id}", id)).andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void detailedInvoiceWithLinesAndReceiver() throws Exception {
        createPayment("""
                {"amount":200,"description":"Order No1311","receiverCode":"83",
                 "receiver":{"register":"UZ96021105","name":"Ganzul","email":"test@gmail.com","phone":"88614450"},
                 "lines":[{"taxProductCode":"6401","description":"Item","quantity":1,"unitPrice":200}]}
                """);

        JsonNode body = mapper.readTree(fakeQPay.last("/v2/invoice").getBody().readUtf8());
        assertThat(body.get("invoice_receiver_code").asText()).isEqualTo("83");
        assertThat(body.at("/invoice_receiver_data/register").asText()).isEqualTo("UZ96021105");
        assertThat(body.at("/lines/0/tax_product_code").asText()).isEqualTo("6401");
        assertThat(body.at("/lines/0/line_description").asText()).isEqualTo("Item");
        assertThat(body.at("/lines/0/line_unit_price").decimalValue()).isEqualByComparingTo("200");
        assertThat(body.get("allow_partial").asBoolean()).isFalse();
    }

    @Test
    void qpayErrorOnInvoiceCreationReturnsBadGateway() throws Exception {
        fakeQPay.invoiceFailure = FakeQPay.json(400,
                "{\"error\":\"INVOICE_CODE_INVALID\",\"message\":\"Invoice code is wrong!\"}");

        mvc.perform(post("/api/v1/payments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100,\"description\":\"x\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("INVOICE_CODE_INVALID"))
                .andExpect(jsonPath("$.qpayStatus").value(400));
    }

    @Test
    void validationErrors() throws Exception {
        mvc.perform(post("/api/v1/payments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
        mvc.perform(get("/api/v1/payments/{id}", "unknown")).andExpect(status().isNotFound());
    }

    @Test
    void callbackAlwaysAnswersSuccess() throws Exception {
        mvc.perform(get("/api/v1/qpay/callback").param("payment_id", "unknown"))
                .andExpect(status().isOk())
                .andExpect(content().string("SUCCESS"));
        mvc.perform(post("/api/v1/qpay/callback"))
                .andExpect(status().isOk())
                .andExpect(content().string("SUCCESS"));
    }

    private JsonNode createPayment(String json) throws Exception {
        String response = mvc.perform(post("/api/v1/payments").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(response);
    }
}
