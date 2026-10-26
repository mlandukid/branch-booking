package com.loyiso.booking.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    @Bean
    Clock clock(@Value("${booking.timezone:Africa/Johannesburg}") String timezone) {
        return Clock.system(ZoneId.of(timezone));
    }
}