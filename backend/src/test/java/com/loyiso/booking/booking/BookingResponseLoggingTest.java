package com.loyiso.booking.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class BookingResponseLoggingTest {

    @Test
    void printingAResponseNeverRevealsTheTokenTheEmailOrTheName() {
        BookingResponse response = new BookingResponse("ref-1", 1L, "Sandton", LocalDate.of(2026, 11, 2),
                LocalTime.of(9, 0), LocalTime.of(9, 30), "Thandi Nkosi", "thandi@example.com",
                Instant.parse("2026-10-26T08:00:00Z"), "super-secret-token");

        assertThat(response.toString())
                .contains("ref-1")
                .doesNotContain("super-secret-token")
                .doesNotContain("thandi@example.com")
                .doesNotContain("Thandi");
    }
}