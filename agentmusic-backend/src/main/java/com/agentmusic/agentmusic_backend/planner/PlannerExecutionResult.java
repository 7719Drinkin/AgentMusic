package com.agentmusic.agentmusic_backend.planner;

public record PlannerExecutionResult(
        AgentPlan plan,
        String replyMessage
) {
}

