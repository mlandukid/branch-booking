package com.loyiso.booking.notification;

import com.loyiso.booking.booking.BookingCancelledEvent;
import com.loyiso.booking.booking.BookingCreatedEvent;
import com.loyiso.booking.booking.BookingRescheduledEvent;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Sends the messages over SMTP. Locally the mail server is MailHog. */
@Component
@ConditionalOnProperty(name = "booking.mail.enabled", havingValue = "true", matchIfMissing = true)
public class MailBookingNotifier implements BookingNotifier {

    private static final Logger log = LoggerFactory.getLogger(MailBookingNotifier.class);
    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);

    private final JavaMailSender mailSender;
    private final String from;
    private final String baseUrl;

    @Autowired
    public MailBookingNotifier(JavaMailSender mailSender,
                               @Value("${booking.mail.from:no-reply@branchbooking.local}") String from,
                               @Value("${booking.public-base-url:http://localhost:8081}") String baseUrl) {
        this.mailSender = mailSender;
        this.from = from;
        this.baseUrl = baseUrl;
    }

    public MailBookingNotifier(JavaMailSender mailSender, String from) {
        this(mailSender, from, "http://localhost:8081");
    }

    @Override
    public void bookingConfirmed(BookingCreatedEvent event) {
        String manage = event.manageToken() == null ? "" : """

                Need to change or cancel? Use this private link and please do not share it:
                %s
                """.formatted(baseUrl + "/#manage=" + event.reference() + "." + event.manageToken());

        send(event.customerEmail(),
                "Appointment confirmed: %s, %s, %s".formatted(
                        event.branchName(), event.date().format(SHORT_DATE), event.startTime().format(TIME)),
                """
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
                        event.reference()) + manage);
        log.info("Confirmation email sent for booking {}", event.reference());
    }

    @Override
    public void bookingCancelled(BookingCancelledEvent event) {
        send(event.customerEmail(),
                "Appointment cancelled: %s, %s, %s".formatted(
                        event.branchName(), event.date().format(SHORT_DATE), event.startTime().format(TIME)),
                """
                Hi %s,

                Your appointment has been cancelled.

                Branch:     %s
                Date:       %s
                Time:       %s
                Reference:  %s

                You can book a new appointment at any time: %s
                """.formatted(
                        event.customerName(),
                        event.branchName(),
                        event.date().format(LONG_DATE),
                        event.startTime().format(TIME),
                        event.reference(),
                        baseUrl));
        log.info("Cancellation email sent for booking {}", event.reference());
    }

    @Override
    public void bookingRescheduled(BookingRescheduledEvent event) {
        send(event.customerEmail(),
                "Appointment moved: %s, %s, %s".formatted(
                        event.branchName(), event.newDate().format(SHORT_DATE), event.newStart().format(TIME)),
                """
                Hi %s,

                Your appointment has been moved.

                Branch:     %s
                Address:    %s
                Was:        %s at %s
                Now:        %s, %s to %s
                Reference:  %s

                Please arrive a few minutes early and quote your reference at reception.
                """.formatted(
                        event.customerName(),
                        event.branchName(),
                        event.branchAddress(),
                        event.oldDate().format(LONG_DATE),
                        event.oldStart().format(TIME),
                        event.newDate().format(LONG_DATE),
                        event.newStart().format(TIME),
                        event.newEnd().format(TIME),
                        event.reference()));
        log.info("Reschedule email sent for booking {}", event.reference());
    }

    private void send(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
    }
}