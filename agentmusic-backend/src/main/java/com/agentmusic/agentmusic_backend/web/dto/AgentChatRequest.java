package com.agentmusic.agentmusic_backend.web.dto;

public record AgentChatRequest(
        String userId,
        String message,
        boolean voiceInput
) {
}

