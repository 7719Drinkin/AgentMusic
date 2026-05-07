package com.agentmusic.agentmusic_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentmusic.agentmusic_backend.config.AgentChatProperties;
import com.agentmusic.agentmusic_backend.domain.ChatRole;
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
import com.agentmusic.agentmusic_backend.web.dto.ChatMessageDto;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClientResponseException;

class LlmBackedTaskPlannerTests {

    @Test
    void shouldUseValidatedLlmPlanWhenEnabled() {
        OpenAiCompatiblePlanningClient planningClient = mock(OpenAiCompatiblePlanningClient.class);
        when(planningClient.isEnabled()).thenReturn(true);

        String query = "Build a playlist mix for a late-night train ride and play it now.";
        AgentPlan llmPlan = new AgentPlan(
                AgentIntent.PLAY_RECOMMENDATION,
                "Build a recommendation playlist first, then start playback.",
                List.of(
                        new PlanStep(1, PlanStepType.READ_CHAT_CONTEXT, Map.of("limit", 20)),
                        new PlanStep(2, PlanStepType.READ_USER_PREFERENCES, Map.of()),
                        new PlanStep(3, PlanStepType.READ_PLAYLIST_HISTORY, Map.of("limit", 10)),
                        new PlanStep(4, PlanStepType.GENERATE_RECOMMENDATION_CANDIDATES, Map.of("query", query)),
                        new PlanStep(5, PlanStepType.RANK_RECOMMENDATION_CANDIDATES, Map.of("query", query)),
                        new PlanStep(6, PlanStepType.CREATE_RECOMMENDATION_PLAYLIST, Map.of("query", query)),
                        new PlanStep(7, PlanStepType.UPDATE_PLAYBACK_STATE, Map.of("query", query)),
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
        assertNull(result.fallbackReason());
        assertEquals(AgentIntent.PLAY_RECOMMENDATION, result.plan().intent());
    }

    @Test
    void shouldFallbackWhenValidatedLlmPlanningFails() {
        OpenAiCompatiblePlanningClient planningClient = mock(OpenAiCompatiblePlanningClient.class);
        when(planningClient.isEnabled()).thenReturn(true);
        doThrow(new IllegalStateException(
                "LLM planning response failed harness validation.",
                new IllegalArgumentException("Step sequence mismatch.")
        ))
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
        assertEquals("harness-validation", result.fallbackReason());
        assertEquals(AgentIntent.PLAY_RECOMMENDATION, result.plan().intent());
    }

    @Test
    void shouldReportProviderRateLimitWhenLlmReturns429() {
        OpenAiCompatiblePlanningClient planningClient = mock(OpenAiCompatiblePlanningClient.class);
        when(planningClient.isEnabled()).thenReturn(true);
        doThrow(WebClientResponseException.create(
                429,
                "Too Many Requests",
                HttpHeaders.EMPTY,
                new byte[0],
                null
        )).when(planningClient).generateValidatedPlan(any());

        LlmBackedTaskPlanner planner = new LlmBackedTaskPlanner(
                enabledProperties(),
                planningClient,
                new SimpleTaskPlanner()
        );

        TaskPlanningResult result = planner.createPlan(recommendationContext());

        assertEquals(LlmBackedTaskPlanner.FALLBACK_SOURCE, result.source());
        assertTrue(result.fallbackUsed());
        assertEquals("provider-rate-limit", result.fallbackReason());
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
                new AgentChatRequest(
                        "demo-user",
                        "Build a playlist mix for a late-night train ride and play it now.",
                        false
                ),
                List.of(
                        new ChatMessageDto(
                                "history-1",
                                ChatRole.AGENT,
                                "Previous playlist included River and other city-pop tracks.",
                                Map.of(),
                                LocalDateTime.of(2026, 5, 1, 20, 1)
                        )
                ),
                List.of("Night Ride -> River / Everyday / Missing You")
        );
    }
}
