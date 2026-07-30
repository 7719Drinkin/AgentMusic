package com.agentmusic.agentmusic_backend.persistence.mybatis.model;

import java.time.LocalDateTime;

public record UserRecord(
        String id,
        String username,
        String email,
        String passwordHash,
        String preferences,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
