package com.loyiso.booking.notification;

import com.loyiso.booking.booking.BookingCreatedEvent;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Sends the confirmation over SMTP. Locally the mail server is MailHog. */
@Component
@ConditionalOnProperty(name = "booking.mail.enabled", havingValue = "true", matchIfMissing = true)
public class MailBookingNotifier implements BookingNotifier {

    private static final Logger log = LoggerFactory.getLogger(MailBookingNotifier.class);
    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);

    private final JavaMailSender mailSender;
    private final String from;

    public MailBookingNotifier(JavaMailSender mailSender,
                               @Value("${booking.mail.from:no-reply@branchbooking.local}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void bookingConfirmed(BookingCreatedEvent event) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(event.customerEmail());
        message.setSubject("Appointment confirmed: %s, %s, %s".formatted(
                event.branchName(), event.date().format(SHORT_DATE), event.startTime().format(TIME)));
        message.setText("""
                Hi %s,

                Your appointment is confirmed.

                Branch:     %s
                Address:    %s
                Date:       %s
                Time:       %s to %s
                Reference:  %s

                Please arrive a few minutes early and quote your reference at reception.
                """.formatted(
                event.customerName(),
                event.branchName(),
                event.branchAddress(),
                event.date().format(LONG_DATE),
                event.startTime().format(TIME),
                event.endTime().format(TIME),
                event.reference()));

        mailSender.send(message);
        log.info("Confirmation email sent for booking {}", event.reference());
    }
}