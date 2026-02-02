package com.statusbeat.statusbeat.unit.service;

import com.statusbeat.statusbeat.service.TimezoneService;
import com.statusbeat.statusbeat.testutil.TestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TimezoneService")
class TimezoneServiceTest extends TestBase {

    private TimezoneService timezoneService;

    @BeforeEach
    void setUp() {
        timezoneService = new TimezoneService();
    }

    @Nested
    @DisplayName("convertLocalToUtc")
    class ConvertLocalToUtcTests {

        @Test
        @DisplayName("should convert PST time to UTC")
        void shouldConvertPstToUtc() {
            // PST is UTC-8 (-28800 seconds)
            // 09:00 PST = 17:00 UTC
            Integer result = timezoneService.convertLocalToUtc("09:00", -28800);

            assertThat(result).isEqualTo(1700);
        }

        @Test
        @DisplayName("should convert EST time to UTC")
        void shouldConvertEstToUtc() {
            // EST is UTC-5 (-18000 seconds)
            // 09:00 EST = 14:00 UTC
            Integer result = timezoneService.convertLocalToUtc("09:00", -18000);

            assertThat(result).isEqualTo(1400);
        }

        @Test
        @DisplayName("should convert UTC time correctly")
        void shouldConvertUtcTime() {
            // UTC offset is 0
            Integer result = timezoneService.convertLocalToUtc("09:00", 0);

            assertThat(result).isEqualTo(900);
        }

        @Test
        @DisplayName("should convert positive timezone offset")
        void shouldConvertPositiveOffset() {
            // CET is UTC+1 (3600 seconds)
            // 09:00 CET = 08:00 UTC
            Integer result = timezoneService.convertLocalToUtc("09:00", 3600);

            assertThat(result).isEqualTo(800);
        }

        @Test
        @DisplayName("should handle midnight wrap-around going backward")
        void shouldHandleMidnightWrapBackward() {
            // EST is UTC-5
            // 02:00 EST = 07:00 UTC
            Integer result = timezoneService.convertLocalToUtc("02:00", -18000);

            assertThat(result).isEqualTo(700);
        }

        @Test
        @DisplayName("should handle midnight wrap-around going forward")
        void shouldHandleMidnightWrapForward() {
            // JST is UTC+9 (32400 seconds)
            // 02:00 JST = 17:00 UTC (previous day)
            Integer result = timezoneService.convertLocalToUtc("02:00", 32400);

            assertThat(result).isEqualTo(1700);
        }

        @Test
        @DisplayName("should return null for null local time")
        void shouldReturnNullForNullLocalTime() {
            Integer result = timezoneService.convertLocalToUtc(null, 0);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return null for null timezone offset")
        void shouldReturnNullForNullOffset() {
            Integer result = timezoneService.convertLocalToUtc("09:00", null);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return null for invalid time format")
        void shouldReturnNullForInvalidTimeFormat() {
            Integer result = timezoneService.convertLocalToUtc("invalid", 0);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should handle time with minutes")
        void shouldHandleTimeWithMinutes() {
            // UTC offset is 0
            Integer result = timezoneService.convertLocalToUtc("09:30", 0);

            assertThat(result).isEqualTo(930);
        }
    }

    @Nested
    @DisplayName("convertUtcToLocal")
    class ConvertUtcToLocalTests {

        @Test
        @DisplayName("should convert UTC to PST")
        void shouldConvertUtcToPst() {
            // PST is UTC-8
            // 17:00 UTC = 09:00 PST
            String result = timezoneService.convertUtcToLocal(1700, -28800);

            assertThat(result).isEqualTo("09:00");
        }

        @Test
        @DisplayName("should convert UTC to EST")
        void shouldConvertUtcToEst() {
            // EST is UTC-5
            // 14:00 UTC = 09:00 EST
            String result = timezoneService.convertUtcToLocal(1400, -18000);

            assertThat(result).isEqualTo("09:00");
        }

        @Test
        @DisplayName("should convert UTC to CET")
        void shouldConvertUtcToCet() {
            // CET is UTC+1
            // 08:00 UTC = 09:00 CET
            String result = timezoneService.convertUtcToLocal(800, 3600);

            assertThat(result).isEqualTo("09:00");
        }

        @Test
        @DisplayName("should handle midnight wrap-around")
        void shouldHandleMidnightWrap() {
            // EST is UTC-5
            // 03:00 UTC = 22:00 EST (previous day)
            String result = timezoneService.convertUtcToLocal(300, -18000);

            assertThat(result).isEqualTo("22:00");
        }

        @Test
        @DisplayName("should return null for null UTC time")
        void shouldReturnNullForNullUtcTime() {
            String result = timezoneService.convertUtcToLocal(null, 0);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should return null for null offset")
        void shouldReturnNullForNullOffset() {
            String result = timezoneService.convertUtcToLocal(900, null);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("should handle minutes correctly")
        void shouldHandleMinutesCorrectly() {
            String result = timezoneService.convertUtcToLocal(930, 0);

            assertThat(result).isEqualTo("09:30");
        }
    }

    @Nested
    @DisplayName("isWithinWorkingHours")
    class IsWithinWorkingHoursTests {

        @Test
        @DisplayName("should return true when within normal working hours")
        void shouldReturnTrueWhenWithinHours() {
            // Get current UTC time
            int currentUtc = timezoneService.getCurrentUtcTimeHHMM();

            // Set working hours to span current time
            int start = (currentUtc - 100 + 2400) % 2400;
            int end = (currentUtc + 100) % 2400;

            // Handle edge case where start > end (wrap around midnight)
            if (start < end) {
                boolean result = timezoneService.isWithinWorkingHours(start, end);
                assertThat(result).isTrue();
            }
        }

        @Test
        @DisplayName("should return true when null hours (24/7 mode)")
        void shouldReturnTrueWhenNullHours() {
            boolean result = timezoneService.isWithinWorkingHours(null, null);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return true when only start is null")
        void shouldReturnTrueWhenStartNull() {
            boolean result = timezoneService.isWithinWorkingHours(null, 1700);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return true when only end is null")
        void shouldReturnTrueWhenEndNull() {
            boolean result = timezoneService.isWithinWorkingHours(900, null);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should handle wrap-around hours (overnight shift)")
        void shouldHandleWrapAroundHours() {
            // Test night shift: 22:00 to 06:00
            int currentUtc = timezoneService.getCurrentUtcTimeHHMM();

            // If current time is 23:00 (2300), should be within 22:00-06:00
            if (currentUtc >= 2200 || currentUtc <= 600) {
                boolean result = timezoneService.isWithinWorkingHours(2200, 600);
                assertThat(result).isTrue();
            } else {
                boolean result = timezoneService.isWithinWorkingHours(2200, 600);
                assertThat(result).isFalse();
            }
        }
    }

    @Nested
    @DisplayName("isOutsideWorkingHours")
    class IsOutsideWorkingHoursTests {

        @Test
        @DisplayName("should be inverse of isWithinWorkingHours")
        void shouldBeInverseOfIsWithinWorkingHours() {
            int currentUtc = timezoneService.getCurrentUtcTimeHHMM();
            int start = (currentUtc - 100 + 2400) % 2400;
            int end = (currentUtc + 100) % 2400;

            // Only test when we can guarantee the hours don't wrap
            if (start < end) {
                boolean within = timezoneService.isWithinWorkingHours(start, end);
                boolean outside = timezoneService.isOutsideWorkingHours(start, end);

                assertThat(within).isNotEqualTo(outside);
            }
        }

        @Test
        @DisplayName("should return false for null hours (24/7 mode)")
        void shouldReturnFalseForNullHours() {
            boolean result = timezoneService.isOutsideWorkingHours(null, null);

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("areValidWorkingHours")
    class AreValidWorkingHoursTests {

        @Test
        @DisplayName("should return true for different start and end")
        void shouldReturnTrueForDifferentTimes() {
            boolean result = timezoneService.areValidWorkingHours(900, 1700);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false for same start and end")
        void shouldReturnFalseForSameTimes() {
            boolean result = timezoneService.areValidWorkingHours(900, 900);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false for null start")
        void shouldReturnFalseForNullStart() {
            boolean result = timezoneService.areValidWorkingHours(null, 1700);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false for null end")
        void shouldReturnFalseForNullEnd() {
            boolean result = timezoneService.areValidWorkingHours(900, null);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return true for overnight hours")
        void shouldReturnTrueForOvernightHours() {
            // 22:00 to 06:00 (night shift)
            boolean result = timezoneService.areValidWorkingHours(2200, 600);

            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("getCurrentUtcTimeHHMM")
    class GetCurrentUtcTimeHHMMTests {

        @Test
        @DisplayName("should return valid HHMM format")
        void shouldReturnValidHHMMFormat() {
            int result = timezoneService.getCurrentUtcTimeHHMM();

            assertThat(result).isGreaterThanOrEqualTo(0);
            assertThat(result).isLessThan(2400);

            // Minutes should be 0-59
            int minutes = result % 100;
            assertThat(minutes).isGreaterThanOrEqualTo(0);
            assertThat(minutes).isLessThan(60);
        }
    }
}
