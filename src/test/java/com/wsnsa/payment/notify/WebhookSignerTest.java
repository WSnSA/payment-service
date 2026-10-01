package com.wsnsa.payment.notify;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignerTest {

    @Test
    void signAndVerify() {
        String header = WebhookSigner.sign("secret", 1000, "{\"a\":1}");
        assertThat(WebhookSigner.verify("secret", header, "{\"a\":1}", 1010, 300)).isTrue();
        assertThat(WebhookSigner.verify("other", header, "{\"a\":1}", 1010, 300)).isFalse();
        assertThat(WebhookSigner.verify("secret", header, "{\"a\":2}", 1010, 300)).isFalse();
        assertThat(WebhookSigner.verify("secret", header, "{\"a\":1}", 2000, 300)).isFalse();
        assertThat(WebhookSigner.verify("secret", null, "{}", 1000, 300)).isFalse();
    }
}
