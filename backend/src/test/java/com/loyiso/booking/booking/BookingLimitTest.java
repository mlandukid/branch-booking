package com.loyiso.booking.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.loyiso.booking.branch.Branch;
import com.loyiso.booking.branch.BranchRepository;
import com.loyiso.booking.branch.BranchService;
import com.loyiso.booking.slot.SlotService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class BookingLimitTest {

    private static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");
    // Monday 26 Oct 2026, 10:00 local time
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-26T08:00:00Z"), ZONE);
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);
    private static final LocalDate TOMORROW = TODAY.plusDays(1);

    private BookingRepository bookingRepository;
    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        BranchRepository branchRepository = mock(BranchRepository.class);
        bookingRepository = mock(BookingRepository.class);
        Branch branch = new Branch("Test", "1 Main Street", "Johannesburg",
                LocalTime.of(8, 0), LocalTime.of(12, 0), 30);
        when(branchRepository.findById(1L)).thenReturn(Optional.of(branch));
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BranchService branchService = new BranchService(branchRepository);
        SlotService slotService = new SlotService(branchService, bookingRepository, CLOCK);
        bookingService = new BookingService(bookingRepository, branchService, slotService,
                mock(ApplicationEventPublisher.class), CLOCK, mock(IdempotencyKeyRepository.class));
    }

    private CreateBookingRequest request(String email) {
        return new CreateBookingRequest(1L, TOMORROW, LocalTime.of(9, 0), "Thandi Nkosi", email);
    }

    @Test
    void rejectsABookingOnceTheLimitIsReached() {
        when(bookingRepository.countByCustomerEmailAndAppointmentDateGreaterThanEqual("thandi@example.com", TODAY))
                .thenReturn(3L);

        assertThatThrownBy(() -> bookingService.create(request("thandi@example.com")))
                .isInstanceOf(BookingLimitExceededException.class)
                .hasMessageContaining("maximum of 3");

        verify(bookingRepository, never()).saveAndFlush(any(Booking.class));
    }

    @Test
    void allowsABookingBelowTheLimit() {
        when(bookingRepository.countByCustomerEmailAndAppointmentDateGreaterThanEqual("thandi@example.com", TODAY))
                .thenReturn(2L);

        BookingResponse response = bookingService.create(request("thandi@example.com"));

        assertThat(response.reference()).isNotBlank();
    }

    @Test
    void countsTheCustomerCaseInsensitively() {
        bookingService.create(request("  Thandi@Example.COM "));

        verify(bookingRepository)
                .countByCustomerEmailAndAppointmentDateGreaterThanEqual("thandi@example.com", TODAY);
    }
}