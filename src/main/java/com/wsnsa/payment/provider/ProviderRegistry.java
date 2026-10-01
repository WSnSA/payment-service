package com.wsnsa.payment.provider;

import com.wsnsa.payment.common.ApiException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ProviderRegistry {

    private final Map<ProviderType, PaymentProvider> providers = new EnumMap<>(ProviderType.class);

    public ProviderRegistry(List<PaymentProvider> list) {
        for (PaymentProvider p : list) {
            providers.put(p.type(), p);
        }
    }

    public PaymentProvider get(ProviderType type) {
        PaymentProvider p = providers.get(type);
        if (p == null) {
            throw ApiException.badRequest("provider_unavailable", "Provider " + type + " is not available");
        }
        return p;
    }

    public Set<ProviderType> available() {
        return providers.keySet();
    }
}
