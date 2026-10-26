package com.loyiso.booking.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.loyiso.booking.booking.BookingCreatedEvent;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class MailBookingNotifierTest {

    private JavaMailSender mailSender;
    private MailBookingNotifier notifier;
    private BookingCreatedEvent event;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        notifier = new MailBookingNotifier(mailSender, "no-reply@test.local");
        event = new BookingCreatedEvent("ref-1", "Sandton", "Sandton City, Johannesburg",
                LocalDate.of(2026, 11, 2), LocalTime.of(9, 0), LocalTime.of(9, 30),
                "Thandi Nkosi", "thandi@example.com");
    }

    @Test
    void sendsConfirmationToTheCustomer() {
        notifier.bookingConfirmed(event);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();

        assertThat(message.getTo()).containsExactly("thandi@example.com");
        assertThat(message.getFrom()).isEqualTo("no-reply@test.local");
        assertThat(message.getSubject()).contains("Sandton").contains("09:00");
        assertThat(message.getText())
                .contains("Thandi Nkosi")
                .contains("ref-1")
                .contains("Monday, 2 November 2026")
                .contains("09:00 to 09:30")
                .contains("Sandton City, Johannesburg");
    }

    @Test
    void propagatesMailFailureSoTheListenerCanLogIt() {
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> notifier.bookingConfirmed(event))
                .isInstanceOf(MailSendException.class);
    }
}