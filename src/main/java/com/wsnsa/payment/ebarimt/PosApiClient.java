package com.wsnsa.payment.ebarimt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wsnsa.payment.common.ApiException;
import com.wsnsa.payment.merchant.EbarimtSettings;
import com.wsnsa.payment.provider.HttpSupport;
import com.wsnsa.payment.provider.ProviderException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * Merchant-ийн PosAPI 3.0 руу хандана.
 */
@Component
@RequiredArgsConstructor
public class PosApiClient {

    private static final String NAME = "PosAPI";

    private final HttpSupport http;
    private final ObjectMapper mapper;

    public PosApiModels.CreateReceiptResponse createReceipt(EbarimtSettings s, PosApiModels.CreateReceipt request) {
        JsonNode res = http.exchange(NAME, c -> c.post()
                .uri(base(s) + "/rest/receipt")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request));
        try {
            return mapper.treeToValue(res, PosApiModels.CreateReceiptResponse.class);
        } catch (JsonProcessingException e) {
            throw new ProviderException(NAME + " returned unexpected response");
        }
    }

    public void deleteReceipt(EbarimtSettings s, PosApiModels.DeleteReceipt request) {
        http.exchange(NAME, c -> c.method(HttpMethod.DELETE)
                .uri(base(s) + "/rest/receipt")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request));
    }

    public void sendData(EbarimtSettings s) {
        http.exchange(NAME, c -> c.get().uri(base(s) + "/rest/sendData"));
    }

    public JsonNode info(EbarimtSettings s) {
        return http.exchange(NAME, c -> c.get().uri(base(s) + "/rest/info"));
    }

    private static String base(EbarimtSettings s) {
        if (s.getPosApiUrl() == null || s.getPosApiUrl().isBlank()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "ebarimt_not_configured", "PosAPI URL is not configured for this merchant");
        }
        return s.getPosApiUrl().replaceAll("/+$", "");
    }
}
