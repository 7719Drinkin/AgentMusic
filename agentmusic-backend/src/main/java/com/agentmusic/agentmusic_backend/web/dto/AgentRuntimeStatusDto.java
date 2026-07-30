package com.agentmusic.agentmusic_backend.web.dto;

public record AgentRuntimeStatusDto(
        boolean liveLlmEnabledConfigured,
        boolean openAiKeyPresent,
        String openAiModelId,
        boolean liveLlmAvailable
) {
}
