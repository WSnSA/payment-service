package com.wsnsa.payment.merchant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MerchantRepository extends JpaRepository<Merchant, String> {

    Optional<Merchant> findByApiKeyHash(String apiKeyHash);

    boolean existsByCode(String code);
}
