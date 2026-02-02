package com.statusbeat.statusbeat.unit.service;

import com.statusbeat.statusbeat.service.TimezoneService;
import com.statusbeat.statusbeat.service.WorkingHoursValidator;
import com.statusbeat.statusbeat.testutil.TestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@DisplayName("WorkingHoursValidator")
class WorkingHoursValidatorTest extends TestBase {

    @Mock
    private TimezoneService timezoneService;

    private WorkingHoursValidator workingHoursValidator;

    @BeforeEach
    void setUp() {
        workingHoursValidator = new WorkingHoursValidator(timezoneService);
    }

    @Nested
    @DisplayName("validateAndConvert")
    class ValidateAndConvertTests {

        @Test
        @DisplayName("should return UTC times for valid input")
        void shouldReturnUtcTimesForValidInput() {
            when(timezoneService.convertLocalToUtc("09:00", 0)).thenReturn(900);
            when(timezoneService.convertLocalToUtc("17:00", 0)).thenReturn(1700);

            Integer[] result = workingHoursValidator.validateAndConvert("09:00", "17:00", 0);

            assertThat(result).isNotNull();
            assertThat(result).hasSize(2);
            assertThat(result[0]).isEqualTo(900);
            assertThat(result[1]).isEqualTo(1700);
        }

        @Test
        @DisplayName("should convert PST times to UTC")
        void shouldConvertPstTimesToUtc() {
            // PST is UTC-8 (-28800 seconds)
            when(timezoneService.convertLocalToUtc("09:00", -28800)).thenReturn(1700);
            when(timezoneService.convertLocalToUtc("17:00", -28800)).thenReturn(100);

            Integer[] result = workingHoursValidator.validateAndConvert("09:00", "17:00", -28800);

            assertThat(result).isNotNull();
            assertThat(result[0]).isEqualTo(1700); // 09:00 PST = 17:00 UTC
            assertThat(result[1]).isEqualTo(100);  // 17:00 PST = 01:00 UTC next day
        }

        @Test
        @DisplayName("should return null for null start time")
        void shouldReturnNullForNullStartTime() {
            Integer[] result = workingHoursValidator.validateAndConvert(null, "17:00", 0);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return null for null end time")
        void shouldReturnNullForNullEndTime() {
            Integer[] result = workingHoursValidator.validateAndConvert("09:00", null, 0);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return null for null timezone offset")
        void shouldReturnNullForNullTimezoneOffset() {
            Integer[] result = workingHoursValidator.validateAndConvert("09:00", "17:00", null);

            assertThat(result).isNull();
        }

        @ParameterizedTest
        @ValueSource(strings = {"9:00", "25:00", "09:60", "invalid", "09-00", "9"})
        @DisplayName("should return null for invalid start time format")
        void shouldReturnNullForInvalidStartTimeFormat(String invalidTime) {
            Integer[] result = workingHoursValidator.validateAndConvert(invalidTime, "17:00", 0);

            assertThat(result).isNull();
        }

        @ParameterizedTest
        @ValueSource(strings = {"17:99", "24:00", "abc", "17.00", ""})
        @DisplayName("should return null for invalid end time format")
        void shouldReturnNullForInvalidEndTimeFormat(String invalidTime) {
            Integer[] result = workingHoursValidator.validateAndConvert("09:00", invalidTime, 0);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return null when start equals end after conversion")
        void shouldReturnNullWhenStartEqualsEnd() {
            when(timezoneService.convertLocalToUtc("09:00", 0)).thenReturn(900);
            when(timezoneService.convertLocalToUtc("09:00", 0)).thenReturn(900);

            Integer[] result = workingHoursValidator.validateAndConvert("09:00", "09:00", 0);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return null when conversion fails")
        void shouldReturnNullWhenConversionFails() {
            when(timezoneService.convertLocalToUtc("09:00", 0)).thenReturn(null);

            Integer[] result = workingHoursValidator.validateAndConvert("09:00", "17:00", 0);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should handle overnight working hours")
        void shouldHandleOvernightHours() {
            // Night shift: 22:00 to 06:00
            when(timezoneService.convertLocalToUtc("22:00", 0)).thenReturn(2200);
            when(timezoneService.convertLocalToUtc("06:00", 0)).thenReturn(600);

            Integer[] result = workingHoursValidator.validateAndConvert("22:00", "06:00", 0);

            assertThat(result).isNotNull();
            assertThat(result[0]).isEqualTo(2200);
            assertThat(result[1]).isEqualTo(600);
        }

        @Test
        @DisplayName("should handle single-digit hour format with leading zero")
        void shouldHandleSingleDigitHourFormat() {
            when(timezoneService.convertLocalToUtc("08:00", 0)).thenReturn(800);
            when(timezoneService.convertLocalToUtc("17:00", 0)).thenReturn(1700);

            Integer[] result = workingHoursValidator.validateAndConvert("08:00", "17:00", 0);

            assertThat(result).isNotNull();
            assertThat(result[0]).isEqualTo(800);
        }

        @Test
        @DisplayName("should handle times with minutes")
        void shouldHandleTimesWithMinutes() {
            when(timezoneService.convertLocalToUtc("08:30", 0)).thenReturn(830);
            when(timezoneService.convertLocalToUtc("17:45", 0)).thenReturn(1745);

            Integer[] result = workingHoursValidator.validateAndConvert("08:30", "17:45", 0);

            assertThat(result).isNotNull();
            assertThat(result[0]).isEqualTo(830);
            assertThat(result[1]).isEqualTo(1745);
        }
    }

    @Nested
    @DisplayName("hasValidWorkingHours")
    class HasValidWorkingHoursTests {

        @Test
        @DisplayName("should return true for valid different hours")
        void shouldReturnTrueForValidDifferentHours() {
            boolean result = workingHoursValidator.hasValidWorkingHours(900, 1700);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false for same hours")
        void shouldReturnFalseForSameHours() {
            boolean result = workingHoursValidator.hasValidWorkingHours(900, 900);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false for null start hour")
        void shouldReturnFalseForNullStartHour() {
            boolean result = workingHoursValidator.hasValidWorkingHours(null, 1700);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false for null end hour")
        void shouldReturnFalseForNullEndHour() {
            boolean result = workingHoursValidator.hasValidWorkingHours(900, null);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when both hours are null")
        void shouldReturnFalseWhenBothNull() {
            boolean result = workingHoursValidator.hasValidWorkingHours(null, null);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return true for overnight hours")
        void shouldReturnTrueForOvernightHours() {
            // 22:00 to 06:00 is valid (overnight shift)
            boolean result = workingHoursValidator.hasValidWorkingHours(2200, 600);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return true when end is less than start (overnight)")
        void shouldReturnTrueWhenEndLessThanStart() {
            boolean result = workingHoursValidator.hasValidWorkingHours(2300, 700);

            assertThat(result).isTrue();
        }
    }
}
