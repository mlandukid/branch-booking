package com.loyiso.booking.branch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.loyiso.booking.booking.BookingRepository;
import com.loyiso.booking.common.ConflictException;
import com.loyiso.booking.common.InvalidRequestException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class BranchAdminServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-26T08:00:00Z"), ZoneId.of("Africa/Johannesburg"));

    private BranchRepository branches;
    private BookingRepository bookings;
    private BranchAdminService service;
    private Branch existing;

    @BeforeEach
    void setUp() {
        branches = mock(BranchRepository.class);
        bookings = mock(BookingRepository.class);
        existing = new Branch("Sandton", "Sandton City", "Johannesburg", LocalTime.of(8, 0), LocalTime.of(16, 0), 30);
        ReflectionTestUtils.setField(existing, "id", 1L);
        when(branches.findById(1L)).thenReturn(Optional.of(existing));
        when(branches.saveAndFlush(any(Branch.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new BranchAdminService(branches, bookings, CLOCK);
    }

    private BranchRequest request(String name, String city, LocalTime opens, LocalTime closes, int slot) {
        return new BranchRequest(name, "1 Test Street", city, opens, closes, slot);
    }

    @Test
    void createsABranch() {
        Branch created = service.create(request("  Pretoria Central ", "Pretoria", LocalTime.of(8, 0), LocalTime.of(16, 0), 30));

        assertThat(created.getName()).isEqualTo("Pretoria Central");
        assertThat(created.getSlotMinutes()).isEqualTo(30);
    }

    @Test
    void refusesADuplicateName() {
        when(branches.existsByNameIgnoreCase("Sandton")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request("Sandton", "Johannesburg", LocalTime.of(8, 0), LocalTime.of(16, 0), 30)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void refusesHoursThatDoNotOpenBeforeTheyClose() {
        assertThatThrownBy(() -> service.create(request("A", "B", LocalTime.of(16, 0), LocalTime.of(8, 0), 30)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("open before it closes");
    }

    @Test
    void refusesHoursThatCannotFitOneAppointment() {
        assertThatThrownBy(() -> service.create(request("A", "B", LocalTime.of(8, 0), LocalTime.of(8, 20), 30)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("fit at least one");
    }

    @Test
    void refusesAScheduleChangeWhileThereAreUpcomingBookings() {
        when(bookings.existsByBranchIdAndAppointmentDateGreaterThanEqual(eq(1L), any(LocalDate.class))).thenReturn(true);

        assertThatThrownBy(() -> service.update(1L, request("Sandton", "Johannesburg", LocalTime.of(8, 0), LocalTime.of(15, 0), 30)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("upcoming bookings");
        assertThat(existing.getClosesAt()).isEqualTo(LocalTime.of(16, 0));
    }

    @Test
    void allowsOtherChangesWhileThereAreUpcomingBookings() {
        when(bookings.existsByBranchIdAndAppointmentDateGreaterThanEqual(eq(1L), any(LocalDate.class))).thenReturn(true);

        Branch updated = service.update(1L, request("Sandton", "Sandton, Johannesburg", LocalTime.of(8, 0), LocalTime.of(16, 0), 30));

        assertThat(updated.getCity()).isEqualTo("Sandton, Johannesburg");
    }
}