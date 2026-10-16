package com.wsnsa.payment.merchant;

import com.wsnsa.payment.provider.ProviderType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/v1/merchants")
@RequiredArgsConstructor
@Tag(name = "Admin")
@SecurityRequirement(name = "adminKey")
public class AdminController {

    private final MerchantService service;

    @Operation(summary = "Merchant (клиент систем) бүртгэх", description = "apiKey, webhookSecret зөвхөн энэ хариунд нэг удаа харагдана.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MerchantDtos.MerchantCredentials create(@Valid @RequestBody MerchantDtos.CreateMerchantRequest request) {
        return service.create(request);
    }

    @GetMapping
    public List<MerchantDtos.MerchantResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public MerchantDtos.MerchantResponse get(@PathVariable String id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    public MerchantDtos.MerchantResponse update(@PathVariable String id, @Valid @RequestBody MerchantDtos.UpdateMerchantRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/rotate-api-key")
    public MerchantDtos.MerchantCredentials rotateApiKey(@PathVariable String id) {
        return service.rotateApiKey(id);
    }

    @PostMapping("/{id}/rotate-webhook-secret")
    public MerchantDtos.MerchantCredentials rotateWebhookSecret(@PathVariable String id) {
        return service.rotateWebhookSecret(id);
    }

    @GetMapping("/{id}/providers")
    public List<MerchantDtos.ProviderConfigResponse> providers(@PathVariable String id) {
        return service.listProviders(id);
    }

    @Operation(summary = "Provider-ийн гэрээний тохиргоо", description = "Утга нь '********' бол өмнөх утгаа хадгална.")
    @PutMapping("/{id}/providers/{provider}")
    public MerchantDtos.ProviderConfigResponse upsertProvider(@PathVariable String id, @PathVariable ProviderType provider,
                                                              @Valid @RequestBody MerchantDtos.ProviderConfigRequest request) {
        return service.upsertProvider(id, provider, request);
    }
}
