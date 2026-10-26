package com.loyiso.booking.notification;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.loyiso.booking.booking.BookingCreatedEvent;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BookingNotificationListenerTest {

    private BookingNotifier notifier;
    private BookingNotificationListener listener;
    private BookingCreatedEvent event;

    @BeforeEach
    void setUp() {
        notifier = mock(BookingNotifier.class);
        listener = new BookingNotificationListener(notifier);
        event = new BookingCreatedEvent("ref-1", "Sandton", "Sandton City, Johannesburg",
                LocalDate.of(2026, 11, 2), LocalTime.of(9, 0), LocalTime.of(9, 30),
                "Thandi Nkosi", "thandi@example.com");
    }

    @Test
    void delegatesToTheNotifier() {
        listener.onBookingCreated(event);

        verify(notifier).bookingConfirmed(event);
    }

    @Test
    void neverLetsANotifierFailureEscape() {
        doThrow(new RuntimeException("mail server on fire")).when(notifier).bookingConfirmed(event);

        assertThatCode(() -> listener.onBookingCreated(event)).doesNotThrowAnyException();
    }
}