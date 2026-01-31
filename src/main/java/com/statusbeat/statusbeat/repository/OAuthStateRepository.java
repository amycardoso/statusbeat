package com.statusbeat.statusbeat.repository;

import com.statusbeat.statusbeat.model.OAuthState;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for OAuth state parameters.
 * Used by MongoDBOAuthStateService for CSRF protection in OAuth flows.
 */
@Repository
public interface OAuthStateRepository extends MongoRepository<OAuthState, String> {

    /**
     * Find an OAuth state by the state parameter value
     */
    Optional<OAuthState> findByState(String state);

    /**
     * Delete an OAuth state by the state parameter value
     */
    void deleteByState(String state);

    /**
     * Check if a state exists
     */
    boolean existsByState(String state);
}
