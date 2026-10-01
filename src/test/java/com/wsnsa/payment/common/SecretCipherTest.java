package com.wsnsa.payment.common;

import com.wsnsa.payment.config.AppProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretCipherTest {

    private static AppProperties props(String key) {
        return new AppProperties("http://x", "admin", key, null, false, false, null);
    }

    @Test
    void roundTripWithRandomIv() {
        SecretCipher c = new SecretCipher(props("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="));
        String a = c.encrypt("нууц");
        String b = c.encrypt("нууц");
        assertThat(a).isNotEqualTo(b);
        assertThat(c.decrypt(a)).isEqualTo("нууц");
        assertThat(c.encrypt(null)).isNull();
    }

    @Test
    void rejectsWrongKeyLength() {
        assertThatThrownBy(() -> new SecretCipher(props("c2hvcnQ=")))
                .isInstanceOf(IllegalStateException.class);
    }
}
