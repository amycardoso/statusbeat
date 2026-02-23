package com.statusbeat.statusbeat.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "bot_installations")
public class BotInstallation {

    @Id
    private String id;

    @Indexed(unique = true)
    private String teamId;

    private String encryptedBotToken;

    private String botUserId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
