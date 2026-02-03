package com.statusbeat.statusbeat.unit.service;

import com.statusbeat.statusbeat.model.User;
import com.statusbeat.statusbeat.model.UserSettings;
import com.statusbeat.statusbeat.repository.UserRepository;
import com.statusbeat.statusbeat.repository.UserSettingsRepository;
import com.statusbeat.statusbeat.service.UserService;
import com.statusbeat.statusbeat.testutil.TestBase;
import com.statusbeat.statusbeat.testutil.TestDataFactory;
import com.statusbeat.statusbeat.util.EncryptionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("UserService")
class UserServiceTest extends TestBase {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserSettingsRepository userSettingsRepository;

    @Mock
    private EncryptionUtil encryptionUtil;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, userSettingsRepository, encryptionUtil);
    }

    @Nested
    @DisplayName("findBySlackUserId")
    class FindBySlackUserIdTests {

        @Test
        @DisplayName("should return user when found")
        void shouldReturnUserWhenFound() {
            User user = TestDataFactory.createUser("U12345");
            when(userRepository.findBySlackUserId("U12345")).thenReturn(Optional.of(user));

            Optional<User> result = userService.findBySlackUserId("U12345");

            assertThat(result).isPresent();
            assertThat(result.get().getSlackUserId()).isEqualTo("U12345");
        }

        @Test
        @DisplayName("should return empty when not found")
        void shouldReturnEmptyWhenNotFound() {
            when(userRepository.findBySlackUserId("U99999")).thenReturn(Optional.empty());

            Optional<User> result = userService.findBySlackUserId("U99999");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findAllActiveUsers")
    class FindAllActiveUsersTests {

        @Test
        @DisplayName("should return list of active users")
        void shouldReturnActiveUsers() {
            List<User> activeUsers = List.of(
                    TestDataFactory.createUser("U1"),
                    TestDataFactory.createUser("U2")
            );
            when(userRepository.findByActiveTrue()).thenReturn(activeUsers);

            List<User> result = userService.findAllActiveUsers();

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("should return empty list when no active users")
        void shouldReturnEmptyListWhenNoActiveUsers() {
            when(userRepository.findByActiveTrue()).thenReturn(List.of());

            List<User> result = userService.findAllActiveUsers();

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("createOrUpdateUser")
    class CreateOrUpdateUserTests {

        @Test
        @DisplayName("should create new user when not exists")
        void shouldCreateNewUser() {
            when(userRepository.findBySlackUserId("U12345")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
            when(userSettingsRepository.save(any(UserSettings.class))).thenAnswer(inv -> inv.getArgument(0));

            User result = userService.createOrUpdateUser("U12345", "T12345", "xoxp-token");

            assertThat(result.getSlackUserId()).isEqualTo("U12345");
            assertThat(result.getSlackTeamId()).isEqualTo("T12345");
            assertThat(result.isActive()).isTrue();

            verify(userSettingsRepository).save(any(UserSettings.class));
        }

        @Test
        @DisplayName("should update existing user")
        void shouldUpdateExistingUser() {
            User existingUser = TestDataFactory.createUser("U12345");
            existingUser.setEncryptedSlackAccessToken("old-encrypted-token");
            when(userRepository.findBySlackUserId("U12345")).thenReturn(Optional.of(existingUser));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
            when(encryptionUtil.encrypt("new-token")).thenReturn("new-encrypted-token");

            User result = userService.createOrUpdateUser("U12345", "T12345", "new-token");

            assertThat(result.getEncryptedSlackAccessToken()).isEqualTo("new-encrypted-token");
            assertThat(result.isActive()).isTrue();
            verify(encryptionUtil).encrypt("new-token");
            verify(userSettingsRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("updateSpotifyTokens")
    class UpdateSpotifyTokensTests {

        @Test
        @DisplayName("should encrypt and save Spotify tokens")
        void shouldEncryptAndSaveTokens() {
            User user = TestDataFactory.createUser();
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
            when(encryptionUtil.encrypt("access-token")).thenReturn("encrypted-access");
            when(encryptionUtil.encrypt("refresh-token")).thenReturn("encrypted-refresh");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            User result = userService.updateSpotifyTokens(
                    user.getId(), "spotify-123", "access-token", "refresh-token", 3600);

            assertThat(result.getSpotifyUserId()).isEqualTo("spotify-123");
            assertThat(result.getEncryptedSpotifyAccessToken()).isEqualTo("encrypted-access");
            assertThat(result.getEncryptedSpotifyRefreshToken()).isEqualTo("encrypted-refresh");
            assertThat(result.getSpotifyTokenExpiresAt()).isAfter(LocalDateTime.now());
        }

        @Test
        @DisplayName("should throw when user not found")
        void shouldThrowWhenUserNotFound() {
            when(userRepository.findById("nonexistent")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.updateSpotifyTokens(
                    "nonexistent", "spotify-123", "access", "refresh", 3600))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("User not found");
        }
    }

    @Nested
    @DisplayName("getDecryptedSpotifyAccessToken")
    class GetDecryptedSpotifyAccessTokenTests {

        @Test
        @DisplayName("should decrypt access token")
        void shouldDecryptAccessToken() {
            User user = TestDataFactory.createUserWithSpotify();
            when(encryptionUtil.decrypt(user.getEncryptedSpotifyAccessToken())).thenReturn("decrypted-token");

            String result = userService.getDecryptedSpotifyAccessToken(user);

            assertThat(result).isEqualTo("decrypted-token");
        }

        @Test
        @DisplayName("should return null when no token")
        void shouldReturnNullWhenNoToken() {
            User user = TestDataFactory.createUser();
            user.setEncryptedSpotifyAccessToken(null);

            String result = userService.getDecryptedSpotifyAccessToken(user);

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("isSpotifyTokenExpired")
    class IsSpotifyTokenExpiredTests {

        @Test
        @DisplayName("should return true when token expires within 5 minutes")
        void shouldReturnTrueWhenTokenExpiresSoon() {
            User user = TestDataFactory.createUserWithSpotify();
            user.setSpotifyTokenExpiresAt(LocalDateTime.now().plusMinutes(3));

            boolean result = userService.isSpotifyTokenExpired(user);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when token has more than 5 minutes")
        void shouldReturnFalseWhenTokenValid() {
            User user = TestDataFactory.createUserWithSpotify();
            user.setSpotifyTokenExpiresAt(LocalDateTime.now().plusMinutes(30));

            boolean result = userService.isSpotifyTokenExpired(user);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return true when expiry is null")
        void shouldReturnTrueWhenExpiryNull() {
            User user = TestDataFactory.createUser();
            user.setSpotifyTokenExpiresAt(null);

            boolean result = userService.isSpotifyTokenExpired(user);

            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("updateCurrentlyPlaying")
    class UpdateCurrentlyPlayingTests {

        @Test
        @DisplayName("should update currently playing info")
        void shouldUpdateCurrentlyPlayingInfo() {
            User user = TestDataFactory.createUser();
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            userService.updateCurrentlyPlaying(user.getId(), "track-1", "Song Title", "Artist");

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());

            User saved = captor.getValue();
            assertThat(saved.getCurrentlyPlayingSongId()).isEqualTo("track-1");
            assertThat(saved.getCurrentlyPlayingSongTitle()).isEqualTo("Song Title");
            assertThat(saved.getCurrentlyPlayingArtist()).isEqualTo("Artist");
            assertThat(saved.getLastSyncedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("clearCurrentlyPlaying")
    class ClearCurrentlyPlayingTests {

        @Test
        @DisplayName("should clear currently playing info")
        void shouldClearCurrentlyPlayingInfo() {
            User user = TestDataFactory.createUserWithCurrentlyPlaying();
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            userService.clearCurrentlyPlaying(user.getId());

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());

            User saved = captor.getValue();
            assertThat(saved.getCurrentlyPlayingSongId()).isNull();
            assertThat(saved.getCurrentlyPlayingSongTitle()).isNull();
            assertThat(saved.getCurrentlyPlayingArtist()).isNull();
        }
    }

    @Nested
    @DisplayName("startSync/stopSync")
    class SyncControlTests {

        @Test
        @DisplayName("should start sync and clear manual status flag")
        void shouldStartSync() {
            User user = TestDataFactory.createUserWithManualStatus();
            UserSettings settings = TestDataFactory.createUserSettings(user.getId());
            when(userSettingsRepository.findByUserId(user.getId())).thenReturn(Optional.of(settings));
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
            when(userSettingsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            userService.startSync(user.getId());

            ArgumentCaptor<UserSettings> settingsCaptor = ArgumentCaptor.forClass(UserSettings.class);
            verify(userSettingsRepository).save(settingsCaptor.capture());
            assertThat(settingsCaptor.getValue().isSyncActive()).isTrue();

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            assertThat(userCaptor.getValue().isManualStatusSet()).isFalse();
            assertThat(userCaptor.getValue().isStatusCleared()).isFalse();
        }

        @Test
        @DisplayName("should stop sync")
        void shouldStopSync() {
            User user = TestDataFactory.createUser();
            UserSettings settings = TestDataFactory.createUserSettingsWithSyncActive(user.getId());
            when(userSettingsRepository.findByUserId(user.getId())).thenReturn(Optional.of(settings));
            when(userSettingsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            userService.stopSync(user.getId());

            ArgumentCaptor<UserSettings> captor = ArgumentCaptor.forClass(UserSettings.class);
            verify(userSettingsRepository).save(captor.capture());
            assertThat(captor.getValue().isSyncActive()).isFalse();
        }
    }

    @Nested
    @DisplayName("setTokenInvalidated")
    class SetTokenInvalidatedTests {

        @Test
        @DisplayName("should mark token as invalidated")
        void shouldMarkTokenInvalidated() {
            User user = TestDataFactory.createUser();
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            userService.setTokenInvalidated(user.getId(), true);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());

            User saved = captor.getValue();
            assertThat(saved.isTokenInvalidated()).isTrue();
            assertThat(saved.getTokenInvalidatedAt()).isNotNull();
            assertThat(saved.isActive()).isFalse();
        }

        @Test
        @DisplayName("should reactivate user when invalidation cleared")
        void shouldReactivateUser() {
            User user = TestDataFactory.createInvalidatedUser();
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            userService.setTokenInvalidated(user.getId(), false);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());

            User saved = captor.getValue();
            assertThat(saved.isTokenInvalidated()).isFalse();
            assertThat(saved.getTokenInvalidatedAt()).isNull();
            assertThat(saved.isActive()).isTrue();
        }
    }

    @Nested
    @DisplayName("updateWorkingHours")
    class UpdateWorkingHoursTests {

        @Test
        @DisplayName("should update working hours settings")
        void shouldUpdateWorkingHours() {
            User user = TestDataFactory.createUser();
            UserSettings settings = TestDataFactory.createUserSettings(user.getId());
            when(userSettingsRepository.findByUserId(user.getId())).thenReturn(Optional.of(settings));
            when(userSettingsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            userService.updateWorkingHours(user.getId(), 900, 1700, true);

            ArgumentCaptor<UserSettings> captor = ArgumentCaptor.forClass(UserSettings.class);
            verify(userSettingsRepository).save(captor.capture());

            UserSettings saved = captor.getValue();
            assertThat(saved.getSyncStartHour()).isEqualTo(900);
            assertThat(saved.getSyncEndHour()).isEqualTo(1700);
            assertThat(saved.isWorkingHoursEnabled()).isTrue();
        }
    }

    @Nested
    @DisplayName("updateAllowedDevices")
    class UpdateAllowedDevicesTests {

        @Test
        @DisplayName("should update allowed devices")
        void shouldUpdateAllowedDevices() {
            User user = TestDataFactory.createUser();
            UserSettings settings = TestDataFactory.createUserSettings(user.getId());
            when(userSettingsRepository.findByUserId(user.getId())).thenReturn(Optional.of(settings));
            when(userSettingsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            List<String> deviceIds = List.of("device-1", "device-2");
            userService.updateAllowedDevices(user.getId(), deviceIds);

            ArgumentCaptor<UserSettings> captor = ArgumentCaptor.forClass(UserSettings.class);
            verify(userSettingsRepository).save(captor.capture());

            assertThat(captor.getValue().getAllowedDeviceIds()).containsExactly("device-1", "device-2");
        }

        @Test
        @DisplayName("should clear allowed devices with null")
        void shouldClearAllowedDevices() {
            User user = TestDataFactory.createUser();
            UserSettings settings = TestDataFactory.createUserSettingsWithDeviceFilter(
                    user.getId(), List.of("device-1"));
            when(userSettingsRepository.findByUserId(user.getId())).thenReturn(Optional.of(settings));
            when(userSettingsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            userService.updateAllowedDevices(user.getId(), null);

            ArgumentCaptor<UserSettings> captor = ArgumentCaptor.forClass(UserSettings.class);
            verify(userSettingsRepository).save(captor.capture());

            assertThat(captor.getValue().getAllowedDeviceIds()).isNull();
        }
    }

    @Nested
    @DisplayName("updateDefaultEmoji")
    class UpdateDefaultEmojiTests {

        @Test
        @DisplayName("should update default emoji")
        void shouldUpdateDefaultEmoji() {
            User user = TestDataFactory.createUser();
            UserSettings settings = TestDataFactory.createUserSettings(user.getId());
            when(userSettingsRepository.findByUserId(user.getId())).thenReturn(Optional.of(settings));
            when(userSettingsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            userService.updateDefaultEmoji(user.getId(), ":headphones:");

            ArgumentCaptor<UserSettings> captor = ArgumentCaptor.forClass(UserSettings.class);
            verify(userSettingsRepository).save(captor.capture());

            assertThat(captor.getValue().getDefaultEmoji()).isEqualTo(":headphones:");
        }
    }
}
