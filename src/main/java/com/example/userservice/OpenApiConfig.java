package com.example.userservice;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The title, description and links shown at the top of the Swagger UI page. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI userServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("User Service API")
                        .version("1.0")
                        .description("""
                                A Spring Boot microservice running on Kubernetes, with PostgreSQL \
                                for storage and Redis for caching.

                                **This is a live demo.** Feel free to create, edit and delete users \
                                with the "Try it out" buttons. All data resets every night at \
                                03:00 UK time. Please don't enter real personal details.""")
                        .contact(new Contact()
                                .name("Ben Findlay")
                                .url("https://ben-findlay-portfolio.netlify.app")))
                .externalDocs(new ExternalDocumentation()
                        .description("Source code on GitHub")
                        .url("https://github.com/Yuritiu/Java-Microservice"));
    }
}
