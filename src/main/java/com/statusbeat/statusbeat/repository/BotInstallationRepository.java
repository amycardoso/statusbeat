package com.statusbeat.statusbeat.repository;

import com.statusbeat.statusbeat.model.BotInstallation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BotInstallationRepository extends MongoRepository<BotInstallation, String> {

    Optional<BotInstallation> findByTeamId(String teamId);

    void deleteByTeamId(String teamId);
}
