package com.agentmusic.agentmusic_backend.planner.llm;

import com.agentmusic.agentmusic_backend.planner.AgentPlan;

public record AgentLlmPlanningResult(
        AgentLlmPlanningResponse response,
        AgentPlan plan
) {
}
