package com.loyiso.booking.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Simulated delivery for when real email is switched off (booking.mail.enabled=false). */
@Component
@ConditionalOnProperty(name = "booking.mail.enabled", havingValue = "false")
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(String to, String subject, String body) {
        log.info("Simulated email: {}", subject);
    }
}