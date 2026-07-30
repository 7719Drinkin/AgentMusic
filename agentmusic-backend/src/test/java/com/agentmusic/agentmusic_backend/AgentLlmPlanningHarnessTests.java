package com.agentmusic.agentmusic_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agentmusic.agentmusic_backend.config.AgentChatProperties;
import com.agentmusic.agentmusic_backend.domain.ChatRole;
import com.agentmusic.agentmusic_backend.planner.AgentIntent;
import com.agentmusic.agentmusic_backend.planner.PlanStepType;
import com.agentmusic.agentmusic_backend.planner.PlanningContext;
import com.agentmusic.agentmusic_backend.planner.llm.AgentLlmPlanningHarness;
import com.agentmusic.agentmusic_backend.web.dto.AgentChatRequest;
import com.agentmusic.agentmusic_backend.web.dto.ChatMessageDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentLlmPlanningHarnessTests {

    private static final String RECOMMENDATION_MESSAGE =
            "\u7ed9\u6211\u63a8\u8350\u5f20\u96e8\u751f\u7684\u300a\u6cb3\u300b\u4ee5\u53ca\u4ed6\u7684\u5176\u4ed6\u6b4c\u66f2";

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
    void buildRequestIncludesStructuredConversationAndRecommendationMemory() {
        PlanningContext context = new PlanningContext(
                new AgentChatRequest("demo-user", "Recommend poetic Mandarin songs for a late-night train ride.", false),
                List.of(
                        new ChatMessageDto(
                                "user-1",
                                ChatRole.USER,
                                "Build me a soft city-pop playlist.",
                                Map.of(),
                                LocalDateTime.of(2026, 5, 1, 20, 0)
                        ),
                        new ChatMessageDto(
                                "agent-1",
                                ChatRole.AGENT,
                                "I recommended River by Tom Chang and a few other city-pop tracks.",
                                Map.of(),
                                LocalDateTime.of(2026, 5, 1, 20, 1)
                        )
                ),
                List.of("Night Ride -> River / Everyday / Missing You")
        );

        var request = harness.buildRequest(context);

        assertEquals("agentmusic.plan.v1", request.schemaVersion());
        assertEquals("demo-user", request.userId());
        assertEquals("Recommend poetic Mandarin songs for a late-night train ride.", request.latestUserMessage());
        assertTrue(request.allowedIntents().contains(AgentIntent.PLAY_RECOMMENDATION.name()));
        assertTrue(request.allowedStepTypes().contains(PlanStepType.CREATE_RECOMMENDATION_PLAYLIST.name()));
        assertEquals(2, request.recentConversation().size());
        assertEquals("user", request.recentConversation().getFirst().role());
        assertEquals("assistant", request.recentConversation().getLast().role());
        assertEquals(1, request.recentRecommendationSummaries().size());
    }

    @Test
    void parseAndValidateAcceptsValidRecommendationPlan() {
        String responseJson = """
                {
                  "schemaVersion": "agentmusic.plan.v1",
                  "intent": "PLAY_RECOMMENDATION",
                  "summary": "Build a recommendation playlist first, then start playback.",
                  "reasoning": "The user asked for recommendation plus immediate playback.",
                  "confidence": 91,
                  "steps": [
                    {"type": "READ_CHAT_CONTEXT", "arguments": {"limit": 20}},
                    {"type": "READ_USER_PREFERENCES", "arguments": {}},
                    {"type": "READ_PLAYLIST_HISTORY", "arguments": {"limit": 10}},
                    {"type": "GENERATE_RECOMMENDATION_CANDIDATES", "arguments": {"query": "Recommend poetic Mandarin songs for a late-night train ride."}},
                    {"type": "RANK_RECOMMENDATION_CANDIDATES", "arguments": {"query": "Recommend poetic Mandarin songs for a late-night train ride."}},
                    {"type": "CREATE_RECOMMENDATION_PLAYLIST", "arguments": {"query": "Recommend poetic Mandarin songs for a late-night train ride."}},
                    {"type": "UPDATE_PLAYBACK_STATE", "arguments": {"query": "Recommend poetic Mandarin songs for a late-night train ride."}},
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
    void parseAndValidateRejectsQueryThatDropsExplicitTitleFromLatestMessage() {
        PlanningContext context = new PlanningContext(
                new AgentChatRequest("demo-user", RECOMMENDATION_MESSAGE, false),
                List.of(),
                List.of()
        );

        String responseJson = """
                {
                  "schemaVersion": "agentmusic.plan.v1",
                  "intent": "PLAY_RECOMMENDATION",
                  "summary": "Recommend songs by Zhang Yusheng including He.",
                  "reasoning": "The user asked for songs by Zhang Yusheng and mentioned a specific title.",
                  "confidence": 90,
                  "steps": [
                    {"type": "READ_CHAT_CONTEXT", "arguments": {"limit": 20}},
                    {"type": "READ_USER_PREFERENCES", "arguments": {}},
                    {"type": "READ_PLAYLIST_HISTORY", "arguments": {"limit": 10}},
                    {"type": "GENERATE_RECOMMENDATION_CANDIDATES", "arguments": {"query": "Zhang Yusheng He"}},
                    {"type": "RANK_RECOMMENDATION_CANDIDATES", "arguments": {"query": "Zhang Yusheng He"}},
                    {"type": "CREATE_RECOMMENDATION_PLAYLIST", "arguments": {"query": "Zhang Yusheng He"}},
                    {"type": "UPDATE_PLAYBACK_STATE", "arguments": {"query": "Zhang Yusheng He"}},
                    {"type": "PERSIST_CHAT_REPLY", "arguments": {}}
                  ]
                }
                """;

        assertThrows(IllegalArgumentException.class, () -> harness.parseAndValidate(responseJson, context));
    }

    @Test
    void parseAndValidateRejectsDefaultRecommendationWithoutAutoplay() {
        PlanningContext context = new PlanningContext(
                new AgentChatRequest("demo-user", RECOMMENDATION_MESSAGE, false),
                List.of(),
                List.of()
        );

        String responseJson = """
                {
                  "schemaVersion": "agentmusic.plan.v1",
                  "intent": "RECOMMEND_PLAYLIST",
                  "summary": "Recommend songs by Zhang Yusheng including He.",
                  "reasoning": "The user asked for a recommendation.",
                  "confidence": 90,
                  "steps": [
                    {"type": "READ_CHAT_CONTEXT", "arguments": {"limit": 20}},
                    {"type": "READ_USER_PREFERENCES", "arguments": {}},
                    {"type": "READ_PLAYLIST_HISTORY", "arguments": {"limit": 10}},
                    {"type": "GENERATE_RECOMMENDATION_CANDIDATES", "arguments": {"query": "\u7ed9\u6211\u63a8\u8350\u5f20\u96e8\u751f\u7684\u300a\u6cb3\u300b\u4ee5\u53ca\u4ed6\u7684\u5176\u4ed6\u6b4c\u66f2"}},
                    {"type": "RANK_RECOMMENDATION_CANDIDATES", "arguments": {"query": "\u7ed9\u6211\u63a8\u8350\u5f20\u96e8\u751f\u7684\u300a\u6cb3\u300b\u4ee5\u53ca\u4ed6\u7684\u5176\u4ed6\u6b4c\u66f2"}},
                    {"type": "CREATE_RECOMMENDATION_PLAYLIST", "arguments": {"query": "\u7ed9\u6211\u63a8\u8350\u5f20\u96e8\u751f\u7684\u300a\u6cb3\u300b\u4ee5\u53ca\u4ed6\u7684\u5176\u4ed6\u6b4c\u66f2"}},
                    {"type": "PERSIST_CHAT_REPLY", "arguments": {}}
                  ]
                }
                """;

        assertThrows(IllegalArgumentException.class, () -> harness.parseAndValidate(responseJson, context));
    }

    @Test
    void parseAndValidateRejectsRecommendationRequestClassifiedAsArtistLookup() {
        PlanningContext context = new PlanningContext(
                new AgentChatRequest("demo-user", RECOMMENDATION_MESSAGE + "\uff0c\u5148\u4e0d\u8981\u64ad\u653e", false),
                List.of(),
                List.of()
        );

        String responseJson = """
                {
                  "schemaVersion": "agentmusic.plan.v1",
                  "intent": "ARTIST_LOOKUP",
                  "summary": "Read artist metadata for Zhang Yusheng.",
                  "reasoning": "The request mentions Zhang Yusheng.",
                  "confidence": 82,
                  "steps": [
                    {"type": "READ_CHAT_CONTEXT", "arguments": {"limit": 20}},
                    {"type": "LOOKUP_ARTIST", "arguments": {"query": "\u5f20\u96e8\u751f"}},
                    {"type": "PERSIST_CHAT_REPLY", "arguments": {}}
                  ]
                }
                """;

        assertThrows(IllegalArgumentException.class, () -> harness.parseAndValidate(responseJson, context));
    }

    @Test
    void parseAndValidateAcceptsNoPlayRecommendationPlan() {
        PlanningContext context = new PlanningContext(
                new AgentChatRequest("demo-user", RECOMMENDATION_MESSAGE + "\uff0c\u4e0d\u8981\u64ad\u653e", false),
                List.of(),
                List.of()
        );

        String responseJson = """
                {
                  "schemaVersion": "agentmusic.plan.v1",
                  "intent": "RECOMMEND_PLAYLIST",
                  "summary": "Create a recommendation playlist without playback.",
                  "reasoning": "The user explicitly asked not to play.",
                  "confidence": 90,
                  "steps": [
                    {"type": "READ_CHAT_CONTEXT", "arguments": {"limit": 20}},
                    {"type": "READ_USER_PREFERENCES", "arguments": {}},
                    {"type": "READ_PLAYLIST_HISTORY", "arguments": {"limit": 10}},
                    {"type": "GENERATE_RECOMMENDATION_CANDIDATES", "arguments": {"query": "\u7ed9\u6211\u63a8\u8350\u5f20\u96e8\u751f\u7684\u300a\u6cb3\u300b\u4ee5\u53ca\u4ed6\u7684\u5176\u4ed6\u6b4c\u66f2\uff0c\u4e0d\u8981\u64ad\u653e"}},
                    {"type": "RANK_RECOMMENDATION_CANDIDATES", "arguments": {"query": "\u7ed9\u6211\u63a8\u8350\u5f20\u96e8\u751f\u7684\u300a\u6cb3\u300b\u4ee5\u53ca\u4ed6\u7684\u5176\u4ed6\u6b4c\u66f2\uff0c\u4e0d\u8981\u64ad\u653e"}},
                    {"type": "CREATE_RECOMMENDATION_PLAYLIST", "arguments": {"query": "\u7ed9\u6211\u63a8\u8350\u5f20\u96e8\u751f\u7684\u300a\u6cb3\u300b\u4ee5\u53ca\u4ed6\u7684\u5176\u4ed6\u6b4c\u66f2\uff0c\u4e0d\u8981\u64ad\u653e"}},
                    {"type": "PERSIST_CHAT_REPLY", "arguments": {}}
                  ]
                }
                """;

        var result = harness.parseAndValidate(responseJson, context);

        assertEquals(AgentIntent.RECOMMEND_PLAYLIST, result.plan().intent());
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
                    {"type": "UPDATE_PLAYBACK_STATE", "arguments": {"query": "Pause the current track."}},
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

    @Test
    void parseAndValidateRejectsRecommendationRequestClassifiedAsUnknown() {
        PlanningContext context = new PlanningContext(
                new AgentChatRequest("demo-user", RECOMMENDATION_MESSAGE, false),
                List.of(),
                List.of()
        );

        String responseJson = """
                {
                  "schemaVersion": "agentmusic.plan.v1",
                  "intent": "UNKNOWN",
                  "summary": "The request is unclear.",
                  "reasoning": "The request could not be classified confidently.",
                  "confidence": 32,
                  "steps": [
                    {"type": "READ_CHAT_CONTEXT", "arguments": {"limit": 20}},
                    {"type": "PERSIST_CHAT_REPLY", "arguments": {}}
                  ]
                }
                """;

        assertThrows(IllegalArgumentException.class, () -> harness.parseAndValidate(responseJson, context));
    }
}
