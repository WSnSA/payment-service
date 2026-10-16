package com.wsnsa.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wsnsa.payment.ebarimt.ReceiptJobs;
import com.wsnsa.payment.ebarimt.ReceiptRepository;
import com.wsnsa.payment.ebarimt.ReceiptStatus;
import com.wsnsa.payment.notify.WebhookDispatcher;
import com.wsnsa.payment.notify.WebhookEvent;
import com.wsnsa.payment.notify.WebhookEventRepository;
import com.wsnsa.payment.notify.WebhookSigner;
import com.wsnsa.payment.payment.InvoiceService;
import com.wsnsa.payment.support.StubServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentFlowIntegrationTest {

    private static final String ADMIN = "test-admin-key";

    static final java.util.concurrent.atomic.AtomicInteger QPAY_SEQ = new java.util.concurrent.atomic.AtomicInteger();

    static StubServer posApi;
    static StubServer merchantHook;
    static StubServer qpay;
    static StubServer toki;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired ReceiptJobs receiptJobs;
    @Autowired ReceiptRepository receipts;
    @Autowired WebhookEventRepository events;
    @Autowired WebhookDispatcher dispatcher;
    @Autowired InvoiceService invoiceService;
    @Autowired com.wsnsa.payment.payment.InvoiceRepository invoices;
    @Autowired com.wsnsa.payment.provider.impl.SandboxProvider sandbox;

    @BeforeAll
    static void startStubs() throws Exception {
        posApi = new StubServer().on("POST", "/rest/receipt", r -> StubServer.Response.json("""
                {"id":"000000000000000000000000000000001","posId":"P1","status":"SUCCESS","message":"",
                 "qrData":"QRDATA","lottery":"AB 12345678","date":"2026-10-01 12:00:00"}"""))
                .on("DELETE", "/rest/receipt", r -> new StubServer.Response(200, ""));
        merchantHook = new StubServer().on("POST", "/hook", r -> new StubServer.Response(200, "{}"));
        qpay = new StubServer()
                .on("POST", "/v2/auth/token", r -> StubServer.Response.json("{\"access_token\":\"tok\",\"expires_in\":3600}"))
                .on("POST", "/v2/invoice", r -> StubServer.Response.json("""
                        {"invoice_id":"QP-%d","qr_text":"qr","qr_image":"img","qPay_shortUrl":"https://s.qpay.mn/x",
                         "urls":[{"name":"Khan","description":"Khan bank","logo":"l","link":"khanbank://q"}]}""".formatted(QPAY_SEQ.incrementAndGet())))
                .on("POST", "/v2/payment/check", r -> StubServer.Response.json("""
                        {"count":1,"paid_amount":3000,"rows":[{"payment_id":"PAY-9","payment_status":"PAID","payment_amount":3000}]}"""));
        toki = new StubServer()
                .on("POST", "/deeplink", r -> StubServer.Response.json("{\"data\":{\"deeplink\":\"toki://pay/1\"}}"))
                .on("GET", "/status", r -> StubServer.Response.json("{\"data\":{\"status\":\"PENDING\"}}"));
    }

    @AfterAll
    static void stopStubs() {
        posApi.close();
        merchantHook.close();
        qpay.close();
        toki.close();
    }

    record Merchant(String id, String apiKey, String webhookSecret) {
    }

    private Merchant createMerchant(String mode) throws Exception {
        String code = "m" + UUID.randomUUID().toString().substring(0, 8);
        String body = """
                {"code":"%s","name":"Test merchant","webhookUrl":"%s/hook",
                 "ebarimt":{"mode":"%s","posApiUrl":"%s","merchantTin":"12345678901","posNo":"10000001",
                            "districtCode":"25","branchNo":"001","classificationCode":"3600100","defaultItemName":"Ус"}}
                """.formatted(code, merchantHook.url(), mode, posApi.url());
        JsonNode res = json(mvc.perform(post("/admin/v1/merchants").header("X-Admin-Key", ADMIN)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()));
        assertThat(res.path("apiKey").asText()).startsWith("pk_");
        return new Merchant(res.path("merchant").path("id").asText(), res.path("apiKey").asText(), res.path("webhookSecret").asText());
    }

    private ResultActions createInvoice(Merchant m, String json) throws Exception {
        return mvc.perform(post("/api/v1/invoices").header("X-API-Key", m.apiKey())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private JsonNode json(ResultActions r) throws Exception {
        return mapper.readTree(r.andReturn().getResponse().getContentAsString());
    }

    @Test
    void rejectsMissingOrWrongKeys() throws Exception {
        mvc.perform(get("/api/v1/providers")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/providers").header("X-API-Key", "pk_wrong")).andExpect(status().isUnauthorized());
        mvc.perform(get("/admin/v1/merchants").header("X-Admin-Key", "wrong")).andExpect(status().isUnauthorized());
        mvc.perform(get("/admin/v1/merchants").header("X-Admin-Key", ADMIN)).andExpect(status().isOk());
    }

    @Test
    void sandboxPaymentIssuesReceiptAndNotifiesMerchant() throws Exception {
        Merchant m = createMerchant("PER_PAYMENT");

        String req = """
                {"provider":"SANDBOX","externalRef":"order-1","amount":5500,"description":"Цэнэглэлт",
                 "metadata":{"accountId":"A-1"},"receipt":{"consumerNo":"12345678"}}""";
        JsonNode inv = json(createInvoice(m, req).andExpect(status().isOk()));
        String id = inv.path("id").asText();
        assertThat(inv.path("status").asText()).isEqualTo("PENDING");
        assertThat(inv.path("paymentUrl").asText()).contains("/sandbox/invoices/" + id + "/pay");

        // Idempotent: ижил externalRef → ижил нэхэмжлэх
        assertThat(json(createInvoice(m, req)).path("id").asText()).isEqualTo(id);
        // Өөр дүнтэй бол 409
        createInvoice(m, req.replace("5500", "6000")).andExpect(status().isConflict());

        // Webhook ирсэн ч provider дээр төлөгдөөгүй бол PENDING хэвээр
        mvc.perform(post("/webhooks/sandbox/" + id)).andExpect(jsonPath("$.status").value("PENDING"));

        mvc.perform(post("/sandbox/invoices/" + id + "/pay")).andExpect(jsonPath("$.status").value("PAID"));
        // Давхар webhook — дахин PAID event үүсэхгүй
        Thread.sleep(2100);
        mvc.perform(post("/webhooks/sandbox/" + id)).andExpect(jsonPath("$.status").value("PAID"));

        JsonNode paid = json(mvc.perform(get("/api/v1/invoices/" + id).header("X-API-Key", m.apiKey())));
        assertThat(paid.path("status").asText()).isEqualTo("PAID");
        assertThat(paid.path("paidAmount").decimalValue()).isEqualByComparingTo("5500");
        String receiptId = paid.path("receiptId").asText();
        assertThat(receiptId).isNotBlank();

        receiptJobs.issueDue();
        var receipt = receipts.findById(receiptId).orElseThrow();
        assertThat(receipt.getStatus()).isEqualTo(ReceiptStatus.ISSUED);
        assertThat(receipt.getDdtd()).isEqualTo("000000000000000000000000000000001");
        assertThat(receipt.getLottery()).isEqualTo("AB 12345678");
        JsonNode posReq = mapper.readTree(posApi.requests("POST", "/rest/receipt").get(posApi.requests("POST", "/rest/receipt").size() - 1).body());
        assertThat(posReq.path("totalAmount").decimalValue()).isEqualByComparingTo("5500");
        assertThat(posReq.path("totalVAT").decimalValue()).isEqualByComparingTo("500");
        assertThat(posReq.path("merchantTin").asText()).isEqualTo("12345678901");

        // Merchant other merchant-ийн нэхэмжлэхийг харах эрхгүй
        Merchant other = createMerchant("DISABLED");
        mvc.perform(get("/api/v1/invoices/" + id).header("X-API-Key", other.apiKey())).andExpect(status().isNotFound());

        List<WebhookEvent> mine = events.findAll().stream().filter(e -> e.getMerchantId().equals(m.id())).toList();
        assertThat(mine).extracting(WebhookEvent::getEventType).containsExactlyInAnyOrder("invoice.paid", "receipt.issued");

        int before = merchantHook.requests.size();
        dispatcher.dispatchDue();
        List<StubServer.Request> delivered = merchantHook.requests.subList(before, merchantHook.requests.size());
        assertThat(delivered).hasSizeGreaterThanOrEqualTo(2);
        for (StubServer.Request r : delivered) {
            assertThat(WebhookSigner.verify(m.webhookSecret(), r.header(WebhookSigner.HEADER), r.body(),
                    Instant.now().getEpochSecond(), 300)).isTrue();
        }
        StubServer.Request paidHook = delivered.stream().filter(r -> "invoice.paid".equals(r.header("X-Payment-Event"))).findFirst().orElseThrow();
        JsonNode payload = mapper.readTree(paidHook.body());
        assertThat(payload.path("data").path("externalRef").asText()).isEqualTo("order-1");
        assertThat(payload.path("data").path("metadata").path("accountId").asText()).isEqualTo("A-1");

        // Буцаалт
        mvc.perform(post("/api/v1/receipts/" + receiptId + "/return").header("X-API-Key", m.apiKey()))
                .andExpect(jsonPath("$.status").value("RETURNED"));
        assertThat(posApi.requests("DELETE", "/rest/receipt")).isNotEmpty();
    }

    @Test
    void concurrentConfirmationsSettleOnce() throws Exception {
        Merchant m = createMerchant("DISABLED");
        String id = json(createInvoice(m, "{\"provider\":\"SANDBOX\",\"externalRef\":\"c-1\",\"amount\":1000}")).path("id").asText();
        var invoice = invoices.findById(id).orElseThrow();
        // Provider талд төлөгдсөн, гэхдээ манай DB-д PENDING хэвээр — webhook, poll, check зэрэг ирнэ.
        sandbox.markPaid(invoice.getProviderInvoiceId(), invoice.getAmount());

        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Object>> tasks = java.util.Collections.nCopies(8, () -> invoiceService.refresh(id));
            for (var f : pool.invokeAll(tasks)) {
                f.get();
            }
        } finally {
            pool.shutdown();
        }
        assertThat(invoices.findById(id).orElseThrow().getStatus()).isEqualTo(com.wsnsa.payment.payment.InvoiceStatus.PAID);
        long paidEvents = events.findAll().stream()
                .filter(e -> e.getMerchantId().equals(m.id()) && e.getEventType().equals("invoice.paid"))
                .count();
        assertThat(paidEvents).isEqualTo(1);
    }

    @Test
    void qpayInvoiceAndWebhookVerifiedAgainstProvider() throws Exception {
        Merchant m = createMerchant("DISABLED");
        mvc.perform(put("/admin/v1/merchants/" + m.id() + "/providers/QPAY").header("X-Admin-Key", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabled":true,"config":{"baseUrl":"%s/v2","username":"U","password":"P","invoiceCode":"INV"}}
                                """.formatted(qpay.url())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.config.password").value("********"))
                .andExpect(jsonPath("$.config.username").value("U"));

        mvc.perform(get("/api/v1/providers").header("X-API-Key", m.apiKey()))
                .andExpect(jsonPath("$[0]").value("QPAY"));

        JsonNode inv = json(createInvoice(m, "{\"provider\":\"QPAY\",\"externalRef\":\"q-1\",\"amount\":3000}").andExpect(status().isOk()));
        String id = inv.path("id").asText();
        assertThat(inv.path("providerInvoiceId").asText()).isEqualTo("QP-1");
        assertThat(inv.path("deeplinks").get(0).path("link").asText()).isEqualTo("khanbank://q");

        JsonNode sent = mapper.readTree(qpay.requests("POST", "/v2/invoice").get(0).body());
        assertThat(sent.path("sender_invoice_no").asText()).isEqualTo(id);
        assertThat(sent.path("callback_url").asText()).isEqualTo("http://localhost:8090/webhooks/qpay/" + id);
        assertThat(qpay.requests("POST", "/v2/invoice").get(0).header("Authorization")).isEqualTo("Bearer tok");

        mvc.perform(get("/webhooks/qpay/" + id).param("qpay_payment_id", "x")).andExpect(jsonPath("$.status").value("PAID"));
        JsonNode paid = json(mvc.perform(get("/api/v1/invoices").param("externalRef", "q-1").header("X-API-Key", m.apiKey())));
        assertThat(paid.path("providerTxnId").asText()).isEqualTo("PAY-9");

        // Масктай утга илгээвэл хуучин нууц үг хадгалагдана
        mvc.perform(put("/admin/v1/merchants/" + m.id() + "/providers/QPAY").header("X-Admin-Key", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabled":true,"config":{"baseUrl":"%s/v2","username":"U","password":"********","invoiceCode":"INV2"}}
                                """.formatted(qpay.url())))
                .andExpect(jsonPath("$.config.password").value("********"));
        createInvoice(m, "{\"provider\":\"QPAY\",\"externalRef\":\"q-2\",\"amount\":3000}").andExpect(status().isOk());
    }

    @Test
    void forgedTokiWebhookDoesNotMarkPaid() throws Exception {
        Merchant m = createMerchant("DISABLED");
        mvc.perform(put("/admin/v1/merchants/" + m.id() + "/providers/TOKI").header("X-Admin-Key", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"config\":{\"baseUrl\":\"%s\",\"merchantId\":\"M\",\"token\":\"T\"}}".formatted(toki.url())))
                .andExpect(status().isOk());
        String id = json(createInvoice(m, "{\"provider\":\"TOKI\",\"externalRef\":\"t-1\",\"amount\":2000}")).path("id").asText();

        mvc.perform(post("/webhooks/toki/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"" + id + "\",\"status\":\"SUCCESS\"}"))
                .andExpect(jsonPath("$.status").value("PENDING"));
        // Буруу provider-ийн замаар орж ирвэл 404
        mvc.perform(post("/webhooks/qpay/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void disabledProviderAndValidation() throws Exception {
        Merchant m = createMerchant("DISABLED");
        for (String bad : new String[]{"<TOKI_BASE_URL>", "ftp://x", "localhost:8080"}) {
            mvc.perform(put("/admin/v1/merchants/" + m.id() + "/providers/TOKI").header("X-Admin-Key", ADMIN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"enabled\":true,\"config\":{\"baseUrl\":\"" + bad + "\",\"merchantId\":\"M\",\"token\":\"T\"}}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("invalid_config"));
        }
        createInvoice(m, "{\"provider\":\"HIPAY\",\"externalRef\":\"h-1\",\"amount\":1000}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("provider_not_enabled"));
        createInvoice(m, "{\"provider\":\"SANDBOX\",\"externalRef\":\"v-1\",\"amount\":10.5}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.amount").exists());
        createInvoice(m, "{\"provider\":\"SANDBOX\",\"externalRef\":\"v-2\",\"amount\":100,\"receipt\":{\"items\":[]}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ebarimt_per_payment_disabled"));
    }

    @Test
    void standaloneReceipt() throws Exception {
        Merchant m = createMerchant("MANUAL");
        String body = """
                {"externalRef":"2026-09","amount":11000,
                 "receipt":{"type":"B2B_RECEIPT","customerTin":"12345678901",
                            "items":[{"name":"Ус","quantity":1000,"unitPrice":11,"measureUnit":"литр"}]}}""";
        JsonNode r = json(mvc.perform(post("/api/v1/receipts").header("X-API-Key", m.apiKey())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()));
        assertThat(r.path("status").asText()).isEqualTo("ISSUED");
        assertThat(r.path("totalVat").decimalValue()).isEqualByComparingTo("1000");
        // Idempotent
        assertThat(json(mvc.perform(post("/api/v1/receipts").header("X-API-Key", m.apiKey())
                .contentType(MediaType.APPLICATION_JSON).content(body))).path("id").asText()).isEqualTo(r.path("id").asText());
    }
}
