package com.loyiso.booking.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.loyiso.booking.booking.BookingCancelledEvent;
import com.loyiso.booking.booking.BookingCreatedEvent;
import com.loyiso.booking.booking.BookingRescheduledEvent;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class ChangeNotificationTest {

    private JavaMailSender mailSender;
    private MailBookingNotifier notifier;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        notifier = new MailBookingNotifier(mailSender, "no-reply@test.local");
    }

    private SimpleMailMessage sent() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

    private BookingCreatedEvent created(String token) {
        return new BookingCreatedEvent("ref-1", "Sandton", "Sandton City, Johannesburg",
                LocalDate.of(2026, 11, 2), LocalTime.of(9, 0), LocalTime.of(9, 30),
                "Thandi Nkosi", "thandi@example.com", token);
    }

    @Test
    void theConfirmationCarriesThePrivateManageLink() {
        notifier.bookingConfirmed(created("tok123"));

        assertThat(sent().getText()).contains("http://localhost:8081/#manage=ref-1.tok123");
    }

    @Test
    void aConfirmationWithoutATokenHasNoManageLink() {
        notifier.bookingConfirmed(created(null));

        assertThat(sent().getText()).doesNotContain("#manage=");
    }

    @Test
    void theCancellationEmailNamesTheBookingThatWasCancelled() {
        notifier.bookingCancelled(new BookingCancelledEvent("ref-1", "Sandton", "Sandton City, Johannesburg",
                LocalDate.of(2026, 11, 2), LocalTime.of(9, 0), "Thandi Nkosi", "thandi@example.com"));

        SimpleMailMessage message = sent();
        assertThat(message.getTo()).containsExactly("thandi@example.com");
        assertThat(message.getSubject()).contains("cancelled").contains("Sandton");
        assertThat(message.getText()).contains("ref-1").contains("Monday, 2 November 2026");
    }

    @Test
    void theRescheduleEmailShowsTheOldAndTheNewTime() {
        notifier.bookingRescheduled(new BookingRescheduledEvent("ref-1", "Sandton", "Sandton City, Johannesburg",
                LocalDate.of(2026, 11, 2), LocalTime.of(9, 0),
                LocalDate.of(2026, 11, 3), LocalTime.of(14, 0), LocalTime.of(14, 30),
                "Thandi Nkosi", "thandi@example.com"));

        SimpleMailMessage message = sent();
        assertThat(message.getSubject()).contains("moved");
        assertThat(message.getText()).contains("Monday, 2 November 2026 at 09:00")
                .contains("Tuesday, 3 November 2026, 14:00 to 14:30");
    }
}