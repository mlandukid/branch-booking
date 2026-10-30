package com.loyiso.booking.slot;

import com.loyiso.booking.booking.BookingRepository;
import com.loyiso.booking.branch.Branch;
import com.loyiso.booking.branch.BranchService;
import com.loyiso.booking.common.InvalidRequestException;
import com.loyiso.booking.holiday.Holiday;
import com.loyiso.booking.holiday.PublicHolidayCalendar;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Slots are computed from the branch opening hours and slot length, then matched
 * against existing bookings. Nothing is pre-generated, so there is no slot data to go stale.
 * Branches are closed at weekends and on South African public holidays.
 */
@Service
public class SlotService {

    public static final int MAX_ADVANCE_DAYS = 30;

    private final BranchService branchService;
    private final BookingRepository bookingRepository;
    private final Clock clock;
    private final PublicHolidayCalendar holidays;
    private final boolean closedOnHolidays;

    @Autowired
    public SlotService(BranchService branchService,
                       BookingRepository bookingRepository,
                       Clock clock,
                       PublicHolidayCalendar holidays,
                       @Value("${booking.closed-on-public-holidays:true}") boolean closedOnHolidays) {
        this.branchService = branchService;
        this.bookingRepository = bookingRepository;
        this.clock = clock;
        this.holidays = holidays;
        this.closedOnHolidays = closedOnHolidays;
    }

    public SlotService(BranchService branchService, BookingRepository bookingRepository, Clock clock) {
        this(branchService, bookingRepository, clock, new PublicHolidayCalendar(), true);
    }

    @Transactional(readOnly = true)
    public List<SlotResponse> findSlots(Long branchId, LocalDate date) {
        Branch branch = branchService.getById(branchId);
        validateBookableDate(date);

        Set<LocalTime> booked = new HashSet<>(bookingRepository.findBookedStartTimes(branchId, date));
        LocalDateTime now = LocalDateTime.now(clock);

        List<SlotResponse> slots = new ArrayList<>();
        LocalTime start = branch.getOpensAt();
        while (true) {
            LocalTime end = start.plusMinutes(branch.getSlotMinutes());
            boolean wrappedPastMidnight = !end.isAfter(start);
            if (wrappedPastMidnight || end.isAfter(branch.getClosesAt())) {
                break;
            }
            boolean available = !booked.contains(start)
                    && LocalDateTime.of(date, start).isAfter(now);
            slots.add(new SlotResponse(start, end, available));
            start = end;
        }
        return slots;
    }

    public void validateBookableDate(LocalDate date) {
        LocalDate today = LocalDate.now(clock);
        if (date.isBefore(today)) {
            throw new InvalidRequestException("The date must not be in the past.");
        }
        if (date.isAfter(today.plusDays(MAX_ADVANCE_DAYS))) {
            throw new InvalidRequestException(
                    "Appointments can only be booked up to %d days ahead.".formatted(MAX_ADVANCE_DAYS));
        }
        DayOfWeek day = date.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            throw new InvalidRequestException("Branches are closed on weekends.");
        }
        Optional<Holiday> holiday = closedOnHolidays ? holidays.on(date) : Optional.empty();
        if (holiday.isPresent()) {
            throw new InvalidRequestException("Branches are closed on %s.".formatted(holiday.get().name()));
        }
    }

    /**
     * Checks that the requested start time is a real slot for this branch: on the slot grid,
     * inside opening hours, and not already in the past.
     */
    public void validateSlot(Branch branch, LocalDate date, LocalTime start) {
        validateBookableDate(date);

        LocalTime end = start.plusMinutes(branch.getSlotMinutes());
        boolean wholeMinute = start.equals(start.truncatedTo(ChronoUnit.MINUTES));
        boolean withinHours = !start.isBefore(branch.getOpensAt())
                && end.isAfter(start)
                && !end.isAfter(branch.getClosesAt());
        boolean onGrid = withinHours
                && wholeMinute
                && Duration.between(branch.getOpensAt(), start).toMinutes() % branch.getSlotMinutes() == 0;

        if (!onGrid) {
            throw new InvalidRequestException("The requested time is not a valid slot for this branch.");
        }
        if (!LocalDateTime.of(date, start).isAfter(LocalDateTime.now(clock))) {
            throw new InvalidRequestException("The requested slot has already started.");
        }
    }
}