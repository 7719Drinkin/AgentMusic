package com.agentmusic.agentmusic_backend.persistence.repository;

import com.agentmusic.agentmusic_backend.domain.ChatMessage;
import java.util.List;

public interface ChatMessageRepository {

    ChatMessage save(ChatMessage chatMessage);

    List<ChatMessage> findRecentByUserId(String userId, int limit);

    void trimToLatest(String userId, int keepLatest);
}

