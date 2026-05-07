package com.agentmusic.agentmusic_backend.planner.llm;

import com.agentmusic.agentmusic_backend.planner.PlanStepType;
import java.util.Map;

public record AgentLlmPlanningStep(
        PlanStepType type,
        Map<String, Object> arguments
) {
}
