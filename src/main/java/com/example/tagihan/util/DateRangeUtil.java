package com.example.tagihan.util;


import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class DateRangeUtil {

    private static final Pattern DATE_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

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
    private static LocalDate parseReminder(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }

        try {
            Matcher matcher = DATE_PATTERN.matcher(text);
            if (matcher.find()) {
                String date = matcher.group();
                return LocalDate.parse(date);
            }
        } catch (Exception e) {
            log.warn("Failed to parse reminder date from: {}", text, e);
        }

        return null;
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
