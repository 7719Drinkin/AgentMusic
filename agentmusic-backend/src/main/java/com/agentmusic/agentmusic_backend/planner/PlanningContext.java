package com.agentmusic.agentmusic_backend.planner;

import com.agentmusic.agentmusic_backend.web.dto.AgentChatRequest;
import com.agentmusic.agentmusic_backend.web.dto.ChatMessageDto;
import java.util.List;

public record PlanningContext(
        AgentChatRequest request,
        List<ChatMessageDto> recentConversation,
        List<String> recentRecommendationSummaries
) {
}
