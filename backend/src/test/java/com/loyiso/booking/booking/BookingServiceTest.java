package com.loyiso.booking.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.loyiso.booking.branch.Branch;
import com.loyiso.booking.branch.BranchRepository;
import com.loyiso.booking.branch.BranchService;
import com.loyiso.booking.common.InvalidRequestException;
import com.loyiso.booking.common.ResourceNotFoundException;
import com.loyiso.booking.slot.SlotService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

class BookingServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");
    // Monday 26 Oct 2026, 10:00 local time
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-26T08:00:00Z"), ZONE);
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);
    private static final LocalDate TOMORROW = TODAY.plusDays(1);

    private BranchRepository branchRepository;
    private BookingRepository bookingRepository;
    private ApplicationEventPublisher publisher;
    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        branchRepository = mock(BranchRepository.class);
        bookingRepository = mock(BookingRepository.class);
        publisher = mock(ApplicationEventPublisher.class);

        Branch branch = new Branch("Test", "1 Main Street", "Johannesburg",
                LocalTime.of(8, 0), LocalTime.of(12, 0), 30);
        when(branchRepository.findById(1L)).thenReturn(Optional.of(branch));
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BranchService branchService = new BranchService(branchRepository);
        SlotService slotService = new SlotService(branchService, bookingRepository, CLOCK);
        bookingService = new BookingService(bookingRepository, branchService, slotService, publisher, CLOCK, mock(IdempotencyKeyRepository.class));
    }

    private CreateBookingRequest request(LocalDate date, LocalTime time) {
        return new CreateBookingRequest(1L, date, time, "  Thandi Nkosi ", "Thandi@Example.com");
    }

    @Test
    void createsBookingAndPublishesEvent() {
        BookingResponse response = bookingService.create(request(TOMORROW, LocalTime.of(9, 0)));

        assertThat(response.reference()).isNotBlank();
        assertThat(response.branchName()).isEqualTo("Test");
        assertThat(response.date()).isEqualTo(TOMORROW);
        assertThat(response.startTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(response.endTime()).isEqualTo(LocalTime.of(9, 30));
        assertThat(response.customerName()).isEqualTo("Thandi Nkosi");
        assertThat(response.customerEmail()).isEqualTo("thandi@example.com");

        ArgumentCaptor<BookingCreatedEvent> captor = ArgumentCaptor.forClass(BookingCreatedEvent.class);
        verify(publisher).publishEvent(captor.capture());
        assertThat(captor.getValue().reference()).isEqualTo(response.reference());
        assertThat(captor.getValue().customerEmail()).isEqualTo("thandi@example.com");
    }

    @Test
    void rejectsSlotThatIsAlreadyBooked() {
        when(bookingRepository.existsByBranchIdAndAppointmentDateAndStartTime(
                null, TOMORROW, LocalTime.of(9, 0))).thenReturn(true);

        assertThatThrownBy(() -> bookingService.create(request(TOMORROW, LocalTime.of(9, 0))))
                .isInstanceOf(SlotUnavailableException.class);

        verify(bookingRepository, never()).saveAndFlush(any(Booking.class));
        verifyNoInteractions(publisher);
    }

    @Test
    void turnsDatabaseUniqueViolationIntoConflict() {
        when(bookingRepository.saveAndFlush(any(Booking.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> bookingService.create(request(TOMORROW, LocalTime.of(9, 0))))
                .isInstanceOf(SlotUnavailableException.class)
                .hasMessageContaining("no longer available");

        verifyNoInteractions(publisher);
    }

    @Test
    void rejectsTimeThatIsNotOnTheSlotGrid() {
        assertThatThrownBy(() -> bookingService.create(request(TOMORROW, LocalTime.of(9, 10))))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("not a valid slot");
    }

    @Test
    void rejectsSlotThatEndsAfterClosingTime() {
        assertThatThrownBy(() -> bookingService.create(request(TOMORROW, LocalTime.of(12, 0))))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("not a valid slot");
    }

    @Test
    void rejectsSlotThatAlreadyStartedToday() {
        assertThatThrownBy(() -> bookingService.create(request(TODAY, LocalTime.of(9, 30))))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("already started");
    }

    @Test
    void unknownBranchIsNotFound() {
        when(branchRepository.findById(99L)).thenReturn(Optional.empty());
        CreateBookingRequest unknown = new CreateBookingRequest(
                99L, TOMORROW, LocalTime.of(9, 0), "Thandi", "thandi@example.com");

        assertThatThrownBy(() -> bookingService.create(unknown))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void lookupOfUnknownReferenceIsNotFound() {
        when(bookingRepository.findByReferenceWithBranch("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.getByReference("nope"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("nope");
    }
}