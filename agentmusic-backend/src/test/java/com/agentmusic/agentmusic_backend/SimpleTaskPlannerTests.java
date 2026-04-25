package com.agentmusic.agentmusic_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.agentmusic.agentmusic_backend.web.dto.AgentChatRequest;
import com.agentmusic.agentmusic_backend.planner.AgentIntent;
import com.agentmusic.agentmusic_backend.planner.PlanningContext;
import com.agentmusic.agentmusic_backend.planner.impl.SimpleTaskPlanner;
import java.util.List;
import org.junit.jupiter.api.Test;

class SimpleTaskPlannerTests {

    private final SimpleTaskPlanner planner = new SimpleTaskPlanner();

    @Test
    void shouldClassifyChineseRecommendationAsPlayRecommendation() {
        var plan = planner.createPlan(new PlanningContext(
                new AgentChatRequest("demo-user", "来点适合雨天通勤的中文歌", false),
                List.of()
        ));

        assertEquals(AgentIntent.PLAY_RECOMMENDATION, plan.intent());
    }

    @Test
    void shouldClassifyChineseRecommendOnlyRequest() {
        var plan = planner.createPlan(new PlanningContext(
                new AgentChatRequest("demo-user", "先生成歌单，不要直接播放", false),
                List.of()
        ));

        assertEquals(AgentIntent.RECOMMEND_PLAYLIST, plan.intent());
    }

    @Test
    void shouldClassifyChinesePlaybackControlRequest() {
        var plan = planner.createPlan(new PlanningContext(
                new AgentChatRequest("demo-user", "把当前播放切成随机模式", false),
                List.of()
        ));

        assertEquals(AgentIntent.PLAYBACK_CONTROL, plan.intent());
    }
}
