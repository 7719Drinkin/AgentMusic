package com.agentmusic.agentmusic_backend.persistence.repository.memory;

import com.agentmusic.agentmusic_backend.domain.ChatMessage;
import com.agentmusic.agentmusic_backend.persistence.repository.ChatMessageRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "agentmusic.persistence.mode", havingValue = "memory", matchIfMissing = true)
public class InMemoryChatMessageRepository implements ChatMessageRepository {

    private final Map<String, CopyOnWriteArrayList<ChatMessage>> messagesByUser = new ConcurrentHashMap<>();

    @Override
    public ChatMessage save(ChatMessage chatMessage) {
        messagesByUser
                .computeIfAbsent(chatMessage.userId(), ignored -> new CopyOnWriteArrayList<>())
                .add(chatMessage);
        return chatMessage;
    }

    @Override
    public List<ChatMessage> findRecentByUserId(String userId, int limit) {
        return messagesByUser.getOrDefault(userId, new CopyOnWriteArrayList<>()).stream()
                .sorted(Comparator.comparing(ChatMessage::createdAt).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public void trimToLatest(String userId, int keepLatest) {
        CopyOnWriteArrayList<ChatMessage> messages = messagesByUser.get(userId);
        if (messages == null || messages.size() <= keepLatest) {
            return;
        }
        List<ChatMessage> latest = messages.stream()
                .sorted(Comparator.comparing(ChatMessage::createdAt).reversed())
                .limit(keepLatest)
                .sorted(Comparator.comparing(ChatMessage::createdAt))
                .toList();
        messagesByUser.put(userId, new CopyOnWriteArrayList<>(latest));
    }
}
