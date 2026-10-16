package com.wsnsa.payment.provider.impl;

import com.wsnsa.payment.provider.HttpSupport;
import com.wsnsa.payment.provider.ProviderType;
import org.springframework.stereotype.Component;

@Component
public class GolomtCardProvider extends GolomtEcommerceProvider {

    public GolomtCardProvider(HttpSupport http) {
        super(http, false);
    }

    @Override
    public ProviderType type() {
        return ProviderType.GOLOMT_CARD;
    }
}
