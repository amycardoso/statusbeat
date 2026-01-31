package com.statusbeat.statusbeat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for validating and converting working hours settings.
 * Ensures start/end times are valid before storage.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkingHoursValidator {

    private final TimezoneService timezoneService;

    /**
     * Validates and converts working hours from user's local time to UTC.
     * Returns an array with [utcStartHour, utcEndHour] or null if invalid.
     *
     * @param localStartTime Start time in HH:mm format (user's local time)
     * @param localEndTime End time in HH:mm format (user's local time)
     * @param timezoneOffsetSeconds User's timezone offset from UTC in seconds
     * @return Integer array [utcStartHour, utcEndHour] in HHMM format, or null if invalid
     */
    public Integer[] validateAndConvert(String localStartTime, String localEndTime, Integer timezoneOffsetSeconds) {
        // Validate inputs are not null
        if (localStartTime == null || localEndTime == null || timezoneOffsetSeconds == null) {
            log.warn("Cannot validate working hours with null inputs");
            return null;
        }

        // Validate time format (HH:mm)
        if (!isValidTimeFormat(localStartTime) || !isValidTimeFormat(localEndTime)) {
            log.warn("Invalid time format. Expected HH:mm, got start: {}, end: {}",
                    localStartTime, localEndTime);
            return null;
        }

        // Convert to UTC
        Integer utcStartHour = timezoneService.convertLocalToUtc(localStartTime, timezoneOffsetSeconds);
        Integer utcEndHour = timezoneService.convertLocalToUtc(localEndTime, timezoneOffsetSeconds);

        if (utcStartHour == null || utcEndHour == null) {
            log.warn("Failed to convert times to UTC");
            return null;
        }

        // Validate start != end
        if (utcStartHour.equals(utcEndHour)) {
            log.warn("Start and end times cannot be identical: {} (UTC: {})",
                    localStartTime, utcStartHour);
            return null;
        }

        log.debug("Validated and converted working hours: {} - {} (local) → {} - {} (UTC)",
                localStartTime, localEndTime,
                formatHHMM(utcStartHour), formatHHMM(utcEndHour));

        return new Integer[]{utcStartHour, utcEndHour};
    }

    /**
     * Validates time string is in HH:mm format.
     */
    private boolean isValidTimeFormat(String time) {
        if (time == null) {
            return false;
        }
        // Check format: HH:mm (e.g., "09:00", "13:30")
        return time.matches("^([0-1]?[0-9]|2[0-3]):[0-5][0-9]$");
    }

    /**
     * Formats HHMM integer as HH:mm string for logging.
     */
    private String formatHHMM(int hhmm) {
        int hour = hhmm / 100;
        int minute = hhmm % 100;
        return String.format("%02d:%02d", hour, minute);
    }

    /**
     * Checks if working hours are configured and valid.
     */
    public boolean hasValidWorkingHours(Integer syncStartHour, Integer syncEndHour) {
        if (syncStartHour == null || syncEndHour == null) {
            return false;
        }
        return !syncStartHour.equals(syncEndHour);
    }
}
