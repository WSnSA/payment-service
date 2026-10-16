package com.wsnsa.payment.payment;

import com.fasterxml.jackson.core.type.TypeReference;
import com.wsnsa.payment.common.ApiException;
import com.wsnsa.payment.common.JsonSupport;
import com.wsnsa.payment.config.AppProperties;
import com.wsnsa.payment.merchant.MerchantProviderConfig;
import com.wsnsa.payment.merchant.MerchantProviderConfigRepository;
import com.wsnsa.payment.provider.ProviderConfig;
import com.wsnsa.payment.provider.ProviderContext;
import com.wsnsa.payment.provider.ProviderType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ProviderContextFactory {

    private final MerchantProviderConfigRepository configs;
    private final JsonSupport json;
    private final AppProperties props;

    public ProviderContext forInvoice(Invoice invoice) {
        return build(invoice.getMerchantId(), invoice.getProvider(), invoice.getId(), invoice.getReturnUrl() != null);
    }

    ProviderContext build(String merchantId, ProviderType provider, String invoiceId, boolean hasReturnUrl) {
        Map<String, String> values = provider == ProviderType.SANDBOX ? Map.of() : loadConfig(merchantId, provider);
        String callback = callbackUrl(provider, invoiceId);
        return new ProviderContext(merchantId, new ProviderConfig(provider, values), callback,
                hasReturnUrl ? callback + "?redirect=1" : null);
    }

    public String callbackUrl(ProviderType provider, String invoiceId) {
        return props.publicBaseUrl() + "/webhooks/" + provider.name().toLowerCase(Locale.ROOT) + "/" + invoiceId;
    }

    private Map<String, String> loadConfig(String merchantId, ProviderType provider) {
        MerchantProviderConfig c = configs.findByMerchantIdAndProvider(merchantId, provider)
                .filter(MerchantProviderConfig::isEnabled)
                .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "provider_not_enabled",
                        "Provider " + provider + " is not enabled for this merchant"));
        Map<String, String> values = json.read(c.getConfig(), new TypeReference<Map<String, String>>() {
        });
        return values == null ? Map.of() : values;
    }
}
