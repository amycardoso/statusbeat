package com.statusbeat.statusbeat.repository;

import com.statusbeat.statusbeat.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findBySlackUserId(String slackUserId);

    Optional<User> findBySlackTeamId(String slackTeamId);

    Optional<User> findBySpotifyUserId(String spotifyUserId);

    List<User> findByActiveTrue();

    boolean existsBySlackUserId(String slackUserId);
}
