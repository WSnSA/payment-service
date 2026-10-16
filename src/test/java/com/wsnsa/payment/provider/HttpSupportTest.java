package com.wsnsa.payment.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpSupportTest {

    @Test
    void invalidConfiguredUrlBecomesProviderError() {
        HttpSupport http = new HttpSupport(RestClient.create(), new ObjectMapper());
        assertThatThrownBy(() -> http.exchange("Toki", c -> c.post().uri("<TOKI_BASE_URL>/deeplink")))
                .isInstanceOf(ProviderException.class)
                .hasMessageContaining("invalid URL");
    }
}
