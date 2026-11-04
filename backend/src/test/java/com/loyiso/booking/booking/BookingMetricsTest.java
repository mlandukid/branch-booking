package com.loyiso.booking.booking;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class BookingMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final BookingMetrics metrics = new BookingMetrics(registry);

    @Test
    void countsCreatedBookingsByBranchAndReason() {
        metrics.onCreated(new BookingCreatedEvent("r1", "Sandton", "addr", LocalDate.of(2026, 11, 2),
                LocalTime.of(9, 0), LocalTime.of(9, 30), "Thandi", "t@example.com", null, BookingReason.REPLACE_CARD));
        metrics.onCreated(new BookingCreatedEvent("r2", "Sandton", "addr", LocalDate.of(2026, 11, 2),
                LocalTime.of(9, 30), LocalTime.of(10, 0), "Sipho", "s@example.com"));

        assertThat(registry.counter("bookings.confirmed", "branch", "Sandton", "reason", "REPLACE_CARD").count()).isEqualTo(1.0);
        assertThat(registry.counter("bookings.confirmed", "branch", "Sandton", "reason", "OTHER").count()).isEqualTo(1.0);
    }

    @Test
    void countsCancelledAndMovedBookings() {
        metrics.onCancelled(new BookingCancelledEvent("r1", "Sandton", "addr", LocalDate.of(2026, 11, 2),
                LocalTime.of(9, 0), "Thandi", "t@example.com"));
        metrics.onRescheduled(new BookingRescheduledEvent("r1", "Sandton", "addr", LocalDate.of(2026, 11, 2),
                LocalTime.of(9, 0), LocalDate.of(2026, 11, 3), LocalTime.of(10, 0), LocalTime.of(10, 30),
                "Thandi", "t@example.com"));

        assertThat(registry.counter("bookings.cancelled", "branch", "Sandton").count()).isEqualTo(1.0);
        assertThat(registry.counter("bookings.rescheduled", "branch", "Sandton").count()).isEqualTo(1.0);
    }
}