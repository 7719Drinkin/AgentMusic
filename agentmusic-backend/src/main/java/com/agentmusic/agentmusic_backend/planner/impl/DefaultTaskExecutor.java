package com.agentmusic.agentmusic_backend.planner.impl;

import com.agentmusic.agentmusic_backend.planner.AgentPlan;
import com.agentmusic.agentmusic_backend.planner.PlannerExecutionResult;
import com.agentmusic.agentmusic_backend.planner.PlanningContext;
import com.agentmusic.agentmusic_backend.planner.TaskExecutor;
import org.springframework.stereotype.Component;

@Component
public class DefaultTaskExecutor implements TaskExecutor {

    @Override
    public PlannerExecutionResult execute(AgentPlan plan, PlanningContext planningContext) {
        String reply = "Planner skeleton ready. Intent=" + plan.intent()
                + ", steps=" + plan.steps().size()
                + ". Spotify bridge-mode execution wiring is the next implementation step.";
        return new PlannerExecutionResult(plan, reply);
    }
}

