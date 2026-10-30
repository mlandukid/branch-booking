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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

class IdempotencyTest {

    private static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");
    // Monday 26 Oct 2026, 10:00 local time
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-26T08:00:00Z"), ZONE);
    private static final LocalDate TOMORROW = LocalDate.of(2026, 10, 27);
    private static final String KEY = "key-1234567890";

    private BookingRepository bookingRepository;
    private IdempotencyKeyRepository keyRepository;
    private ApplicationEventPublisher publisher;
    private BookingService bookingService;
    private Branch branch;

    @BeforeEach
    void setUp() {
        BranchRepository branchRepository = mock(BranchRepository.class);
        bookingRepository = mock(BookingRepository.class);
        keyRepository = mock(IdempotencyKeyRepository.class);
        publisher = mock(ApplicationEventPublisher.class);

        branch = new Branch("Test", "1 Main Street", "Johannesburg",
                LocalTime.of(8, 0), LocalTime.of(12, 0), 30);
        ReflectionTestUtils.setField(branch, "id", 1L);
        when(branchRepository.findById(1L)).thenReturn(Optional.of(branch));
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BranchService branchService = new BranchService(branchRepository);
        SlotService slotService = new SlotService(branchService, bookingRepository, CLOCK);
        bookingService = new BookingService(bookingRepository, branchService, slotService, publisher, CLOCK, keyRepository);
    }

    private CreateBookingRequest request(LocalTime time) {
        return new CreateBookingRequest(1L, TOMORROW, time, "Thandi Nkosi", "thandi@example.com");
    }

    private IdempotencyKey storedKeyFor(LocalTime time) {
        Instant created = Instant.parse("2026-10-26T08:00:00Z");
        Booking original = new Booking(branch, TOMORROW, time, "Thandi Nkosi", "thandi@example.com", created);
        return new IdempotencyKey(KEY, original, created);
    }

    @Test
    void firstUseStoresTheKeyAndIsNotAReplay() {
        CreateResult result = bookingService.place(request(LocalTime.of(9, 0)), KEY);

        assertThat(result.replayed()).isFalse();
        verify(keyRepository).saveAndFlush(any(IdempotencyKey.class));
        verify(publisher).publishEvent(any(BookingCreatedEvent.class));
    }

    @Test
    void repeatingTheSameRequestReturnsTheOriginalWithoutBookingOrEmailingAgain() {
        IdempotencyKey stored = storedKeyFor(LocalTime.of(9, 0));
        when(keyRepository.findWithBooking(KEY)).thenReturn(Optional.of(stored));

        CreateResult result = bookingService.place(request(LocalTime.of(9, 0)), KEY);

        assertThat(result.replayed()).isTrue();
        assertThat(result.booking().reference()).isEqualTo(stored.getBooking().getReference());
        verify(bookingRepository, never()).saveAndFlush(any(Booking.class));
        verifyNoInteractions(publisher);
    }

    @Test
    void reusingAKeyForADifferentRequestIsRejected() {
        when(keyRepository.findWithBooking(KEY)).thenReturn(Optional.of(storedKeyFor(LocalTime.of(9, 0))));

        assertThatThrownBy(() -> bookingService.place(request(LocalTime.of(9, 30)), KEY))
                .isInstanceOf(IdempotencyKeyReuseException.class)
                .hasMessageContaining("Idempotency-Key");
    }

    @Test
    void aKeyThatLosesTheInsertRaceIsReportedAsReused() {
        doThrow(new DataIntegrityViolationException("duplicate key"))
                .when(keyRepository).saveAndFlush(any(IdempotencyKey.class));

        assertThatThrownBy(() -> bookingService.place(request(LocalTime.of(9, 0)), KEY))
                .isInstanceOf(IdempotencyKeyReuseException.class);
        verifyNoInteractions(publisher);
    }

    @Test
    void requestsWithoutAKeyNeverTouchTheKeyStore() {
        bookingService.place(request(LocalTime.of(9, 0)), null);

        verifyNoInteractions(keyRepository);
    }
}