package com.agentmusic.agentmusic_backend.service.impl;

import com.agentmusic.agentmusic_backend.persistence.redis.RedisKeys;
import com.agentmusic.agentmusic_backend.domain.ChatMessage;
import com.agentmusic.agentmusic_backend.domain.ChatRole;
import com.agentmusic.agentmusic_backend.web.dto.ChatMessageDto;
import com.agentmusic.agentmusic_backend.web.mapper.DomainDtoMapper;
import com.agentmusic.agentmusic_backend.persistence.repository.ChatMessageRepository;
import com.agentmusic.agentmusic_backend.service.ChatMemoryService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DefaultChatMemoryService implements ChatMemoryService {

    private static final int MAX_PERSISTED_MESSAGES = 200;

    private final ChatMessageRepository chatMessageRepository;
    private final Clock clock;

    public DefaultChatMemoryService(ChatMessageRepository chatMessageRepository, Clock clock) {
        this.chatMessageRepository = chatMessageRepository;
        this.clock = clock;
    }

    @Override
    public ChatMessageDto appendMessage(String userId, ChatRole role, String message, Map<String, Object> metadata) {
        ChatMessage chatMessage = new ChatMessage(
                UUID.randomUUID().toString(),
                userId,
                message,
                role,
                metadata,
                LocalDateTime.now(clock)
        );
        chatMessageRepository.save(chatMessage);
        chatMessageRepository.trimToLatest(userId, MAX_PERSISTED_MESSAGES);
        return DomainDtoMapper.toDto(chatMessage);
    }

    @Override
    public List<ChatMessageDto> getRecentMessages(String userId, int limit) {
        int effectiveLimit = Math.min(limit, RedisKeys.SHORT_TERM_CHAT_LIMIT);
        return chatMessageRepository.findRecentByUserId(userId, effectiveLimit).stream()
                .map(DomainDtoMapper::toDto)
                .toList();
    }
}

