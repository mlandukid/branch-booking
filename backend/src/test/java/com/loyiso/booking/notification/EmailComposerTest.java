package com.loyiso.booking.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.loyiso.booking.booking.BookingCancelledEvent;
import com.loyiso.booking.booking.BookingCreatedEvent;
import com.loyiso.booking.booking.BookingReason;
import com.loyiso.booking.booking.BookingRescheduledEvent;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class EmailComposerTest {

    private final EmailComposer composer = new EmailComposer("http://localhost:8081/");

    private BookingCreatedEvent created(String token, BookingReason reason) {
        return new BookingCreatedEvent("ref-1", "Sandton", "Sandton City, Johannesburg",
                LocalDate.of(2026, 11, 2), LocalTime.of(9, 0), LocalTime.of(9, 30),
                "Thandi Nkosi", "thandi@example.com", token, reason);
    }

    @Test
    void theConfirmationHasTheDetailsAndTheManageLink() {
        EmailContent email = composer.confirmation(created("tok123", null));

        assertThat(email.to()).isEqualTo("thandi@example.com");
        assertThat(email.subject()).contains("Sandton").contains("09:00");
        assertThat(email.body())
                .contains("Thandi Nkosi")
                .contains("Monday, 2 November 2026")
                .contains("09:00 to 09:30")
                .contains("Sandton City, Johannesburg")
                .contains("ref-1")
                .contains("http://localhost:8081/#manage=ref-1.tok123");
    }

    @Test
    void theConfirmationSaysWhyTheyAreComingAndWhatToBring() {
        EmailContent email = composer.confirmation(created("tok123", BookingReason.REPLACE_CARD));

        assertThat(email.body()).contains("Visit:      Replace a card").contains("What to bring: Bring your ID");
    }

    @Test
    void aConfirmationWithoutATokenHasNoManageLink() {
        assertThat(composer.confirmation(created(null, null)).body()).doesNotContain("#manage=");
    }

    @Test
    void theCancellationNamesTheBookingThatWasCancelled() {
        EmailContent email = composer.cancellation(new BookingCancelledEvent("ref-1", "Sandton",
                "Sandton City, Johannesburg", LocalDate.of(2026, 11, 2), LocalTime.of(9, 0),
                "Thandi Nkosi", "thandi@example.com"));

        assertThat(email.subject()).contains("cancelled").contains("Sandton");
        assertThat(email.body()).contains("ref-1").contains("Monday, 2 November 2026");
    }

    @Test
    void theRescheduleShowsTheOldAndTheNewTime() {
        EmailContent email = composer.rescheduled(new BookingRescheduledEvent("ref-1", "Sandton",
                "Sandton City, Johannesburg", LocalDate.of(2026, 11, 2), LocalTime.of(9, 0),
                LocalDate.of(2026, 11, 3), LocalTime.of(14, 0), LocalTime.of(14, 30),
                "Thandi Nkosi", "thandi@example.com"));

        assertThat(email.subject()).contains("moved");
        assertThat(email.body()).contains("Monday, 2 November 2026 at 09:00")
                .contains("Tuesday, 3 November 2026, 14:00 to 14:30");
    }
}