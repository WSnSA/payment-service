package com.wsnsa.payment.merchant;

import com.fasterxml.jackson.core.type.TypeReference;
import com.wsnsa.payment.common.ApiException;
import com.wsnsa.payment.common.Crypto;
import com.wsnsa.payment.common.JsonSupport;
import com.wsnsa.payment.provider.ProviderType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MerchantService {

    public static final String MASK = "********";

    /** Эдгээр түлхүүрүүдийг нууц биш гэж үзэж admin API-д ил харуулна. */
    private static final Set<String> PUBLIC_KEYS = Set.of(
            "baseUrl", "invoiceCode", "invoiceReceiverCode", "entityId", "merchantId", "merchantName",
            "terminalId", "receiver", "paymentPageUrl", "username", "clientId", "basicUsername");

    private final MerchantRepository merchants;
    private final MerchantProviderConfigRepository providerConfigs;
    private final JsonSupport json;

    public Optional<MerchantPrincipal> authenticate(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) return Optional.empty();
        return merchants.findByApiKeyHash(Crypto.sha256Hex(apiKey))
                .filter(Merchant::isActive)
                .map(m -> new MerchantPrincipal(m.getId(), m.getCode()));
    }

    @Transactional
    public MerchantDtos.MerchantCredentials create(MerchantDtos.CreateMerchantRequest req) {
        if (merchants.existsByCode(req.code())) {
            throw ApiException.conflict("duplicate_code", "Merchant code already exists");
        }
        String apiKey = newApiKey();
        String secret = newWebhookSecret();
        Merchant m = new Merchant();
        m.setCode(req.code());
        m.setName(req.name());
        m.setWebhookUrl(req.webhookUrl());
        m.setApiKeyHash(Crypto.sha256Hex(apiKey));
        m.setWebhookSecret(secret);
        if (req.ebarimt() != null) req.ebarimt().applyTo(m.getEbarimt());
        merchants.save(m);
        return new MerchantDtos.MerchantCredentials(MerchantDtos.MerchantResponse.of(m), apiKey, secret);
    }

    @Transactional(readOnly = true)
    public List<MerchantDtos.MerchantResponse> list() {
        return merchants.findAll().stream().map(MerchantDtos.MerchantResponse::of).toList();
    }

    @Transactional(readOnly = true)
    public MerchantDtos.MerchantResponse get(String id) {
        return MerchantDtos.MerchantResponse.of(find(id));
    }

    @Transactional
    public MerchantDtos.MerchantResponse update(String id, MerchantDtos.UpdateMerchantRequest req) {
        Merchant m = find(id);
        m.setName(req.name());
        m.setWebhookUrl(req.webhookUrl());
        if (req.active() != null) m.setActive(req.active());
        if (req.ebarimt() != null) req.ebarimt().applyTo(m.getEbarimt());
        return MerchantDtos.MerchantResponse.of(m);
    }

    @Transactional
    public MerchantDtos.MerchantCredentials rotateApiKey(String id) {
        Merchant m = find(id);
        String apiKey = newApiKey();
        m.setApiKeyHash(Crypto.sha256Hex(apiKey));
        return new MerchantDtos.MerchantCredentials(MerchantDtos.MerchantResponse.of(m), apiKey, null);
    }

    @Transactional
    public MerchantDtos.MerchantCredentials rotateWebhookSecret(String id) {
        Merchant m = find(id);
        String secret = newWebhookSecret();
        m.setWebhookSecret(secret);
        return new MerchantDtos.MerchantCredentials(MerchantDtos.MerchantResponse.of(m), null, secret);
    }

    @Transactional
    public MerchantDtos.ProviderConfigResponse upsertProvider(String merchantId, ProviderType provider, MerchantDtos.ProviderConfigRequest req) {
        find(merchantId);
        if (provider == ProviderType.SANDBOX) {
            throw ApiException.badRequest("invalid_provider", "SANDBOX needs no configuration");
        }
        MerchantProviderConfig c = providerConfigs.findByMerchantIdAndProvider(merchantId, provider).orElseGet(() -> {
            MerchantProviderConfig n = new MerchantProviderConfig();
            n.setMerchantId(merchantId);
            n.setProvider(provider);
            return n;
        });
        String baseUrl = req.config().get("baseUrl");
        if (baseUrl == null || !isHttpUrl(baseUrl)) {
            throw ApiException.badRequest("invalid_config", "config.baseUrl must be an absolute http(s) URL");
        }
        Map<String, String> old = c.getConfig() == null ? Map.of() : readConfig(c);
        Map<String, String> merged = new LinkedHashMap<>();
        req.config().forEach((k, v) -> {
            if (MASK.equals(v)) {
                if (old.containsKey(k)) merged.put(k, old.get(k));
            } else if (v != null) {
                merged.put(k, v);
            }
        });
        c.setEnabled(req.enabled());
        c.setConfig(json.write(merged));
        providerConfigs.saveAndFlush(c);
        return toResponse(c);
    }

    @Transactional(readOnly = true)
    public List<MerchantDtos.ProviderConfigResponse> listProviders(String merchantId) {
        find(merchantId);
        return providerConfigs.findAllByMerchantId(merchantId).stream().map(this::toResponse).toList();
    }

    private MerchantDtos.ProviderConfigResponse toResponse(MerchantProviderConfig c) {
        Map<String, String> masked = new LinkedHashMap<>();
        readConfig(c).forEach((k, v) -> masked.put(k, PUBLIC_KEYS.contains(k) ? v : MASK));
        return new MerchantDtos.ProviderConfigResponse(c.getProvider(), c.isEnabled(), masked, c.getUpdatedAt());
    }

    private Map<String, String> readConfig(MerchantProviderConfig c) {
        Map<String, String> m = json.read(c.getConfig(), new TypeReference<Map<String, String>>() {
        });
        return m == null ? Map.of() : m;
    }

    private static boolean isHttpUrl(String value) {
        try {
            URI uri = new URI(value);
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private Merchant find(String id) {
        return merchants.findById(id).orElseThrow(() -> ApiException.notFound("Merchant"));
    }

    private static String newApiKey() {
        return "pk_" + Crypto.randomToken(32);
    }

    private static String newWebhookSecret() {
        return "whsec_" + Crypto.randomToken(32);
    }
}
