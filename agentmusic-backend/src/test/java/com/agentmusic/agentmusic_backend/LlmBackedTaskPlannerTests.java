package com.agentmusic.agentmusic_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentmusic.agentmusic_backend.config.AgentChatProperties;
import com.agentmusic.agentmusic_backend.planner.AgentIntent;
import com.agentmusic.agentmusic_backend.planner.AgentPlan;
import com.agentmusic.agentmusic_backend.planner.PlanStep;
import com.agentmusic.agentmusic_backend.planner.PlanStepType;
import com.agentmusic.agentmusic_backend.planner.PlanningContext;
import com.agentmusic.agentmusic_backend.planner.TaskPlanningResult;
import com.agentmusic.agentmusic_backend.planner.impl.LlmBackedTaskPlanner;
import com.agentmusic.agentmusic_backend.planner.impl.SimpleTaskPlanner;
import com.agentmusic.agentmusic_backend.planner.llm.AgentLlmPlanningResponse;
import com.agentmusic.agentmusic_backend.planner.llm.AgentLlmPlanningResult;
import com.agentmusic.agentmusic_backend.planner.llm.OpenAiCompatiblePlanningClient;
import com.agentmusic.agentmusic_backend.web.dto.AgentChatRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LlmBackedTaskPlannerTests {

    @Test
    void shouldUseValidatedLlmPlanWhenEnabled() {
        OpenAiCompatiblePlanningClient planningClient = mock(OpenAiCompatiblePlanningClient.class);
        when(planningClient.isEnabled()).thenReturn(true);

        AgentPlan llmPlan = new AgentPlan(
                AgentIntent.PLAY_RECOMMENDATION,
                "Build a recommendation playlist first, then start playback.",
                List.of(
                        new PlanStep(1, PlanStepType.READ_CHAT_CONTEXT, Map.of("limit", 20)),
                        new PlanStep(2, PlanStepType.READ_USER_PREFERENCES, Map.of()),
                        new PlanStep(3, PlanStepType.READ_PLAYLIST_HISTORY, Map.of("limit", 10)),
                        new PlanStep(4, PlanStepType.GENERATE_RECOMMENDATION_CANDIDATES, Map.of("query", "来点适合雨天通勤的中文歌并直接播放")),
                        new PlanStep(5, PlanStepType.RANK_RECOMMENDATION_CANDIDATES, Map.of("query", "来点适合雨天通勤的中文歌并直接播放")),
                        new PlanStep(6, PlanStepType.CREATE_RECOMMENDATION_PLAYLIST, Map.of("query", "来点适合雨天通勤的中文歌并直接播放")),
                        new PlanStep(7, PlanStepType.UPDATE_PLAYBACK_STATE, Map.of("query", "来点适合雨天通勤的中文歌并直接播放")),
                        new PlanStep(8, PlanStepType.PERSIST_CHAT_REPLY, Map.of())
                )
        );
        when(planningClient.generateValidatedPlan(any())).thenReturn(
                new AgentLlmPlanningResult(
                        new AgentLlmPlanningResponse(
                                "agentmusic.plan.v1",
                                AgentIntent.PLAY_RECOMMENDATION,
                                "Build a recommendation playlist first, then start playback.",
                                "The user asked for recommendation plus immediate playback.",
                                90,
                                List.of()
                        ),
                        llmPlan
                )
        );

        LlmBackedTaskPlanner planner = new LlmBackedTaskPlanner(
                enabledProperties(),
                planningClient,
                new SimpleTaskPlanner()
        );

        TaskPlanningResult result = planner.createPlan(recommendationContext());

        assertEquals(LlmBackedTaskPlanner.LLM_SOURCE, result.source());
        assertFalse(result.fallbackUsed());
        assertEquals(AgentIntent.PLAY_RECOMMENDATION, result.plan().intent());
    }

    @Test
    void shouldFallbackWhenValidatedLlmPlanningFails() {
        OpenAiCompatiblePlanningClient planningClient = mock(OpenAiCompatiblePlanningClient.class);
        when(planningClient.isEnabled()).thenReturn(true);
        doThrow(new IllegalStateException("validation failed"))
                .when(planningClient)
                .generateValidatedPlan(any());

        LlmBackedTaskPlanner planner = new LlmBackedTaskPlanner(
                enabledProperties(),
                planningClient,
                new SimpleTaskPlanner()
        );

        TaskPlanningResult result = planner.createPlan(recommendationContext());

        assertEquals(LlmBackedTaskPlanner.FALLBACK_SOURCE, result.source());
        assertTrue(result.fallbackUsed());
        assertEquals(AgentIntent.PLAY_RECOMMENDATION, result.plan().intent());
    }

    @Test
    void shouldUseSimplePlannerWhenLivePlanningIsDisabled() {
        OpenAiCompatiblePlanningClient planningClient = mock(OpenAiCompatiblePlanningClient.class);
        when(planningClient.isEnabled()).thenReturn(true);

        LlmBackedTaskPlanner planner = new LlmBackedTaskPlanner(
                disabledProperties(),
                planningClient,
                new SimpleTaskPlanner()
        );

        TaskPlanningResult result = planner.createPlan(recommendationContext());

        assertEquals(SimpleTaskPlanner.SOURCE, result.source());
        assertFalse(result.fallbackUsed());
        assertEquals(AgentIntent.PLAY_RECOMMENDATION, result.plan().intent());
    }

    private AgentChatProperties enabledProperties() {
        return new AgentChatProperties(
                true,
                "test-system-prompt",
                "agentmusic.plan.v1",
                12,
                0.0,
                1,
                1,
                800
        );
    }

    private AgentChatProperties disabledProperties() {
        return new AgentChatProperties(
                false,
                "test-system-prompt",
                "agentmusic.plan.v1",
                12,
                0.0,
                1,
                1,
                800
        );
    }

    private PlanningContext recommendationContext() {
        return new PlanningContext(
                new AgentChatRequest("demo-user", "来点适合雨天通勤的中文歌并直接播放", false),
                List.of("上一轮生成的是粤语歌单", "当前在通勤场景")
        );
    }
}
