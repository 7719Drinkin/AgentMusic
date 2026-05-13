package com.agentmusic.agentmusic_backend.service;

import com.agentmusic.agentmusic_backend.web.dto.AgentRuntimeStatusDto;
import com.agentmusic.agentmusic_backend.web.dto.ChatMessageDto;
import com.agentmusic.agentmusic_backend.planner.AgentPlan;
import com.agentmusic.agentmusic_backend.planner.PlanningContext;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public interface LiveChatReplyService {

    Optional<String> generateReply(List<ChatMessageDto> recentMessages, String userMessage);

    default Optional<String> generateStreamingReply(
            List<ChatMessageDto> recentMessages,
            String userMessage,
            Consumer<String> deltaConsumer
    ) {
        Optional<String> reply = generateReply(recentMessages, userMessage);
        reply.ifPresent(content -> {
            if (deltaConsumer != null) {
                deltaConsumer.accept(content);
            }
        });
        return reply;
    }

    default Optional<String> generateStreamingNarration(
            AgentPlan plan,
            PlanningContext planningContext,
            String executionResult,
            Consumer<String> deltaConsumer
    ) {
        return Optional.empty();
    }

    boolean isEnabled();

    AgentRuntimeStatusDto getRuntimeStatus();
}
