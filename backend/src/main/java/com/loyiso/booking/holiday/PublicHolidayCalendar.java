package com.loyiso.booking.holiday;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * South African public holidays under the Public Holidays Act, 1994: the fixed dates, the two
 * Easter-based days, the rule that a holiday falling on a Sunday is observed on the Monday, and
 * one-off holidays proclaimed by the President.
 */
@Component
public class PublicHolidayCalendar {

    private record Fixed(int month, int day, String code, String name) {
    }

    private static final List<Fixed> FIXED = List.of(
            new Fixed(1, 1, "NEW_YEARS_DAY", "New Year's Day"),
            new Fixed(3, 21, "HUMAN_RIGHTS_DAY", "Human Rights Day"),
            new Fixed(4, 27, "FREEDOM_DAY", "Freedom Day"),
            new Fixed(5, 1, "WORKERS_DAY", "Workers' Day"),
            new Fixed(6, 16, "YOUTH_DAY", "Youth Day"),
            new Fixed(8, 9, "NATIONAL_WOMENS_DAY", "National Women's Day"),
            new Fixed(9, 24, "HERITAGE_DAY", "Heritage Day"),
            new Fixed(12, 16, "DAY_OF_RECONCILIATION", "Day of Reconciliation"),
            new Fixed(12, 25, "CHRISTMAS_DAY", "Christmas Day"),
            new Fixed(12, 26, "DAY_OF_GOODWILL", "Day of Goodwill"));

    /**
     * One-off holidays declared by the President by notice in the Government Gazette. Add new
     * ones here when they are gazetted. The same applies to the rare year in which a statutory
     * holiday needs a further day in lieu, for example when 25 December falls on a Sunday.
     */
    private static final List<Holiday> PROCLAIMED = List.of(
            new Holiday(LocalDate.of(2026, 11, 4), "LOCAL_GOVERNMENT_ELECTIONS", "Local Government Elections Day"));

    private final Map<Integer, List<Holiday>> cache = new ConcurrentHashMap<>();

    public List<Holiday> forYear(int year) {
        return cache.computeIfAbsent(year, this::compute);
    }

    public Optional<Holiday> on(LocalDate date) {
        return forYear(date.getYear()).stream().filter(holiday -> holiday.date().equals(date)).findFirst();
    }

    private List<Holiday> compute(int year) {
        LocalDate easter = easterSunday(year);
        List<Holiday> statutory = new ArrayList<>();
        for (Fixed fixed : FIXED) {
            statutory.add(new Holiday(LocalDate.of(year, fixed.month(), fixed.day()), fixed.code(), fixed.name()));
        }
        statutory.add(new Holiday(easter.minusDays(2), "GOOD_FRIDAY", "Good Friday"));
        statutory.add(new Holiday(easter.plusDays(1), "FAMILY_DAY", "Family Day"));

        Map<LocalDate, Holiday> byDate = new TreeMap<>();
        statutory.forEach(holiday -> byDate.put(holiday.date(), holiday));

        // A holiday that falls on a Sunday is observed on the following Monday.
        for (Holiday holiday : statutory) {
            if (holiday.date().getDayOfWeek() == DayOfWeek.SUNDAY) {
                LocalDate monday = holiday.date().plusDays(1);
                byDate.putIfAbsent(monday, new Holiday(monday, holiday.code(), holiday.name() + " (observed)"));
            }
        }

        PROCLAIMED.stream()
                .filter(holiday -> holiday.date().getYear() == year)
                .forEach(holiday -> byDate.putIfAbsent(holiday.date(), holiday));

        return List.copyOf(byDate.values());
    }

    /** Easter Sunday in the Gregorian calendar (the "anonymous Gregorian" algorithm). */
    static LocalDate easterSunday(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(year, month, day);
    }
}