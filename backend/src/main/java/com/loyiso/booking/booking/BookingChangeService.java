package com.loyiso.booking.booking;

import com.loyiso.booking.common.InvalidRequestException;
import com.loyiso.booking.common.ResourceNotFoundException;
import com.loyiso.booking.slot.SlotService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lets a customer view, move or cancel a booking with the private token from the confirmation email.
 *
 * Cancelling deletes the booking, which frees its slot without touching the double-booking rule.
 * Moving updates the same row, so the unique slot constraint still decides every race. The version
 * column makes two simultaneous changes to one booking fail cleanly instead of overwriting each other.
 */
@Service
public class BookingChangeService {

    private static final Logger log = LoggerFactory.getLogger(BookingChangeService.class);

    private final BookingRepository bookingRepository;
    private final SlotService slotService;
    private final BookingHistoryRepository historyRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final long cutoffHours;

    public BookingChangeService(BookingRepository bookingRepository,
                                SlotService slotService,
                                BookingHistoryRepository historyRepository,
                                ApplicationEventPublisher eventPublisher,
                                Clock clock,
                                @Value("${booking.change-cutoff-hours:2}") long cutoffHours) {
        this.bookingRepository = bookingRepository;
        this.slotService = slotService;
        this.historyRepository = historyRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.cutoffHours = cutoffHours;
    }

    @Transactional(readOnly = true)
    public BookingResponse view(String reference, String token) {
        return BookingResponse.forLookup(loadAuthorised(reference, token));
    }

    @Transactional
    public void cancel(String reference, String token) {
        Booking booking = loadAuthorised(reference, token);
        ensureChangeable(booking);

        BookingCancelledEvent event = BookingCancelledEvent.from(booking);
        historyRepository.save(BookingHistory.cancelled(booking, Instant.now(clock)));
        bookingRepository.delete(booking);
        // Flush now so a clash with a simultaneous change surfaces here, inside the transaction.
        bookingRepository.flush();

        log.info("Booking {} cancelled", reference);
        eventPublisher.publishEvent(event);
    }

    @Transactional
    public BookingResponse reschedule(String reference, String token, LocalDate newDate, LocalTime newStart) {
        Booking booking = loadAuthorised(reference, token);
        ensureChangeable(booking);

        LocalDate oldDate = booking.getAppointmentDate();
        LocalTime oldStart = booking.getStartTime();
        if (oldDate.equals(newDate) && oldStart.equals(newStart)) {
            throw new InvalidRequestException("That is already the time of your appointment.");
        }

        slotService.validateSlot(booking.getBranch(), newDate, newStart);
        if (bookingRepository.existsByBranchIdAndAppointmentDateAndStartTime(
                booking.getBranch().getId(), newDate, newStart)) {
            throw new SlotUnavailableException("That slot is no longer available.");
        }

        booking.reschedule(newDate, newStart);
        try {
            // Flush now so a constraint violation surfaces here, not later at commit.
            bookingRepository.saveAndFlush(booking);
        } catch (DataIntegrityViolationException ex) {
            throw new SlotUnavailableException("That slot is no longer available.", ex);
        }

        historyRepository.save(BookingHistory.rescheduled(booking, oldDate, oldStart, Instant.now(clock)));
        log.info("Booking {} moved", reference);
        eventPublisher.publishEvent(BookingRescheduledEvent.from(booking, oldDate, oldStart));
        return BookingResponse.forLookup(booking);
    }

    /** A wrong token and an unknown reference look identical, so nobody can probe for which bookings exist. */
    private Booking loadAuthorised(String reference, String token) {
        Booking booking = bookingRepository.findByReferenceWithBranch(reference)
                .orElseThrow(BookingChangeService::notFound);
        if (!ManageTokens.matches(token, booking.getManageTokenHash())) {
            throw notFound();
        }
        return booking;
    }

    private void ensureChangeable(Booking booking) {
        LocalDateTime start = LocalDateTime.of(booking.getAppointmentDate(), booking.getStartTime());
        if (!start.minusHours(cutoffHours).isAfter(LocalDateTime.now(clock))) {
            throw new ChangeWindowClosedException(
                    "Appointments can only be changed or cancelled up to %d hours before they start. "
                            .formatted(cutoffHours) + "Please contact the branch.");
        }
    }

    private static ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("Booking not found");
    }
}