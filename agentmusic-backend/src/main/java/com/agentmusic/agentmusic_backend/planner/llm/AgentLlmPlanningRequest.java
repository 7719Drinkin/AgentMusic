package com.agentmusic.agentmusic_backend.planner.llm;

import java.util.List;

public record AgentLlmPlanningRequest(
        String schemaVersion,
        String userId,
        boolean voiceInput,
        String latestUserMessage,
        List<String> recentMessages,
        List<String> allowedIntents,
        List<String> allowedStepTypes
) {
}
