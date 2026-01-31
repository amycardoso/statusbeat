package com.statusbeat.statusbeat.service;

import com.slack.api.bolt.service.OAuthStateService;
import com.statusbeat.statusbeat.model.OAuthState;
import com.statusbeat.statusbeat.repository.OAuthStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class MongoDBOAuthStateService implements OAuthStateService {

    private final OAuthStateRepository oauthStateRepository;

    @Override
    public void addNewStateToDatastore(String state) throws Exception {
        LocalDateTime expiryTime = LocalDateTime.now().plusMinutes(10);

        OAuthState oauthState = OAuthState.builder()
                .state(state)
                .expiresAt(expiryTime)
                .build();

        oauthStateRepository.save(oauthState);
        log.info("OAuth state stored: {} (expires: {})", state, expiryTime);
    }

    @Override
    public boolean isAvailableInDatabase(String state) {
        boolean exists = oauthStateRepository.existsByState(state);
        if (!exists) {
            log.warn("OAuth state not found or expired: {}", state);
        }
        return exists;
    }

    @Override
    public void deleteStateFromDatastore(String state) throws Exception {
        oauthStateRepository.deleteByState(state);
    }
}
