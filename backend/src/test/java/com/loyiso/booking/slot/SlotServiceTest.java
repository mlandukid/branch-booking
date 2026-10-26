package com.loyiso.booking.slot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.loyiso.booking.booking.BookingRepository;
import com.loyiso.booking.branch.Branch;
import com.loyiso.booking.branch.BranchRepository;
import com.loyiso.booking.branch.BranchService;
import com.loyiso.booking.common.InvalidRequestException;
import com.loyiso.booking.common.ResourceNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SlotServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");
    // Monday 26 Oct 2026, 10:00 local time (08:00 UTC)
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-26T08:00:00Z"), ZONE);
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);
    private static final LocalDate TOMORROW = TODAY.plusDays(1);

    private BranchRepository branchRepository;
    private BookingRepository bookingRepository;
    private SlotService slotService;

    @BeforeEach
    void setUp() {
        branchRepository = mock(BranchRepository.class);
        bookingRepository = mock(BookingRepository.class);
        Branch branch = new Branch("Test", "1 Main Street", "Johannesburg",
                LocalTime.of(8, 0), LocalTime.of(12, 0), 30);
        when(branchRepository.findById(1L)).thenReturn(Optional.of(branch));
        slotService = new SlotService(new BranchService(branchRepository), bookingRepository, CLOCK);
    }

    @Test
    void generatesEverySlotWithinOpeningHours() {
        List<SlotResponse> slots = slotService.findSlots(1L, TOMORROW);

        assertThat(slots).hasSize(8);
        assertThat(slots.get(0).startTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(slots.get(0).endTime()).isEqualTo(LocalTime.of(8, 30));
        assertThat(slots.get(7).endTime()).isEqualTo(LocalTime.of(12, 0));
        assertThat(slots).allMatch(SlotResponse::available);
    }

    @Test
    void marksBookedSlotAsUnavailable() {
        when(bookingRepository.findBookedStartTimes(1L, TOMORROW)).thenReturn(List.of(LocalTime.of(9, 0)));

        List<SlotResponse> slots = slotService.findSlots(1L, TOMORROW);

        assertThat(slots.get(2).startTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(slots.get(2).available()).isFalse();
        assertThat(slots.stream().filter(SlotResponse::available)).hasSize(7);
    }

    @Test
    void slotsThatAlreadyStartedTodayAreUnavailable() {
        List<SlotResponse> slots = slotService.findSlots(1L, TODAY);

        // now is 10:00, so only 10:30, 11:00 and 11:30 are still in the future
        assertThat(slots.stream().filter(SlotResponse::available)).hasSize(3);
        assertThat(slots.get(5).startTime()).isEqualTo(LocalTime.of(10, 30));
        assertThat(slots.get(5).available()).isTrue();
        assertThat(slots.get(4).available()).isFalse();
    }

    @Test
    void rejectsPastDates() {
        assertThatThrownBy(() -> slotService.findSlots(1L, TODAY.minusDays(1)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("past");
    }

    @Test
    void rejectsDatesTooFarAhead() {
        assertThatThrownBy(() -> slotService.findSlots(1L, TODAY.plusDays(31)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("30 days");
    }

    @Test
    void rejectsWeekends() {
        LocalDate saturday = LocalDate.of(2026, 10, 31);
        assertThatThrownBy(() -> slotService.findSlots(1L, saturday))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("weekends");
    }

    @Test
    void unknownBranchIsNotFound() {
        when(branchRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> slotService.findSlots(99L, TOMORROW))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }
}