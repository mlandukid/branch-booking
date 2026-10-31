package com.loyiso.booking.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.loyiso.booking.branch.Branch;
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
import org.springframework.test.util.ReflectionTestUtils;

class BookingChangeServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");
    // Monday 26 Oct 2026, 10:00 local time
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-26T08:00:00Z"), ZONE);
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);
    private static final LocalDate TOMORROW = TODAY.plusDays(1);
    private static final String TOKEN = "secret-token-value";

    private BookingRepository bookingRepository;
    private BookingHistoryRepository historyRepository;
    private ApplicationEventPublisher publisher;
    private BookingChangeService service;
    private Branch branch;
    private Booking booking;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        historyRepository = mock(BookingHistoryRepository.class);
        publisher = mock(ApplicationEventPublisher.class);

        branch = new Branch("Test", "1 Main Street", "Johannesburg", LocalTime.of(8, 0), LocalTime.of(12, 0), 30);
        ReflectionTestUtils.setField(branch, "id", 1L);
        booking = bookingAt(TOMORROW, LocalTime.of(9, 0));

        SlotService slotService = new SlotService(mock(BranchService.class), bookingRepository, CLOCK);
        service = new BookingChangeService(bookingRepository, slotService, historyRepository, publisher, CLOCK, 2);
    }

    private Booking bookingAt(LocalDate date, LocalTime time) {
        Booking created = new Booking(branch, date, time, "Thandi Nkosi", "thandi@example.com",
                Instant.parse("2026-10-20T08:00:00Z"));
        created.setManageTokenHash(ManageTokens.hash(TOKEN));
        when(bookingRepository.findByReferenceWithBranch(created.getReference())).thenReturn(Optional.of(created));
        return created;
    }

    @Test
    void cancellingDeletesTheBookingRecordsItAndTellsTheCustomer() {
        service.cancel(booking.getReference(), TOKEN);

        verify(bookingRepository).delete(booking);
        verify(bookingRepository).flush();
        ArgumentCaptor<BookingHistory> history = ArgumentCaptor.forClass(BookingHistory.class);
        verify(historyRepository).save(history.capture());
        assertThat(history.getValue().getAction()).isEqualTo("CANCELLED");
        verify(publisher).publishEvent(any(BookingCancelledEvent.class));
    }

    @Test
    void aWrongTokenLooksLikeAnUnknownBooking() {
        when(bookingRepository.findByReferenceWithBranch("no-such-reference")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(booking.getReference(), "a-wrong-guess"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Booking not found");
        assertThatThrownBy(() -> service.cancel("no-such-reference", TOKEN))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Booking not found");

        verify(bookingRepository, never()).delete(any(Booking.class));
        verifyNoInteractions(publisher);
    }

    @Test
    void aMissingTokenIsRefused() {
        assertThatThrownBy(() -> service.cancel(booking.getReference(), null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aBookingWithoutAStoredTokenCannotBeManaged() {
        Booking old = new Booking(branch, TOMORROW, LocalTime.of(10, 0), "Old Customer", "old@example.com",
                Instant.parse("2026-10-20T08:00:00Z"));
        when(bookingRepository.findByReferenceWithBranch(old.getReference())).thenReturn(Optional.of(old));

        assertThatThrownBy(() -> service.cancel(old.getReference(), TOKEN))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void tooCloseToTheStartTimeCannotBeCancelled() {
        Booking soon = bookingAt(TODAY, LocalTime.of(11, 0));

        assertThatThrownBy(() -> service.cancel(soon.getReference(), TOKEN))
                .isInstanceOf(ChangeWindowClosedException.class)
                .hasMessageContaining("2 hours");
        verify(bookingRepository, never()).delete(any(Booking.class));
    }

    @Test
    void movingUpdatesTheBookingRecordsItAndTellsTheCustomer() {
        BookingResponse response = service.reschedule(booking.getReference(), TOKEN, TOMORROW, LocalTime.of(10, 0));

        assertThat(response.startTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(response.customerEmail()).isEqualTo("t***@example.com");
        assertThat(booking.getStartTime()).isEqualTo(LocalTime.of(10, 0));
        verify(bookingRepository).saveAndFlush(booking);
        ArgumentCaptor<BookingRescheduledEvent> event = ArgumentCaptor.forClass(BookingRescheduledEvent.class);
        verify(publisher).publishEvent(event.capture());
        assertThat(event.getValue().oldStart()).isEqualTo(LocalTime.of(9, 0));
        assertThat(event.getValue().newStart()).isEqualTo(LocalTime.of(10, 0));
        verify(historyRepository).save(any(BookingHistory.class));
    }

    @Test
    void movingToTheSameTimeIsRejected() {
        assertThatThrownBy(() -> service.reschedule(booking.getReference(), TOKEN, TOMORROW, LocalTime.of(9, 0)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("already");
    }

    @Test
    void movingToATimeOffTheSlotGridIsRejected() {
        assertThatThrownBy(() -> service.reschedule(booking.getReference(), TOKEN, TOMORROW, LocalTime.of(10, 10)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("not a valid slot");
    }

    @Test
    void movingToATakenSlotIsRefusedAndLeavesTheBookingWhereItWas() {
        when(bookingRepository.existsByBranchIdAndAppointmentDateAndStartTime(1L, TOMORROW, LocalTime.of(10, 0)))
                .thenReturn(true);

        assertThatThrownBy(() -> service.reschedule(booking.getReference(), TOKEN, TOMORROW, LocalTime.of(10, 0)))
                .isInstanceOf(SlotUnavailableException.class);

        assertThat(booking.getStartTime()).isEqualTo(LocalTime.of(9, 0));
        verifyNoInteractions(publisher);
    }

    @Test
    void losingTheRaceForTheNewSlotIsReportedAsAConflict() {
        doThrow(new DataIntegrityViolationException("duplicate key")).when(bookingRepository).saveAndFlush(booking);

        assertThatThrownBy(() -> service.reschedule(booking.getReference(), TOKEN, TOMORROW, LocalTime.of(10, 0)))
                .isInstanceOf(SlotUnavailableException.class);
        verifyNoInteractions(publisher);
    }
}