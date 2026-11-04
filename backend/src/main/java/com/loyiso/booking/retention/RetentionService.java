package com.loyiso.booking.retention;

import com.loyiso.booking.booking.BookingRepository;
import com.loyiso.booking.outbox.OutboxEmailRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes bookings, and with them the customer names and email addresses, a set number of days
 * after the appointment, and clears out old queued emails. Idempotency keys go with their booking
 * through the database cascade. The booking history holds no personal data, so it is kept.
 */
@Service
public class RetentionService {

    private static final Logger log = LoggerFactory.getLogger(RetentionService.class);

    private final BookingRepository bookingRepository;
    private final OutboxEmailRepository outboxRepository;
    private final Clock clock;
    private final long retentionDays;
    private final long outboxRetentionDays;

    public RetentionService(BookingRepository bookingRepository,
                            OutboxEmailRepository outboxRepository,
                            Clock clock,
                            @Value("${booking.retention-days:90}") long retentionDays,
                            @Value("${booking.outbox.retention-days:30}") long outboxRetentionDays) {
        this.bookingRepository = bookingRepository;
        this.outboxRepository = outboxRepository;
        this.clock = clock;
        this.retentionDays = retentionDays;
        this.outboxRetentionDays = outboxRetentionDays;
    }

    @Transactional
    public int purgeExpired() {
        int emails = outboxRepository.deleteOlderThan(clock.instant().minus(Duration.ofDays(outboxRetentionDays)));
        if (emails > 0) {
            log.info("Removed {} queued emails older than {} days", emails, outboxRetentionDays);
        }
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