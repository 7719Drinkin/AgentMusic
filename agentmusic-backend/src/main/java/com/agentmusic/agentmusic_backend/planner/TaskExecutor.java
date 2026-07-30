package com.agentmusic.agentmusic_backend.planner;

public interface TaskExecutor {

    PlannerExecutionResult execute(AgentPlan plan, PlanningContext planningContext);
}

