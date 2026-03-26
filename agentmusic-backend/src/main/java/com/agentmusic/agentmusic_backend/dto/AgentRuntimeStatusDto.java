package com.agentmusic.agentmusic_backend.dto;

public record AgentRuntimeStatusDto(
        boolean liveLlmEnabledConfigured,
        boolean openAiKeyPresent,
        String openAiModelId,
        boolean liveLlmAvailable
) {
}
