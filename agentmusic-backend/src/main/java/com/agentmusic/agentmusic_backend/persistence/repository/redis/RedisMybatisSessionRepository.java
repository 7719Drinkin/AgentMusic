package com.agentmusic.agentmusic_backend.persistence.repository.redis;

import com.agentmusic.agentmusic_backend.domain.PlaybackMode;
import com.agentmusic.agentmusic_backend.domain.PlaybackSession;
import com.agentmusic.agentmusic_backend.persistence.mybatis.mapper.PlaybackSessionMybatisMapper;
import com.agentmusic.agentmusic_backend.persistence.mybatis.model.PlaybackSessionRecord;
import com.agentmusic.agentmusic_backend.persistence.redis.RedisKeys;
import com.agentmusic.agentmusic_backend.persistence.repository.SessionRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "agentmusic.persistence.mode", havingValue = "mybatis")
public class RedisMybatisSessionRepository implements SessionRepository {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final Logger log = LoggerFactory.getLogger(RedisMybatisSessionRepository.class);

    private final PlaybackSessionMybatisMapper playbackSessionMybatisMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public RedisMybatisSessionRepository(
            PlaybackSessionMybatisMapper playbackSessionMybatisMapper,
            StringRedisTemplate stringRedisTemplate
    ) {
        this.playbackSessionMybatisMapper = playbackSessionMybatisMapper;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public PlaybackSession save(PlaybackSession playbackSession) {
        playbackSessionMybatisMapper.upsert(toRecord(playbackSession));
        cacheSafely(playbackSession);
        return playbackSession;
    }

    @Override
    public Optional<PlaybackSession> findActiveByUserId(String userId, LocalDateTime now) {
        Optional<PlaybackSession> cached = loadFromRedisSafely(userId)
                .filter(session -> session.expiresAt().isAfter(now));
        if (cached.isPresent()) {
            return cached;
        }

        PlaybackSessionRecord record = playbackSessionMybatisMapper.selectLatestActiveByUserId(userId, now);
        if (record == null) {
            return Optional.empty();
        }

        PlaybackSession session = toDomain(record);
        cacheSafely(session);
        return Optional.of(session);
    }

    private void cacheSafely(PlaybackSession playbackSession) {
        try {
            cache(playbackSession);
        } catch (RuntimeException exception) {
            log.warn(
                    "Redis session cache write failed for userId={}, sessionId={}. Falling back to MySQL only.",
                    playbackSession.userId(),
                    playbackSession.id(),
                    exception
            );
        }
    }

    private Optional<PlaybackSession> loadFromRedisSafely(String userId) {
        try {
            return loadFromRedis(userId);
        } catch (RuntimeException exception) {
            log.warn(
                    "Redis session cache read failed for userId={}. Falling back to MySQL lookup.",
                    userId,
                    exception
            );
            return Optional.empty();
        }
    }

    private void cache(PlaybackSession playbackSession) {
        String key = RedisKeys.userSession(playbackSession.userId());
        Map<String, String> values = new LinkedHashMap<>();
        values.put("sessionId", playbackSession.id());
        putIfNotNull(values, "currentTrackId", playbackSession.currentTrackId());
        putIfNotNull(values, "currentPlaylistId", playbackSession.currentPlaylistId());
        putIfNotNull(values, "currentTrackIndex", playbackSession.currentTrackIndex());
        putIfNotNull(values, "currentPositionMs", playbackSession.currentPositionMs());
        values.put("isPlaying", Boolean.toString(playbackSession.isPlaying()));
        values.put("playbackMode", playbackSession.playbackMode().name());
        putIfNotNull(values, "deviceId", playbackSession.deviceId());
        values.put("lastUpdated", playbackSession.lastUpdated().format(DATE_TIME_FORMATTER));
        values.put("expiresAt", playbackSession.expiresAt().format(DATE_TIME_FORMATTER));

        stringRedisTemplate.delete(key);
        stringRedisTemplate.opsForHash().putAll(key, values);
        stringRedisTemplate.expire(key, resolveCacheTtl(playbackSession));
    }

    private Optional<PlaybackSession> loadFromRedis(String userId) {
        String key = RedisKeys.userSession(userId);
        Map<Object, Object> raw = stringRedisTemplate.opsForHash().entries(key);
        if (raw == null || raw.isEmpty()) {
            return Optional.empty();
        }

        PlaybackSession session = new PlaybackSession(
                readString(raw, "sessionId"),
                userId,
                nullableString(raw, "currentTrackId"),
                nullableString(raw, "currentPlaylistId"),
                nullableInteger(raw, "currentTrackIndex"),
                nullableInteger(raw, "currentPositionMs"),
                Boolean.parseBoolean(readString(raw, "isPlaying")),
                PlaybackMode.valueOf(readString(raw, "playbackMode")),
                nullableString(raw, "deviceId"),
                LocalDateTime.parse(readString(raw, "lastUpdated"), DATE_TIME_FORMATTER),
                LocalDateTime.parse(readString(raw, "expiresAt"), DATE_TIME_FORMATTER)
        );
        return Optional.of(session);
    }

    private PlaybackSessionRecord toRecord(PlaybackSession playbackSession) {
        return new PlaybackSessionRecord(
                playbackSession.id(),
                playbackSession.userId(),
                playbackSession.currentTrackId(),
                playbackSession.currentPlaylistId(),
                playbackSession.currentTrackIndex(),
                playbackSession.currentPositionMs(),
                playbackSession.isPlaying(),
                playbackSession.playbackMode().name(),
                playbackSession.deviceId(),
                playbackSession.lastUpdated(),
                playbackSession.expiresAt()
        );
    }

    private PlaybackSession toDomain(PlaybackSessionRecord record) {
        return new PlaybackSession(
                record.sessionId(),
                record.userId(),
                record.currentTrackId(),
                record.currentPlaylistId(),
                record.currentTrackIndex(),
                record.currentPositionMs(),
                Boolean.TRUE.equals(record.isPlaying()),
                PlaybackMode.valueOf(record.playbackMode()),
                record.deviceId(),
                record.lastUpdated(),
                record.expiresAt()
        );
    }

    private Duration resolveCacheTtl(PlaybackSession playbackSession) {
        Duration maxHotTtl = RedisKeys.SESSION_TTL;
        Duration remaining = Duration.between(LocalDateTime.now(), playbackSession.expiresAt());
        if (remaining.isNegative() || remaining.isZero()) {
            return Duration.ofSeconds(1);
        }
        return remaining.compareTo(maxHotTtl) < 0 ? remaining : maxHotTtl;
    }

    private void putIfNotNull(Map<String, String> values, String key, Object value) {
        if (value != null) {
            values.put(key, String.valueOf(value));
        }
    }

    private String readString(Map<Object, Object> raw, String key) {
        Object value = raw.get(key);
        if (value == null) {
            throw new IllegalStateException("Missing Redis session field: " + key);
        }
        return String.valueOf(value);
    }

    private String nullableString(Map<Object, Object> raw, String key) {
        Object value = raw.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private Integer nullableInteger(Map<Object, Object> raw, String key) {
        Object value = raw.get(key);
        return value == null ? null : Integer.valueOf(String.valueOf(value));
    }
}
