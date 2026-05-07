package com.agentmusic.agentmusic_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agentmusic.agentmusic_backend.config.AgentChatProperties;
import com.agentmusic.agentmusic_backend.planner.AgentIntent;
import com.agentmusic.agentmusic_backend.planner.PlanStepType;
import com.agentmusic.agentmusic_backend.planner.PlanningContext;
import com.agentmusic.agentmusic_backend.planner.llm.AgentLlmPlanningHarness;
import com.agentmusic.agentmusic_backend.web.dto.AgentChatRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentLlmPlanningHarnessTests {

    private final AgentLlmPlanningHarness harness = new AgentLlmPlanningHarness(
            new AgentChatProperties(
                    true,
                    "test-system-prompt",
                    "agentmusic.plan.v1",
                    12,
                    0.0,
                    1,
                    1,
                    800
            ),
            new ObjectMapper()
    );

    @Test
    void buildRequestIncludesExplicitEnumsAndRecentMessages() {
        PlanningContext context = new PlanningContext(
                new AgentChatRequest("demo-user", "来点适合雨天通勤的中文歌", false),
                List.of("上一轮生成的是粤语歌单", "当前在通勤场景")
        );

        var request = harness.buildRequest(context);

        assertEquals("agentmusic.plan.v1", request.schemaVersion());
        assertEquals("demo-user", request.userId());
        assertEquals("来点适合雨天通勤的中文歌", request.latestUserMessage());
        assertTrue(request.allowedIntents().contains(AgentIntent.PLAY_RECOMMENDATION.name()));
        assertTrue(request.allowedStepTypes().contains(PlanStepType.CREATE_RECOMMENDATION_PLAYLIST.name()));
        assertEquals(2, request.recentMessages().size());
    }

    @Test
    void parseAndValidateAcceptsValidRecommendationPlan() {
        String responseJson = """
                {
                  "schemaVersion": "agentmusic.plan.v1",
                  "intent": "PLAY_RECOMMENDATION",
                  "summary": "Build a recommendation playlist first, then start playback.",
                  "reasoning": "The user asked for songs and expects immediate playback.",
                  "confidence": 91,
                  "steps": [
                    {"type": "READ_CHAT_CONTEXT", "arguments": {"limit": 20}},
                    {"type": "READ_USER_PREFERENCES", "arguments": {}},
                    {"type": "READ_PLAYLIST_HISTORY", "arguments": {"limit": 10}},
                    {"type": "GENERATE_RECOMMENDATION_CANDIDATES", "arguments": {"query": "来点适合雨天通勤的中文歌"}},
                    {"type": "RANK_RECOMMENDATION_CANDIDATES", "arguments": {"query": "来点适合雨天通勤的中文歌"}},
                    {"type": "CREATE_RECOMMENDATION_PLAYLIST", "arguments": {"query": "来点适合雨天通勤的中文歌"}},
                    {"type": "UPDATE_PLAYBACK_STATE", "arguments": {"query": "来点适合雨天通勤的中文歌"}},
                    {"type": "PERSIST_CHAT_REPLY", "arguments": {}}
                  ]
                }
                """;

        var result = harness.parseAndValidate(responseJson);

        assertEquals(AgentIntent.PLAY_RECOMMENDATION, result.plan().intent());
        assertEquals(8, result.plan().steps().size());
        assertEquals(PlanStepType.READ_CHAT_CONTEXT, result.plan().steps().getFirst().type());
        assertEquals(PlanStepType.PERSIST_CHAT_REPLY, result.plan().steps().getLast().type());
    }

    @Test
    void parseAndValidateRejectsInvalidStepSequence() {
        String responseJson = """
                {
                  "schemaVersion": "agentmusic.plan.v1",
                  "intent": "PLAYBACK_CONTROL",
                  "summary": "Read the session and update playback state.",
                  "reasoning": "The user requested a playback control action.",
                  "confidence": 88,
                  "steps": [
                    {"type": "READ_CHAT_CONTEXT", "arguments": {"limit": 20}},
                    {"type": "UPDATE_PLAYBACK_STATE", "arguments": {"query": "暂停当前播放"}},
                    {"type": "READ_LOCAL_SESSION", "arguments": {}},
                    {"type": "PERSIST_CHAT_REPLY", "arguments": {}}
                  ]
                }
                """;

        assertThrows(IllegalArgumentException.class, () -> harness.parseAndValidate(responseJson));
    }

    @Test
    void parseAndValidateRejectsUnknownFieldsAndBadArguments() {
        String responseJson = """
                {
                  "schemaVersion": "agentmusic.plan.v1",
                  "intent": "TRACK_LOOKUP",
                  "summary": "Search track metadata.",
                  "reasoning": "The user asked to search for a track.",
                  "confidence": 70,
                  "unexpected": true,
                  "steps": [
                    {"type": "READ_CHAT_CONTEXT", "arguments": {"limit": 20}},
                    {"type": "LOOKUP_TRACK", "arguments": {"query": ""}},
                    {"type": "PERSIST_CHAT_REPLY", "arguments": {}}
                  ]
                }
                """;

        assertThrows(IllegalArgumentException.class, () -> harness.parseAndValidate(responseJson));
    }
}
