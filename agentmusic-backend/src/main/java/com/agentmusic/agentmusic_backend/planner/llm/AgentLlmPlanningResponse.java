package com.agentmusic.agentmusic_backend.planner.llm;

import com.agentmusic.agentmusic_backend.planner.AgentIntent;
import java.util.List;

public record AgentLlmPlanningResponse(
        String schemaVersion,
        AgentIntent intent,
        String summary,
        String reasoning,
        int confidence,
        List<AgentLlmPlanningStep> steps
) {
}
