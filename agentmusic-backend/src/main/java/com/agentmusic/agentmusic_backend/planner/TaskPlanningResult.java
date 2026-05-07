package com.agentmusic.agentmusic_backend.planner;

import java.util.Objects;

public record TaskPlanningResult(
        AgentPlan plan,
        String source,
        boolean fallbackUsed
) {

    public TaskPlanningResult {
        Objects.requireNonNull(plan, "plan must not be null");
        source = Objects.requireNonNullElse(source, "unknown");
    }
}
