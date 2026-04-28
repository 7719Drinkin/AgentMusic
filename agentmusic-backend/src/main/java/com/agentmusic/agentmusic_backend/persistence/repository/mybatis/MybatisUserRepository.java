package com.agentmusic.agentmusic_backend.persistence.repository.mybatis;

import com.agentmusic.agentmusic_backend.domain.User;
import com.agentmusic.agentmusic_backend.domain.UserPreferences;
import com.agentmusic.agentmusic_backend.persistence.mybatis.mapper.UserMybatisMapper;
import com.agentmusic.agentmusic_backend.persistence.mybatis.model.UserRecord;
import com.agentmusic.agentmusic_backend.persistence.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "agentmusic.persistence.mode", havingValue = "mybatis")
public class MybatisUserRepository implements UserRepository {

    private final UserMybatisMapper userMybatisMapper;
    private final JsonParser jsonParser;

    public MybatisUserRepository(UserMybatisMapper userMybatisMapper) {
        this.userMybatisMapper = userMybatisMapper;
        this.jsonParser = JsonParserFactory.getJsonParser();
    }

    @Override
    public User save(User user) {
        UserRecord existing = userMybatisMapper.selectById(user.id());
        LocalDateTime now = LocalDateTime.now();
        User normalized = new User(
                user.id(),
                user.username(),
                user.email(),
                user.passwordHash(),
                normalizePreferences(user.preferences()),
                user.createdAt() != null ? user.createdAt() : existing != null ? existing.createdAt() : now,
                user.updatedAt() != null ? user.updatedAt() : now
        );
        userMybatisMapper.upsert(toRecord(normalized));
        return normalized;
    }

    @Override
    public Optional<User> findById(String userId) {
        return Optional.ofNullable(userMybatisMapper.selectById(userId))
                .map(this::toDomain);
    }

    private UserRecord toRecord(User user) {
        return new UserRecord(
                user.id(),
                user.username(),
                user.email(),
                user.passwordHash(),
                serializePreferences(user.preferences()),
                user.createdAt(),
                user.updatedAt()
        );
    }

    private User toDomain(UserRecord record) {
        return new User(
                record.id(),
                record.username(),
                record.email(),
                record.passwordHash(),
                deserializePreferences(record.preferences()),
                record.createdAt(),
                record.updatedAt()
        );
    }

    private UserPreferences normalizePreferences(UserPreferences preferences) {
        if (preferences == null) {
            return new UserPreferences(List.of(), List.of(), List.of(), null, null);
        }
        return new UserPreferences(
                safeList(preferences.favoriteGenres()),
                safeList(preferences.favoriteArtists()),
                safeList(preferences.excludedGenres()),
                preferences.preferredLanguage(),
                preferences.moodPreference()
        );
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private String serializePreferences(UserPreferences preferences) {
        UserPreferences normalized = normalizePreferences(preferences);
        return "{"
                + "\"favoriteGenres\":" + writeJsonArray(normalized.favoriteGenres()) + ","
                + "\"favoriteArtists\":" + writeJsonArray(normalized.favoriteArtists()) + ","
                + "\"excludedGenres\":" + writeJsonArray(normalized.excludedGenres()) + ","
                + "\"preferredLanguage\":" + writeJsonValue(normalized.preferredLanguage()) + ","
                + "\"moodPreference\":" + writeJsonValue(normalized.moodPreference())
                + "}";
    }

    private UserPreferences deserializePreferences(String preferences) {
        if (preferences == null || preferences.isBlank()) {
            return new UserPreferences(List.of(), List.of(), List.of(), null, null);
        }
        Map<String, Object> values = jsonParser.parseMap(preferences);
        return new UserPreferences(
                readStringList(values, "favoriteGenres"),
                readStringList(values, "favoriteArtists"),
                readStringList(values, "excludedGenres"),
                nullableString(values.get("preferredLanguage")),
                nullableString(values.get("moodPreference"))
        );
    }

    private List<String> readStringList(Map<String, Object> values, String key) {
        Object raw = values.get(key);
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .map(String::valueOf)
                .toList();
    }

    private String nullableString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String writeJsonArray(List<String> values) {
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(writeJsonValue(values.get(index)));
        }
        builder.append(']');
        return builder.toString();
    }

    private String writeJsonValue(String value) {
        if (value == null) {
            return "null";
        }
        return '"' + escapeJson(value) + '"';
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
