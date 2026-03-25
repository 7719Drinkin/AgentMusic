package com.agentmusic.agentmusic_backend.service.application;

import com.agentmusic.agentmusic_backend.dto.AgentChatRequest;
import com.agentmusic.agentmusic_backend.dto.AgentChatResponse;
import com.agentmusic.agentmusic_backend.dto.ChatMessageDto;
import java.util.List;

public interface AgentApplicationService {

    AgentChatResponse chat(AgentChatRequest request);

    List<ChatMessageDto> getRecentHistory(String userId, int limit);
}

