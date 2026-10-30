package com.loyiso.booking.booking;

import com.loyiso.booking.branch.Branch;
import com.loyiso.booking.branch.BranchService;
import com.loyiso.booking.common.ResourceNotFoundException;
import com.loyiso.booking.slot.SlotService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;
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
    private final IdempotencyKeyRepository idempotencyKeyRepository;

    public BookingService(BookingRepository bookingRepository,
                          BranchService branchService,
                          SlotService slotService,
                          ApplicationEventPublisher eventPublisher,
                          Clock clock,
                          IdempotencyKeyRepository idempotencyKeyRepository) {
        this.bookingRepository = bookingRepository;
        this.branchService = branchService;
        this.slotService = slotService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
    }

    @Transactional
    public BookingResponse create(CreateBookingRequest request) {
        return place(request, null).booking();
    }

    /**
     * Books a slot. The unique constraint on (branch, date, start time) is the real guarantee
     * against double-booking. The exists check below only produces a friendlier error in the
     * common case, because two requests can both pass it at the same moment.
     *
     * With an idempotency key, repeating the same request returns the original booking and
     * publishes nothing, so a retry never books twice or sends a second email. The key is stored
     * in the same transaction as the booking, so either both exist or neither does.
     *
     * The per-customer limit is a softer rule: two simultaneous requests from the same email
     * could both pass the count. That is acceptable for an anti-hoarding rule, whereas a double
     * booking is not, which is why only the slot rule is enforced by the database.
     */
    @Transactional
    public CreateResult place(CreateBookingRequest request, String idempotencyKey) {
        if (idempotencyKey != null) {
            Optional<IdempotencyKey> existing = idempotencyKeyRepository.findWithBooking(idempotencyKey);
            if (existing.isPresent()) {
                Booking original = existing.get().getBooking();
                if (!isSameRequest(original, request)) {
                    throw new IdempotencyKeyReuseException(
                            "This Idempotency-Key was already used for a different request.");
                }
                return new CreateResult(BookingResponse.from(original), true);
            }
        }

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

        if (idempotencyKey != null) {
            try {
                idempotencyKeyRepository.saveAndFlush(new IdempotencyKey(idempotencyKey, booking, Instant.now(clock)));
            } catch (DataIntegrityViolationException ex) {
                // Another request claimed this key for a different slot at the same moment. Roll back.
                throw new IdempotencyKeyReuseException(
                        "This Idempotency-Key was already used for a different request.");
            }
        }

        eventPublisher.publishEvent(BookingCreatedEvent.from(booking));
        return new CreateResult(BookingResponse.from(booking), false);
    }

    @Transactional(readOnly = true)
    public Optional<BookingResponse> findByIdempotencyKey(String idempotencyKey) {
        return idempotencyKeyRepository.findWithBooking(idempotencyKey)
                .map(key -> BookingResponse.from(key.getBooking()));
    }

    @Transactional(readOnly = true)
    public BookingResponse getByReference(String reference) {
        return bookingRepository.findByReferenceWithBranch(reference)
                .map(BookingResponse::forLookup)
                .orElseThrow(() -> new ResourceNotFoundException("Booking %s not found".formatted(reference)));
    }

    private static boolean isSameRequest(Booking original, CreateBookingRequest request) {
        return original.getBranch().getId().equals(request.branchId())
                && original.getAppointmentDate().equals(request.date())
                && original.getStartTime().equals(request.startTime());
    }

    private SlotUnavailableException slotTaken() {
        return new SlotUnavailableException("That slot is no longer available.");
    }
}