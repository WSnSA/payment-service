package com.wsnsa.payment.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Payment Service")
                        .description("Төлбөрийн нэхэмжлэх (QPay, HiPay, Golomt/SocialPay, Toki, MonPay, DigiPay) болон И-баримт (PosAPI 3.0)")
                        .version("v1"))
                .components(new Components()
                        .addSecuritySchemes("apiKey", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name("X-API-Key"))
                        .addSecuritySchemes("adminKey", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name("X-Admin-Key")));
    }
}
