package com.agentmusic.agentmusic_backend.planner;

import com.agentmusic.agentmusic_backend.dto.AgentChatRequest;
import java.util.List;

public record PlanningContext(
        AgentChatRequest request,
        List<String> recentMessages
) {
}

