package com.agentmusic.agentmusic_backend.planner.impl;

import com.agentmusic.agentmusic_backend.config.AgentChatProperties;
import com.agentmusic.agentmusic_backend.planner.PlanningContext;
import com.agentmusic.agentmusic_backend.planner.TaskPlanner;
import com.agentmusic.agentmusic_backend.planner.TaskPlanningResult;
import com.agentmusic.agentmusic_backend.planner.llm.OpenAiCompatiblePlanningClient;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class LlmBackedTaskPlanner implements TaskPlanner {

    public static final String LLM_SOURCE = "llm-harness";
    public static final String FALLBACK_SOURCE = "simple-task-planner-fallback";

    private final AgentChatProperties agentChatProperties;
    private final OpenAiCompatiblePlanningClient planningClient;
    private final SimpleTaskPlanner fallbackPlanner;

    public LlmBackedTaskPlanner(
            AgentChatProperties agentChatProperties,
            OpenAiCompatiblePlanningClient planningClient,
            SimpleTaskPlanner fallbackPlanner
    ) {
        this.agentChatProperties = agentChatProperties;
        this.planningClient = planningClient;
        this.fallbackPlanner = fallbackPlanner;
    }

    @Override
    public TaskPlanningResult createPlan(PlanningContext planningContext) {
        if (!isLivePlanningEnabled()) {
            return fallbackPlanner.createPlan(planningContext);
        }

        try {
            var result = planningClient.generateValidatedPlan(planningContext);
            return new TaskPlanningResult(result.plan(), LLM_SOURCE, false);
        } catch (RuntimeException exception) {
            TaskPlanningResult fallback = fallbackPlanner.createPlan(planningContext);
            return new TaskPlanningResult(fallback.plan(), FALLBACK_SOURCE, true);
        }
    }

    private boolean isLivePlanningEnabled() {
        return agentChatProperties.liveLlmEnabled() && planningClient.isEnabled();
    }
}
