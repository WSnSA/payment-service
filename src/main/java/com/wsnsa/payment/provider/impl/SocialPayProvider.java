package com.wsnsa.payment.provider.impl;

import com.wsnsa.payment.provider.HttpSupport;
import com.wsnsa.payment.provider.ProviderType;
import org.springframework.stereotype.Component;

@Component
public class SocialPayProvider extends GolomtEcommerceProvider {

    public SocialPayProvider(HttpSupport http) {
        super(http, true);
    }

    @Override
    public ProviderType type() {
        return ProviderType.SOCIALPAY;
    }
}
