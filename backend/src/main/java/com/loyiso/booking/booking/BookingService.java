package com.loyiso.booking.booking;

import com.loyiso.booking.branch.Branch;
import com.loyiso.booking.branch.BranchService;
import com.loyiso.booking.common.ResourceNotFoundException;
import com.loyiso.booking.slot.SlotService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {

    /** Stops one customer hoarding slots. */
    public static final int MAX_UPCOMING_BOOKINGS = 3;

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final BookingRepository bookingRepository;
    private final BranchService branchService;
    private final SlotService slotService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public BookingService(BookingRepository bookingRepository,
                          BranchService branchService,
                          SlotService slotService,
                          ApplicationEventPublisher eventPublisher,
                          Clock clock) {
        this.bookingRepository = bookingRepository;
        this.branchService = branchService;
        this.slotService = slotService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * Books a slot. The unique constraint on (branch, date, start time) is the real guarantee
     * against double-booking. The exists check below only produces a friendlier error in the
     * common case, because two requests can both pass it at the same moment.
     *
     * The per-customer limit is a softer rule: two simultaneous requests from the same email
     * could both pass the count. That is acceptable for an anti-hoarding rule, whereas a double
     * booking is not, which is why only the slot rule is enforced by the database.
     */
    @Transactional
    public BookingResponse create(CreateBookingRequest request) {
        Branch branch = branchService.getById(request.branchId());
        slotService.validateSlot(branch, request.date(), request.startTime());

        String email = request.customerEmail().trim().toLowerCase(Locale.ROOT);
        long upcoming = bookingRepository
                .countByCustomerEmailAndAppointmentDateGreaterThanEqual(email, LocalDate.now(clock));
        if (upcoming >= MAX_UPCOMING_BOOKINGS) {
            throw new BookingLimitExceededException(
                    "You already have the maximum of %d upcoming appointments.".formatted(MAX_UPCOMING_BOOKINGS));
        }

        if (bookingRepository.existsByBranchIdAndAppointmentDateAndStartTime(
                branch.getId(), request.date(), request.startTime())) {
            throw slotTaken();
        }

        Booking booking = new Booking(
                branch,
                request.date(),
                request.startTime(),
                request.customerName().trim(),
                email,
                Instant.now(clock));

        try {
            // Flush now so a constraint violation surfaces here, not later at commit.
            bookingRepository.saveAndFlush(booking);
        } catch (DataIntegrityViolationException ex) {
            // The slot constraint is the only one that can realistically fire here: the email is
            // lower-cased above and the branch was loaded above.
            log.info("Lost the race for slot {} {} at branch {}",
                    request.date(), request.startTime(), branch.getId());
            throw new SlotUnavailableException("That slot is no longer available.", ex);
        }

        eventPublisher.publishEvent(BookingCreatedEvent.from(booking));
        return BookingResponse.from(booking);
    }

    @Transactional(readOnly = true)
    public BookingResponse getByReference(String reference) {
        return bookingRepository.findByReferenceWithBranch(reference)
                .map(BookingResponse::forLookup)
                .orElseThrow(() -> new ResourceNotFoundException("Booking %s not found".formatted(reference)));
    }

    private SlotUnavailableException slotTaken() {
        return new SlotUnavailableException("That slot is no longer available.");
    }
}