package com.agentmusic.agentmusic_backend.domain;

import java.time.LocalDateTime;
import java.util.Map;

public record ChatMessage(
        String id,
        String userId,
        String message,
        ChatRole role,
        Map<String, Object> metadata,
        LocalDateTime createdAt
) {
}

