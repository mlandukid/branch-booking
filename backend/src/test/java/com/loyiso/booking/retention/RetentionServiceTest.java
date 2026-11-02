package com.loyiso.booking.retention;

import static org.assertj.core.api.Assertions.assertThat;

import com.loyiso.booking.booking.Booking;
import com.loyiso.booking.booking.BookingRepository;
import com.loyiso.booking.branch.Branch;
import com.loyiso.booking.branch.BranchRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class RetentionServiceTest {

    @Autowired
    private RetentionService retentionService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Test
    void oldBookingsAreRemovedAndRecentOnesStay() {
        Branch branch = branchRepository.findById(3L).orElseThrow();
        LocalDate longAgo = LocalDate.now().minusDays(200);
        LocalDate recent = LocalDate.now().minusDays(5);
        bookingRepository.saveAndFlush(new Booking(branch, longAgo, LocalTime.of(9, 0),
                "Old Customer", "old@example.com", Instant.now()));
        bookingRepository.saveAndFlush(new Booking(branch, recent, LocalTime.of(9, 0),
                "Recent Customer", "recent@example.com", Instant.now()));

        int removed = retentionService.purgeBookingsBefore(LocalDate.now().minusDays(90));

        assertThat(removed).isGreaterThanOrEqualTo(1);
        assertThat(bookingRepository.existsByBranchIdAndAppointmentDateAndStartTime(3L, longAgo, LocalTime.of(9, 0))).isFalse();
        assertThat(bookingRepository.existsByBranchIdAndAppointmentDateAndStartTime(3L, recent, LocalTime.of(9, 0))).isTrue();
    }
}