package com.loyiso.booking.booking;

import com.loyiso.booking.branch.Branch;
import com.loyiso.booking.branch.BranchService;
import com.loyiso.booking.common.ResourceNotFoundException;
import com.loyiso.booking.slot.SlotService;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {

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
     */
    @Transactional
    public BookingResponse create(CreateBookingRequest request) {
        Branch branch = branchService.getById(request.branchId());
        slotService.validateSlot(branch, request.date(), request.startTime());

        if (bookingRepository.existsByBranchIdAndAppointmentDateAndStartTime(
                branch.getId(), request.date(), request.startTime())) {
            throw slotTaken();
        }

        Booking booking = new Booking(
                branch,
                request.date(),
                request.startTime(),
                request.customerName().trim(),
                request.customerEmail().trim().toLowerCase(Locale.ROOT),
                Instant.now(clock));

        try {
            // Flush now so a constraint violation surfaces here, not later at commit.
            bookingRepository.saveAndFlush(booking);
        } catch (DataIntegrityViolationException ex) {
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
                .map(BookingResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Booking %s not found".formatted(reference)));
    }

    private SlotUnavailableException slotTaken() {
        return new SlotUnavailableException("That slot is no longer available.");
    }
}