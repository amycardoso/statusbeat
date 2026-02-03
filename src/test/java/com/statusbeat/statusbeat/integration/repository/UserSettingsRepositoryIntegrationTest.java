package com.statusbeat.statusbeat.integration.repository;

import com.statusbeat.statusbeat.model.User;
import com.statusbeat.statusbeat.model.UserSettings;
import com.statusbeat.statusbeat.testutil.IntegrationTestBase;
import com.statusbeat.statusbeat.testutil.TestDataFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserSettingsRepository Integration Tests")
class UserSettingsRepositoryIntegrationTest extends IntegrationTestBase {

    @Nested
    @DisplayName("save")
    class SaveTests {

        @Test
        @DisplayName("should save settings with generated ID")
        void shouldSaveSettingsWithGeneratedId() {
            User user = userRepository.save(TestDataFactory.createUser());
            UserSettings settings = TestDataFactory.createUserSettings(user.getId());
            settings.setId(null);

            UserSettings saved = userSettingsRepository.save(settings);

            assertThat(saved.getId()).isNotNull();
        }

        @Test
        @DisplayName("should save settings with all fields")
        void shouldSaveSettingsWithAllFields() {
            User user = userRepository.save(TestDataFactory.createUser());
            UserSettings settings = TestDataFactory.createUserSettingsWithWorkingHours(user.getId());
            settings.setAllowedDeviceIds(List.of("device-1", "device-2"));

            UserSettings saved = userSettingsRepository.save(settings);

            assertThat(saved.getUserId()).isEqualTo(user.getId());
            assertThat(saved.isSyncEnabled()).isTrue();
            assertThat(saved.isWorkingHoursEnabled()).isTrue();
            assertThat(saved.getSyncStartHour()).isEqualTo(900);
            assertThat(saved.getSyncEndHour()).isEqualTo(1700);
            assertThat(saved.getAllowedDeviceIds()).containsExactly("device-1", "device-2");
        }

        @Test
        @DisplayName("should save settings with default values")
        void shouldSaveSettingsWithDefaultValues() {
            User user = userRepository.save(TestDataFactory.createUser());
            UserSettings settings = TestDataFactory.createUserSettings(user.getId());

            UserSettings saved = userSettingsRepository.save(settings);

            assertThat(saved.isSyncEnabled()).isTrue();
            assertThat(saved.isSyncActive()).isFalse();
            assertThat(saved.getDefaultEmoji()).isEqualTo(":musical_note:");
            assertThat(saved.isShowArtist()).isTrue();
            assertThat(saved.isShowSongTitle()).isTrue();
            assertThat(saved.isWorkingHoursEnabled()).isFalse();
        }

        @Test
        @DisplayName("should update existing settings")
        void shouldUpdateExistingSettings() {
            User user = userRepository.save(TestDataFactory.createUser());
            UserSettings settings = TestDataFactory.createUserSettings(user.getId());
            UserSettings saved = userSettingsRepository.save(settings);

            saved.setDefaultEmoji(":headphones:");
            saved.setSyncActive(true);
            UserSettings updated = userSettingsRepository.save(saved);

            assertThat(updated.getId()).isEqualTo(saved.getId());
            assertThat(updated.getDefaultEmoji()).isEqualTo(":headphones:");
            assertThat(updated.isSyncActive()).isTrue();
        }
    }

    @Nested
    @DisplayName("findByUserId")
    class FindByUserIdTests {

        @Test
        @DisplayName("should find settings by user ID")
        void shouldFindSettingsByUserId() {
            User user = userRepository.save(TestDataFactory.createUser());
            UserSettings settings = TestDataFactory.createUserSettings(user.getId());
            userSettingsRepository.save(settings);

            Optional<UserSettings> found = userSettingsRepository.findByUserId(user.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getUserId()).isEqualTo(user.getId());
        }

        @Test
        @DisplayName("should return empty when user ID not found")
        void shouldReturnEmptyWhenNotFound() {
            Optional<UserSettings> found = userSettingsRepository.findByUserId("NONEXISTENT");

            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("existsByUserId")
    class ExistsByUserIdTests {

        @Test
        @DisplayName("should return true when settings exist")
        void shouldReturnTrueWhenSettingsExist() {
            User user = userRepository.save(TestDataFactory.createUser());
            UserSettings settings = TestDataFactory.createUserSettings(user.getId());
            userSettingsRepository.save(settings);

            boolean exists = userSettingsRepository.existsByUserId(user.getId());

            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("should return false when settings do not exist")
        void shouldReturnFalseWhenSettingsDoNotExist() {
            boolean exists = userSettingsRepository.existsByUserId("NONEXISTENT");

            assertThat(exists).isFalse();
        }
    }

    @Nested
    @DisplayName("deleteByUserId")
    class DeleteByUserIdTests {

        @Test
        @DisplayName("should delete settings by user ID")
        void shouldDeleteSettingsByUserId() {
            User user = userRepository.save(TestDataFactory.createUser());
            UserSettings settings = TestDataFactory.createUserSettings(user.getId());
            userSettingsRepository.save(settings);

            userSettingsRepository.deleteByUserId(user.getId());

            Optional<UserSettings> found = userSettingsRepository.findByUserId(user.getId());
            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("should not throw when deleting non-existent settings")
        void shouldNotThrowWhenDeletingNonExistent() {
            Assertions.assertDoesNotThrow(() -> {
                userSettingsRepository.deleteByUserId("NONEXISTENT");
            });
        }
    }

    @Nested
    @DisplayName("working hours configuration")
    class WorkingHoursTests {

        @Test
        @DisplayName("should persist working hours settings")
        void shouldPersistWorkingHoursSettings() {
            User user = userRepository.save(TestDataFactory.createUser());
            UserSettings settings = TestDataFactory.createUserSettings(user.getId());
            settings.setWorkingHoursEnabled(true);
            settings.setSyncStartHour(900);
            settings.setSyncEndHour(1700);
            settings.setTimezoneOffsetSeconds(-18000);

            UserSettings saved = userSettingsRepository.save(settings);
            Optional<UserSettings> found = userSettingsRepository.findByUserId(user.getId());

            assertThat(found).isPresent();
            assertThat(found.get().isWorkingHoursEnabled()).isTrue();
            assertThat(found.get().getSyncStartHour()).isEqualTo(900);
            assertThat(found.get().getSyncEndHour()).isEqualTo(1700);
            assertThat(found.get().getTimezoneOffsetSeconds()).isEqualTo(-18000);
        }

        @Test
        @DisplayName("should allow null working hours when disabled")
        void shouldAllowNullWorkingHoursWhenDisabled() {
            User user = userRepository.save(TestDataFactory.createUser());
            UserSettings settings = TestDataFactory.createUserSettings(user.getId());
            settings.setWorkingHoursEnabled(false);
            settings.setSyncStartHour(null);
            settings.setSyncEndHour(null);

            UserSettings saved = userSettingsRepository.save(settings);

            assertThat(saved.getSyncStartHour()).isNull();
            assertThat(saved.getSyncEndHour()).isNull();
        }
    }

    @Nested
    @DisplayName("device filtering configuration")
    class DeviceFilteringTests {

        @Test
        @DisplayName("should persist allowed device IDs")
        void shouldPersistAllowedDeviceIds() {
            User user = userRepository.save(TestDataFactory.createUser());
            UserSettings settings = TestDataFactory.createUserSettings(user.getId());
            settings.setAllowedDeviceIds(List.of("device-1", "device-2", "device-3"));

            userSettingsRepository.save(settings);
            Optional<UserSettings> found = userSettingsRepository.findByUserId(user.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getAllowedDeviceIds())
                    .containsExactly("device-1", "device-2", "device-3");
        }

        @Test
        @DisplayName("should allow null device list (all devices)")
        void shouldAllowNullDeviceList() {
            User user = userRepository.save(TestDataFactory.createUser());
            UserSettings settings = TestDataFactory.createUserSettings(user.getId());
            settings.setAllowedDeviceIds(null);

            userSettingsRepository.save(settings);
            Optional<UserSettings> found = userSettingsRepository.findByUserId(user.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getAllowedDeviceIds()).isNull();
        }

        @Test
        @DisplayName("should allow empty device list")
        void shouldAllowEmptyDeviceList() {
            User user = userRepository.save(TestDataFactory.createUser());
            UserSettings settings = TestDataFactory.createUserSettings(user.getId());
            settings.setAllowedDeviceIds(List.of());

            userSettingsRepository.save(settings);
            Optional<UserSettings> found = userSettingsRepository.findByUserId(user.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getAllowedDeviceIds()).isEmpty();
        }
    }

    @Nested
    @DisplayName("status template configuration")
    class StatusTemplateTests {

        @Test
        @DisplayName("should persist status template")
        void shouldPersistStatusTemplate() {
            User user = userRepository.save(TestDataFactory.createUser());
            UserSettings settings = TestDataFactory.createUserSettings(user.getId());
            settings.setStatusTemplate("{title} by {artist}");

            userSettingsRepository.save(settings);
            Optional<UserSettings> found = userSettingsRepository.findByUserId(user.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getStatusTemplate()).isEqualTo("{title} by {artist}");
        }
    }
}
