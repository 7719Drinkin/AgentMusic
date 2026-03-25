package com.agentmusic.agentmusic_backend.service.application.impl;

import com.agentmusic.agentmusic_backend.domain.ChatRole;
import com.agentmusic.agentmusic_backend.dto.AgentChatRequest;
import com.agentmusic.agentmusic_backend.dto.AgentChatResponse;
import com.agentmusic.agentmusic_backend.dto.ChatMessageDto;
import com.agentmusic.agentmusic_backend.service.BackendRuntimeFacade;
import com.agentmusic.agentmusic_backend.service.ChatMemoryService;
import com.agentmusic.agentmusic_backend.service.application.AgentApplicationService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class DefaultAgentApplicationService implements AgentApplicationService {

    private final ChatMemoryService chatMemoryService;
    private final BackendRuntimeFacade backendRuntimeFacade;

    public DefaultAgentApplicationService(
            ChatMemoryService chatMemoryService,
            BackendRuntimeFacade backendRuntimeFacade
    ) {
        this.chatMemoryService = chatMemoryService;
        this.backendRuntimeFacade = backendRuntimeFacade;
    }

    @Override
    public AgentChatResponse chat(AgentChatRequest request) {
        chatMemoryService.appendMessage(
                request.userId(),
                ChatRole.USER,
                request.message(),
                Map.of("voiceInput", request.voiceInput())
        );

        ChatMessageDto reply = chatMemoryService.appendMessage(
                request.userId(),
                ChatRole.AGENT,
                "Agent pipeline placeholder: controller/application/service skeleton is ready. Spotify and planner wiring comes next.",
                Map.of("stage", "skeleton")
        );

        return new AgentChatResponse(
                reply,
                backendRuntimeFacade.getActiveSession(request.userId()).orElse(null),
                backendRuntimeFacade.getRecentPlaylists(request.userId())
        );
    }

    @Override
    public List<ChatMessageDto> getRecentHistory(String userId, int limit) {
        return chatMemoryService.getRecentMessages(userId, limit);
    }
}

