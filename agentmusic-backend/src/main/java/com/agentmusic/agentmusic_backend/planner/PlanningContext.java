package com.agentmusic.agentmusic_backend.planner;

import com.agentmusic.agentmusic_backend.web.dto.AgentChatRequest;
import java.util.List;

public record PlanningContext(
        AgentChatRequest request,
        List<String> recentMessages
) {
}

