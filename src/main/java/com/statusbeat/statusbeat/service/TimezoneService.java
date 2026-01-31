package com.statusbeat.statusbeat.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Service for handling timezone conversions between user's local time and UTC.
 * Based on SpotMyStatus pattern: store times in UTC, display in local timezone.
 */
@Slf4j
@Service
public class TimezoneService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Converts user's local time (HH:mm format) to UTC time in HHMM integer format.
     * Example: User in PST (-28800 seconds) enters "09:00" → Returns 1700 (5:00 PM UTC)
     *
     * @param localTime User's local time in "HH:mm" format (e.g., "09:00")
     * @param timezoneOffsetSeconds User's timezone offset from UTC in seconds
     * @return UTC time in HHMM integer format (e.g., 1700 for 5:00 PM)
     */
    public Integer convertLocalToUtc(String localTime, Integer timezoneOffsetSeconds) {
        if (localTime == null || timezoneOffsetSeconds == null) {
            log.warn("Cannot convert null local time or timezone offset");
            return null;
        }

        try {
            // Create offset from user's timezone
            ZoneOffset userOffset = ZoneOffset.ofTotalSeconds(timezoneOffsetSeconds);

            // Parse user's local time and attach their timezone offset
            LocalTime parsedTime = LocalTime.parse(localTime, TIME_FORMATTER);
            OffsetTime localOffsetTime = parsedTime.atOffset(userOffset);

            // Convert to UTC
            OffsetTime utcTime = localOffsetTime.withOffsetSameInstant(ZoneOffset.UTC);

            // Convert to HHMM integer format
            int utcHour = utcTime.getHour();
            int utcMinute = utcTime.getMinute();
            int utcHHMM = utcHour * 100 + utcMinute;

            log.debug("Converted local time {} (offset: {}s) to UTC: {}",
                    localTime, timezoneOffsetSeconds, formatHHMM(utcHHMM));

            return utcHHMM;
        } catch (Exception e) {
            log.error("Error converting local time {} to UTC with offset {}: {}",
                    localTime, timezoneOffsetSeconds, e.getMessage());
            return null;
        }
    }

    /**
     * Converts UTC time in HHMM integer format to user's local time in HH:mm string format.
     * Example: UTC 1700 (5:00 PM) for user in PST (-28800 seconds) → Returns "09:00"
     *
     * @param utcHHMM UTC time in HHMM integer format (e.g., 1700 for 5:00 PM)
     * @param timezoneOffsetSeconds User's timezone offset from UTC in seconds
     * @return Local time in "HH:mm" format (e.g., "09:00")
     */
    public String convertUtcToLocal(Integer utcHHMM, Integer timezoneOffsetSeconds) {
        if (utcHHMM == null || timezoneOffsetSeconds == null) {
            log.warn("Cannot convert null UTC time or timezone offset");
            return null;
        }

        try {
            // Parse HHMM integer to hour and minute
            int utcHour = utcHHMM / 100;
            int utcMinute = utcHHMM % 100;

            // Create UTC OffsetTime
            OffsetTime utcTime = LocalTime.of(utcHour, utcMinute).atOffset(ZoneOffset.UTC);

            // Convert to user's timezone
            ZoneOffset userOffset = ZoneOffset.ofTotalSeconds(timezoneOffsetSeconds);
            OffsetTime localTime = utcTime.withOffsetSameInstant(userOffset);

            // Format as HH:mm string
            String formattedTime = localTime.format(TIME_FORMATTER);

            log.debug("Converted UTC {} to local time {} (offset: {}s)",
                    formatHHMM(utcHHMM), formattedTime, timezoneOffsetSeconds);

            return formattedTime;
        } catch (Exception e) {
            log.error("Error converting UTC {} to local time with offset {}: {}",
                    utcHHMM, timezoneOffsetSeconds, e.getMessage());
            return null;
        }
    }

    /**
     * Checks if the current UTC time is within the specified working hours.
     * Handles wrap-around cases (e.g., 22:00 to 06:00 spans midnight).
     *
     * @param syncStartHourUtc Start hour in UTC (HHMM format)
     * @param syncEndHourUtc End hour in UTC (HHMM format)
     * @return true if current time is within working hours, false otherwise
     */
    public boolean isWithinWorkingHours(Integer syncStartHourUtc, Integer syncEndHourUtc) {
        if (syncStartHourUtc == null || syncEndHourUtc == null) {
            log.debug("Working hours not set, allowing sync (24/7 mode)");
            return true; // If no working hours set, sync 24/7
        }

        // Get current UTC time in HHMM format
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        int currentTimeHHMM = now.getHour() * 100 + now.getMinute();

        boolean isWithin;

        // Handle normal case: start < end (e.g., 09:00 to 17:00)
        if (syncStartHourUtc < syncEndHourUtc) {
            isWithin = currentTimeHHMM >= syncStartHourUtc && currentTimeHHMM <= syncEndHourUtc;
        }
        // Handle wrap-around case: end < start (e.g., 22:00 to 06:00, spans midnight)
        else {
            isWithin = currentTimeHHMM >= syncStartHourUtc || currentTimeHHMM <= syncEndHourUtc;
        }

        log.debug("Current UTC time: {}, Working hours: {} - {}, Within hours: {}",
                formatHHMM(currentTimeHHMM),
                formatHHMM(syncStartHourUtc),
                formatHHMM(syncEndHourUtc),
                isWithin);

        return isWithin;
    }

    /**
     * Checks if we're currently OUTSIDE working hours (inverse of isWithinWorkingHours).
     * Useful for "offline hours" checking as in SpotMyStatus.
     */
    public boolean isOutsideWorkingHours(Integer syncStartHourUtc, Integer syncEndHourUtc) {
        return !isWithinWorkingHours(syncStartHourUtc, syncEndHourUtc);
    }

    /**
     * Validates that start and end times are not identical.
     */
    public boolean areValidWorkingHours(Integer startHour, Integer endHour) {
        if (startHour == null || endHour == null) {
            return false;
        }
        return !startHour.equals(endHour);
    }

    /**
     * Formats HHMM integer as readable time string for logging.
     */
    private String formatHHMM(int hhmm) {
        int hour = hhmm / 100;
        int minute = hhmm % 100;
        return String.format("%02d:%02d", hour, minute);
    }

    /**
     * Gets the current UTC time in HHMM integer format.
     * Useful for testing and debugging.
     */
    public int getCurrentUtcTimeHHMM() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return now.getHour() * 100 + now.getMinute();
    }
}
