package com.agentmusic.agentmusic_backend.persistence.repository.mybatis;

import com.agentmusic.agentmusic_backend.domain.ChatMessage;
import com.agentmusic.agentmusic_backend.domain.ChatRole;
import com.agentmusic.agentmusic_backend.persistence.mybatis.mapper.ChatMessageMybatisMapper;
import com.agentmusic.agentmusic_backend.persistence.mybatis.model.ChatMessageRecord;
import com.agentmusic.agentmusic_backend.persistence.repository.ChatMessageRepository;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "agentmusic.persistence.mode", havingValue = "mybatis")
public class MybatisChatMessageRepository implements ChatMessageRepository {

    private final ChatMessageMybatisMapper chatMessageMybatisMapper;
    private final JsonParser jsonParser;

    public MybatisChatMessageRepository(ChatMessageMybatisMapper chatMessageMybatisMapper) {
        this.chatMessageMybatisMapper = chatMessageMybatisMapper;
        this.jsonParser = JsonParserFactory.getJsonParser();
    }

    @Override
    public ChatMessage save(ChatMessage chatMessage) {
        chatMessageMybatisMapper.insert(toRecord(chatMessage));
        return chatMessage;
    }

    @Override
    public List<ChatMessage> findRecentByUserId(String userId, int limit) {
        return chatMessageMybatisMapper.selectRecentByUserId(userId, limit).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void trimToLatest(String userId, int keepLatest) {
        chatMessageMybatisMapper.deleteOlderThanLatest(userId, keepLatest);
    }

    private ChatMessageRecord toRecord(ChatMessage chatMessage) {
        return new ChatMessageRecord(
                chatMessage.id(),
                chatMessage.userId(),
                chatMessage.message(),
                chatMessage.role().name(),
                serializeMetadata(chatMessage.metadata()),
                chatMessage.createdAt()
        );
    }

    private ChatMessage toDomain(ChatMessageRecord record) {
        return new ChatMessage(
                record.id(),
                record.userId(),
                record.message(),
                ChatRole.valueOf(record.role()),
                deserializeMetadata(record.metadata()),
                record.createdAt()
        );
    }

    private String serializeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        return writeJsonObject(metadata);
    }

    private Map<String, Object> deserializeMetadata(String metadata) {
        if (metadata == null || metadata.isBlank()) {
            return Collections.emptyMap();
        }
        return jsonParser.parseMap(metadata);
    }

    private String writeJsonObject(Map<String, Object> metadata) {
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('"')
                    .append(escapeJson(entry.getKey()))
                    .append('"')
                    .append(':')
                    .append(writeJsonValue(entry.getValue()));
        }
        builder.append('}');
        return builder.toString();
    }

    private String writeJsonValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String text) {
            return '"' + escapeJson(text) + '"';
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof Map<?, ?> nestedMap) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typedMap = (Map<String, Object>) nestedMap;
            return writeJsonObject(typedMap);
        }
        if (value instanceof List<?> list) {
            StringBuilder builder = new StringBuilder("[");
            for (int index = 0; index < list.size(); index++) {
                if (index > 0) {
                    builder.append(',');
                }
                builder.append(writeJsonValue(list.get(index)));
            }
            builder.append(']');
            return builder.toString();
        }
        return '"' + escapeJson(String.valueOf(value)) + '"';
    }

    private String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
