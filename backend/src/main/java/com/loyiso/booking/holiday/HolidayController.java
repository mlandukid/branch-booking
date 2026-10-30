package com.loyiso.booking.holiday;

import com.loyiso.booking.common.InvalidRequestException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/holidays")
@Tag(name = "Holidays", description = "South African public holidays, on which branches are closed")
public class HolidayController {

    private static final int MIN_YEAR = 2000;
    private static final int MAX_YEAR = 2100;

    private final PublicHolidayCalendar calendar;

    public HolidayController(PublicHolidayCalendar calendar) {
        this.calendar = calendar;
    }

    @GetMapping
    @Operation(summary = "List the public holidays in a year",
            description = "Branches are closed on these days, so slots cannot be listed or booked on them.")
    public List<Holiday> list(@Parameter(description = "Four digit year", example = "2026") @RequestParam int year) {
        if (year < MIN_YEAR || year > MAX_YEAR) {
            throw new InvalidRequestException("The year must be between %d and %d.".formatted(MIN_YEAR, MAX_YEAR));
        }
        return calendar.forYear(year);
    }
}