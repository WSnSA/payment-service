package com.wsnsa.payment.merchant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.wsnsa.payment.provider.ProviderType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Map;

public final class MerchantDtos {

    private MerchantDtos() {
    }

    public record CreateMerchantRequest(
            @NotBlank @Size(max = 50) @Pattern(regexp = "[A-Za-z0-9_-]+") String code,
            @NotBlank @Size(max = 255) String name,
            @Size(max = 500) @Pattern(regexp = "^https?://.*", message = "must be an http(s) URL") String webhookUrl,
            @Valid EbarimtSettingsDto ebarimt
    ) {
    }

    public record UpdateMerchantRequest(
            @NotBlank @Size(max = 255) String name,
            @Size(max = 500) @Pattern(regexp = "^https?://.*", message = "must be an http(s) URL") String webhookUrl,
            Boolean active,
            @Valid EbarimtSettingsDto ebarimt
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MerchantResponse(
            String id,
            String code,
            String name,
            String webhookUrl,
            boolean active,
            EbarimtSettingsDto ebarimt,
            Instant createdAt
    ) {
        public static MerchantResponse of(Merchant m) {
            return new MerchantResponse(m.getId(), m.getCode(), m.getName(), m.getWebhookUrl(), m.isActive(),
                    EbarimtSettingsDto.of(m.getEbarimt()), m.getCreatedAt());
        }
    }

    /** Зөвхөн үүсгэх/шинэчлэх үед нэг удаа харагдана. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MerchantCredentials(MerchantResponse merchant, String apiKey, String webhookSecret) {
    }

    /**
     * @param config Provider-ийн тохиргоо. {@value MerchantService#MASK} утгатай түлхүүр өмнөх утгаа хадгална.
     */
    public record ProviderConfigRequest(@NotNull Boolean enabled, @NotNull Map<String, String> config) {
    }

    /** Нууц утгууд нуугдсан. */
    public record ProviderConfigResponse(ProviderType provider, boolean enabled, Map<String, String> config, Instant updatedAt) {
    }
}
