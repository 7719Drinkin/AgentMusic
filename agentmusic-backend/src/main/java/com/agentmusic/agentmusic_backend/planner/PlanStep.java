package com.agentmusic.agentmusic_backend.planner;

import java.util.Map;

public record PlanStep(
        int order,
        PlanStepType type,
        Map<String, Object> arguments
) {
}

