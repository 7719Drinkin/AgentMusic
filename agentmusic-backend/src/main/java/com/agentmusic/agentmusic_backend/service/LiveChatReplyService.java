package com.agentmusic.agentmusic_backend.service;

import com.agentmusic.agentmusic_backend.web.dto.AgentRuntimeStatusDto;
import java.util.Optional;
import java.util.List;
import com.agentmusic.agentmusic_backend.web.dto.ChatMessageDto;

public interface LiveChatReplyService {

    Optional<String> generateReply(List<ChatMessageDto> recentMessages, String userMessage);

    boolean isEnabled();

    AgentRuntimeStatusDto getRuntimeStatus();
}
