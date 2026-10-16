package com.wsnsa.payment.ebarimt;

import com.fasterxml.jackson.databind.JsonNode;
import com.wsnsa.payment.config.EbarimtProperties;
import com.wsnsa.payment.provider.HttpSupport;
import com.wsnsa.payment.provider.ProviderException;
import com.wsnsa.payment.provider.TokenCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.time.Duration;
import java.util.Map;

/**
 * ebarimt.mn-ийн нийтийн мэдээллийн API болон Easy Register.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EbarimtInfoClient {

    private static final String NAME = "ebarimt";

    private final HttpSupport http;
    private final EbarimtProperties props;
    private final TokenCache tokens;

    /** Регистрийн дугаараар ТТД авах. */
    public JsonNode tinInfo(String regNo) {
        return http.exchange(NAME, c -> c.get()
                .uri(base() + "/api/info/check/getTinInfo?regNo={r}", regNo));
    }

    /** ТТД-аар татвар төлөгчийн мэдээлэл (нэр, НӨАТ төлөгч эсэх г.м). */
    public JsonNode taxpayerInfo(String tin) {
        return http.exchange(NAME, c -> c.get()
                .uri(base() + "/api/info/check/getInfo?tin={t}", tin));
    }

    /** Аймаг, дүүргийн кодын жагсаалт. */
    public JsonNode branches() {
        return http.exchange(NAME, c -> c.get().uri(base() + "/api/info/check/getBranchInfo"));
    }

    /**
     * Иргэний ebarimt бүртгэлийн дугаар (consumerNo) хүчинтэй эсэх. Easy Register тохируулаагүй бол
     * шалгахгүйгээр true буцаана.
     */
    public boolean isValidConsumer(String consumerNo) {
        EbarimtProperties.EasyRegister er = props.easyRegister();
        if (!er.configured()) return true;
        try {
            String token = tokens.get("EASY_REGISTER", this::easyAuth);
            JsonNode res = http.exchange(NAME, c -> c.post()
                    .uri(er.apiUrl().replaceAll("/+$", "") + "/rest/v1/getProfile")
                    .headers(h -> h.setBearerAuth(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("customerNo", consumerNo)));
            return res.path("status").asInt() == 200;
        } catch (ProviderException e) {
            log.warn("Easy Register check failed for consumer: {}", e.getMessage());
            return false;
        }
    }

    private TokenCache.Token easyAuth() {
        EbarimtProperties.EasyRegister er = props.easyRegister();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", er.clientId());
        form.add("grant_type", "password");
        form.add("username", er.username());
        form.add("password", er.password());
        JsonNode res = http.exchange(NAME, c -> c.post()
                .uri(er.authUrl())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form));
        long expiresIn = res.path("expires_in").asLong(0);
        return new TokenCache.Token(HttpSupport.requireText(NAME, res, "access_token"),
                expiresIn > 0 ? Duration.ofSeconds(expiresIn) : null);
    }

    private String base() {
        return props.infoApiUrl().replaceAll("/+$", "");
    }
}
