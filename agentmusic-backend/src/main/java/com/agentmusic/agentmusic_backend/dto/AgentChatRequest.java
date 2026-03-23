package com.agentmusic.agentmusic_backend.dto;

public record AgentChatRequest(
        String userId,
        String message,
        boolean voiceInput
) {
}

