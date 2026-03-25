package com.agentmusic.agentmusic_backend.planner;

import java.util.List;

public record AgentPlan(
        AgentIntent intent,
        String summary,
        List<PlanStep> steps
) {
}

