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
@Document(collection = "oauth_states")
public class OAuthState {

    @Id
    private String id;

    @Indexed(unique = true)
    private String state;

    @Indexed(expireAfter = "0s")
    private LocalDateTime expiresAt;
}
