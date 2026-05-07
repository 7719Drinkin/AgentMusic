package com.agentmusic.agentmusic_backend.planner;

import java.util.Objects;

public record TaskPlanningResult(
        AgentPlan plan,
        String source,
        boolean fallbackUsed,
        String fallbackReason
) {

    public TaskPlanningResult {
        Objects.requireNonNull(plan, "plan must not be null");
        source = Objects.requireNonNullElse(source, "unknown");
        fallbackReason = fallbackReason == null || fallbackReason.isBlank()
                ? null
                : fallbackReason.trim();
    }

    public TaskPlanningResult(AgentPlan plan, String source, boolean fallbackUsed) {
        this(plan, source, fallbackUsed, null);
    }
}
