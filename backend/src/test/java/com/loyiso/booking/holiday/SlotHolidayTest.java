package com.loyiso.booking.holiday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.loyiso.booking.booking.BookingRepository;
import com.loyiso.booking.branch.Branch;
import com.loyiso.booking.branch.BranchRepository;
import com.loyiso.booking.branch.BranchService;
import com.loyiso.booking.common.InvalidRequestException;
import com.loyiso.booking.slot.SlotService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SlotHolidayTest {

    // Monday 26 Oct 2026. The election holiday on Wednesday 4 Nov is inside the 30 day window.
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-26T08:00:00Z"), ZoneId.of("Africa/Johannesburg"));
    private static final LocalDate ELECTION_DAY = LocalDate.of(2026, 11, 4);

    private Branch branch;
    private BranchService branchService;
    private BookingRepository bookingRepository;

    @BeforeEach
    void setUp() {
        BranchRepository branchRepository = mock(BranchRepository.class);
        bookingRepository = mock(BookingRepository.class);
        branch = new Branch("Test", "1 Main Street", "Johannesburg", LocalTime.of(8, 0), LocalTime.of(12, 0), 30);
        when(branchRepository.findById(1L)).thenReturn(Optional.of(branch));
        branchService = new BranchService(branchRepository);
    }

    private SlotService service(boolean closedOnHolidays) {
        return new SlotService(branchService, bookingRepository, CLOCK, new PublicHolidayCalendar(), closedOnHolidays);
    }

    @Test
    void slotsOnAPublicHolidayAreRefused() {
        assertThatThrownBy(() -> service(true).findSlots(1L, ELECTION_DAY))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Local Government Elections Day");
    }

    @Test
    void bookingOnAPublicHolidayIsRefused() {
        assertThatThrownBy(() -> service(true).validateSlot(branch, ELECTION_DAY, LocalTime.of(9, 0)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("closed on");
    }

    @Test
    void theDayAfterAHolidayIsBookable() {
        assertThat(service(true).findSlots(1L, ELECTION_DAY.plusDays(1))).hasSize(8);
    }

    @Test
    void theClosureCanBeSwitchedOff() {
        assertThat(service(false).findSlots(1L, ELECTION_DAY)).hasSize(8);
    }
}