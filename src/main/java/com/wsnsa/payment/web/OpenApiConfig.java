package com.wsnsa.payment.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger UI: /swagger-ui.html, OpenAPI JSON: /v3/api-docs */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    OpenAPI paymentServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Payment Service API")
                .version("v1")
                .description("QPay-ээр төлбөр хүлээн авах, и-баримт үүсгэх сервис."));
    }
}
