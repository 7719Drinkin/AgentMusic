package com.agentmusic.agentmusic_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.agentmusic.agentmusic_backend.planner.AgentIntent;
import com.agentmusic.agentmusic_backend.planner.PlanningContext;
import com.agentmusic.agentmusic_backend.planner.impl.SimpleTaskPlanner;
import com.agentmusic.agentmusic_backend.web.dto.AgentChatRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

class SimpleTaskPlannerTests {

    private final SimpleTaskPlanner planner = new SimpleTaskPlanner();

    @Test
    void shouldClassifyRecommendationAsPlayRecommendation() {
        var result = planner.createPlan(new PlanningContext(
                new AgentChatRequest("demo-user", "Build a playlist mix for a rainy commute and play it now.", false),
                List.of(),
                List.of()
        ));

        assertEquals(AgentIntent.PLAY_RECOMMENDATION, result.plan().intent());
    }

    @Test
    void shouldClassifyRecommendOnlyRequest() {
        var result = planner.createPlan(new PlanningContext(
                new AgentChatRequest("demo-user", "Build a playlist mix, recommend only.", false),
                List.of(),
                List.of()
        ));

        assertEquals(AgentIntent.RECOMMEND_PLAYLIST, result.plan().intent());
    }

    @Test
    void shouldClassifyChineseRecommendationRequestWithExplicitTitle() {
        var result = planner.createPlan(new PlanningContext(
                new AgentChatRequest("demo-user", "推荐张雨生的《河》以及他的其他歌曲", false),
                List.of(),
                List.of()
        ));

        assertEquals(AgentIntent.PLAY_RECOMMENDATION, result.plan().intent());
    }

    @Test
    void shouldClassifyChineseNoPlayRecommendationRequestAsRecommendOnly() {
        var result = planner.createPlan(new PlanningContext(
                new AgentChatRequest("demo-user", "推荐张雨生的《河》以及他的其他歌曲，不要播放", false),
                List.of(),
                List.of()
        ));

        assertEquals(AgentIntent.RECOMMEND_PLAYLIST, result.plan().intent());
    }

    @Test
    void shouldClassifyPlaybackControlRequest() {
        var result = planner.createPlan(new PlanningContext(
                new AgentChatRequest("demo-user", "Switch the current playback to shuffle mode.", false),
                List.of(),
                List.of()
        ));

        assertEquals(AgentIntent.PLAYBACK_CONTROL, result.plan().intent());
    }
}
