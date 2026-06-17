package com.personalrouter.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI personalRouterOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Personal Router API")
                        .version("1.0")
                        .description("API REST para planejamento de rotas pessoais"));
    }
}
