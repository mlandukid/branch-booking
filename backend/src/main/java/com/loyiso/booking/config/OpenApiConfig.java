package com.loyiso.booking.config;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String REPO = "https://github.com/mlandukid/branch-booking";

    @Bean
    OpenAPI bookingOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Branch Booking API")
                        .version("v1")
                        .description("""
                                Book branch appointments without ever double-booking a slot.

                                **How it works**
                                - Slots are computed from each branch's opening hours and slot length.
                                - A slot holds exactly one booking. A unique database constraint, not application code, \
                                guarantees this, so concurrent requests for the same slot produce exactly one `201` \
                                and the rest `409`.
                                - The confirmation email is sent only after the booking has committed.

                                **Errors** use RFC 7807 problem responses. Every response carries an `X-Request-Id` \
                                header, repeated in error bodies as `requestId`, so a failure can be traced in the logs.
                                """)
                        .contact(new Contact().name("Loyiso Mlandu").url(REPO)))
                .externalDocs(new ExternalDocumentation()
                        .description("Source code, design decisions and README")
                        .url(REPO));
    }
}