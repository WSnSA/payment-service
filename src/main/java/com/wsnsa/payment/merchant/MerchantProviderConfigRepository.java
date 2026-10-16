package com.wsnsa.payment.merchant;

import com.wsnsa.payment.provider.ProviderType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MerchantProviderConfigRepository extends JpaRepository<MerchantProviderConfig, String> {

    Optional<MerchantProviderConfig> findByMerchantIdAndProvider(String merchantId, ProviderType provider);

    List<MerchantProviderConfig> findAllByMerchantId(String merchantId);

    List<MerchantProviderConfig> findAllByMerchantIdAndEnabledTrue(String merchantId);
}
