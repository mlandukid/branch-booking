package com.loyiso.booking.booking;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BookingResponseTest {

    @Test
    void masksEverythingButTheFirstLetterOfTheLocalPart() {
        assertThat(BookingResponse.maskEmail("thandi@example.com")).isEqualTo("t***@example.com");
    }

    @Test
    void survivesMalformedInput() {
        assertThat(BookingResponse.maskEmail("nonsense")).isEqualTo("***");
        assertThat(BookingResponse.maskEmail("@example.com")).isEqualTo("***");
        assertThat(BookingResponse.maskEmail(null)).isEqualTo("***");
    }
}