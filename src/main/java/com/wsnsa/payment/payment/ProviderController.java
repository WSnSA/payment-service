package com.wsnsa.payment.payment;

import com.wsnsa.payment.merchant.MerchantPrincipal;
import com.wsnsa.payment.merchant.MerchantProviderConfig;
import com.wsnsa.payment.merchant.MerchantProviderConfigRepository;
import com.wsnsa.payment.provider.ProviderRegistry;
import com.wsnsa.payment.provider.ProviderType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/v1/providers")
@RequiredArgsConstructor
@Tag(name = "Invoices")
@SecurityRequirement(name = "apiKey")
public class ProviderController {

    private final MerchantProviderConfigRepository configs;
    private final ProviderRegistry registry;

    @Operation(summary = "Энэ merchant-д идэвхтэй төлбөрийн хэрэгслүүд")
    @GetMapping
    public List<ProviderType> enabled(@AuthenticationPrincipal MerchantPrincipal merchant) {
        Stream<ProviderType> configured = configs.findAllByMerchantIdAndEnabledTrue(merchant.merchantId()).stream()
                .map(MerchantProviderConfig::getProvider);
        Stream<ProviderType> sandbox = registry.available().contains(ProviderType.SANDBOX) ? Stream.of(ProviderType.SANDBOX) : Stream.empty();
        return Stream.concat(configured, sandbox).filter(registry.available()::contains).distinct().sorted().toList();
    }
}
