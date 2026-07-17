package com.xeom.grabbackend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI grabBackendOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Grab Backend API")
                        .description("API documentation for the Grab backend service.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Xeom Backend Team")
                                .email("support@xeom.com")
                                .url("https://xeom.com")));
    }
}
