package com.loyiso.booking.holiday;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PublicHolidayCalendarTest {

    private final PublicHolidayCalendar calendar = new PublicHolidayCalendar();

    @Test
    void easterSundayIsCorrectForKnownYears() {
        assertThat(PublicHolidayCalendar.easterSunday(2024)).isEqualTo(LocalDate.of(2024, 3, 31));
        assertThat(PublicHolidayCalendar.easterSunday(2025)).isEqualTo(LocalDate.of(2025, 4, 20));
        assertThat(PublicHolidayCalendar.easterSunday(2026)).isEqualTo(LocalDate.of(2026, 4, 5));
        assertThat(PublicHolidayCalendar.easterSunday(2027)).isEqualTo(LocalDate.of(2027, 3, 28));
    }

    @Test
    void thereAreFourteenHolidayDatesIn2026() {
        List<LocalDate> dates = calendar.forYear(2026).stream().map(Holiday::date).toList();

        assertThat(dates).containsExactly(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 3, 21),
                LocalDate.of(2026, 4, 3),
                LocalDate.of(2026, 4, 6),
                LocalDate.of(2026, 4, 27),
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 6, 16),
                LocalDate.of(2026, 8, 9),
                LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 9, 24),
                LocalDate.of(2026, 11, 4),
                LocalDate.of(2026, 12, 16),
                LocalDate.of(2026, 12, 25),
                LocalDate.of(2026, 12, 26));
    }

    @Test
    void aHolidayOnASundayIsObservedOnTheMonday() {
        Holiday observed = calendar.on(LocalDate.of(2026, 8, 10)).orElseThrow();

        assertThat(observed.code()).isEqualTo("NATIONAL_WOMENS_DAY");
        assertThat(observed.name()).isEqualTo("National Women's Day (observed)");
    }

    @Test
    void theSundayRuleAlsoAppliesInOtherYears() {
        assertThat(calendar.on(LocalDate.of(2027, 3, 22))).isPresent();
        assertThat(calendar.on(LocalDate.of(2027, 12, 27))).isPresent();
    }

    @Test
    void aHolidayOnASaturdayIsNotMoved() {
        assertThat(calendar.on(LocalDate.of(2026, 3, 21))).isPresent();
        assertThat(calendar.on(LocalDate.of(2026, 3, 23))).isEmpty();
    }

    @Test
    void theProclaimedElectionDayIsIncludedOnlyInItsOwnYear() {
        assertThat(calendar.on(LocalDate.of(2026, 11, 4)).orElseThrow().name())
                .isEqualTo("Local Government Elections Day");
        assertThat(calendar.on(LocalDate.of(2027, 11, 4))).isEmpty();
    }

    @Test
    void ordinaryDaysAreNotHolidays() {
        assertThat(calendar.on(LocalDate.of(2026, 11, 3))).isEmpty();
        assertThat(calendar.on(LocalDate.of(2026, 11, 5))).isEmpty();
    }
}