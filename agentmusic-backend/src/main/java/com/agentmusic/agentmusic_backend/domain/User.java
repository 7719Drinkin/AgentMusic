package com.agentmusic.agentmusic_backend.domain;

import java.time.LocalDateTime;

public record User(
        String id,
        String username,
        String email,
        String passwordHash,
        UserPreferences preferences,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

