package com.loyiso.booking.notification;

import com.loyiso.booking.booking.BookingCancelledEvent;
import com.loyiso.booking.booking.BookingCreatedEvent;
import com.loyiso.booking.booking.BookingRescheduledEvent;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Turns a booking event into the email the customer receives. */
@Component
public class EmailComposer {

    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);

    private final String baseUrl;

    public EmailComposer(@Value("${booking.public-base-url:http://localhost:8081}") String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public EmailContent confirmation(BookingCreatedEvent event) {
        StringBuilder text = new StringBuilder("""
                Hi %s,

                Your appointment is confirmed.

                Branch:     %s
                Address:    %s
                Date:       %s
                Time:       %s to %s
                Reference:  %s
                """.formatted(
                event.customerName(),
                event.branchName(),
                event.branchAddress(),
                event.date().format(LONG_DATE),
                event.startTime().format(TIME),
                event.endTime().format(TIME),
                event.reference()));

        if (event.reason() != null) {
            text.append("Visit:      ").append(event.reason().label()).append('\n');
            text.append("\nWhat to bring: ").append(event.reason().bring()).append('\n');
        }
        text.append("\nPlease arrive a few minutes early and quote your reference at reception.\n");
        if (event.manageToken() != null) {
            text.append("\nNeed to change or cancel? Use this private link and please do not share it:\n")
                    .append(baseUrl).append("/#manage=").append(event.reference()).append('.')
                    .append(event.manageToken()).append('\n');
        }

        return new EmailContent(event.customerEmail(),
                "Appointment confirmed: %s, %s, %s".formatted(
                        event.branchName(), event.date().format(SHORT_DATE), event.startTime().format(TIME)),
                text.toString());
    }

    public EmailContent cancellation(BookingCancelledEvent event) {
        return new EmailContent(event.customerEmail(),
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
    }

    public EmailContent rescheduled(BookingRescheduledEvent event) {
        return new EmailContent(event.customerEmail(),
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
    }
}