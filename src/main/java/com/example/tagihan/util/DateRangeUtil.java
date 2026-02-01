package com.example.tagihan.util;


import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;


public class DateRangeUtil {

    private static final ZoneId JAKARTA_ZONE = ZoneId.of("Asia/Jakarta");

    private DateRangeUtil() {
        throw new IllegalStateException("Utility class");
    }

    public static DateRange createDayRange(LocalDate date) {
		return createDateRange(date, date);
    }

    public static DateRange createDayRange(String dateStr) {
        LocalDate date = LocalDate.parse(dateStr);
        return createDayRange(date);
    }

    public static DateRange createDateRange(LocalDate startDate, LocalDate endDate) {
        Instant startInstant = startDate.atStartOfDay(JAKARTA_ZONE).toInstant();
        Instant endInstant = endDate.atTime(LocalTime.MAX).atZone(JAKARTA_ZONE).toInstant();
        return new DateRange(startInstant, endInstant);
    }

    public static DateRange createDateRange(String startDateStr, String endDateStr) {
        LocalDate startDate = LocalDate.parse(startDateStr);
        LocalDate endDate = LocalDate.parse(endDateStr);
        return createDateRange(startDate, endDate);
    }

    public record DateRange(Instant start, Instant end) {

        public DateRange {
            if (start.isAfter(end)) {
                throw new IllegalArgumentException("Start date must be before or equal to end date");
            }
        }

        public boolean contains(Instant instant) {
            return !instant.isBefore(start) && !instant.isAfter(end);
        }
    }
    public static LocalDate parseDate(String dateStr) {
        return LocalDate.parse(dateStr);
    }
}
