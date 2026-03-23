package com.agentmusic.agentmusic_backend.dto;

import com.agentmusic.agentmusic_backend.domain.ChatRole;
import java.time.LocalDateTime;
import java.util.Map;

public record ChatMessageDto(
        String id,
        ChatRole role,
        String message,
        Map<String, Object> metadata,
        LocalDateTime createdAt
) {
}

