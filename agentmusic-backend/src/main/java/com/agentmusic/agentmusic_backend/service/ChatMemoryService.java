package com.agentmusic.agentmusic_backend.service;

import com.agentmusic.agentmusic_backend.domain.ChatRole;
import com.agentmusic.agentmusic_backend.dto.ChatMessageDto;
import java.util.List;
import java.util.Map;

public interface ChatMemoryService {

    ChatMessageDto appendMessage(String userId, ChatRole role, String message, Map<String, Object> metadata);

    List<ChatMessageDto> getRecentMessages(String userId, int limit);
}

