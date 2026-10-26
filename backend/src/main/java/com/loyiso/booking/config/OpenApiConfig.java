package com.loyiso.booking.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI bookingOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Branch Booking API")
                .version("v1")
                .description("Browse branches, view available appointment slots and book a slot."));
    }
}