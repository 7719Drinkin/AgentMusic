package com.agentmusic.agentmusic_backend.persistence.mybatis.model;

import java.time.LocalDateTime;

public record ChatMessageRecord(
        String id,
        String userId,
        String message,
        String role,
        String metadata,
        LocalDateTime createdAt
) {
}
