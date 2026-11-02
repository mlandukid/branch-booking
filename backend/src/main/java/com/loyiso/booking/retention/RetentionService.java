package com.loyiso.booking.retention;

import com.loyiso.booking.booking.BookingRepository;
import java.time.Clock;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes bookings, and with them the customer names and email addresses, a set number of days
 * after the appointment. Idempotency keys go with their booking through the database cascade.
 * The booking history holds no personal data, so it is kept.
 */
@Service
public class RetentionService {

    private static final Logger log = LoggerFactory.getLogger(RetentionService.class);

    private final BookingRepository bookingRepository;
    private final Clock clock;
    private final long retentionDays;

    public RetentionService(BookingRepository bookingRepository,
                            Clock clock,
                            @Value("${booking.retention-days:90}") long retentionDays) {
        this.bookingRepository = bookingRepository;
        this.clock = clock;
        this.retentionDays = retentionDays;
    }

    @Transactional
    public int purgeExpired() {
        return purgeBookingsBefore(LocalDate.now(clock).minusDays(retentionDays));
    }

    @Transactional
    public int purgeBookingsBefore(LocalDate cutoff) {
        int deleted = bookingRepository.deleteOlderThan(cutoff);
        if (deleted > 0) {
            log.info("Removed {} bookings for appointments before {}", deleted, cutoff);
        }
        return deleted;
    }
}