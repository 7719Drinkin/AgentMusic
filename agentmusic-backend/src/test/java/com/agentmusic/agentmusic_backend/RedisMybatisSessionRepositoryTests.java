package com.agentmusic.agentmusic_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentmusic.agentmusic_backend.domain.PlaybackMode;
import com.agentmusic.agentmusic_backend.domain.PlaybackSession;
import com.agentmusic.agentmusic_backend.persistence.mybatis.mapper.PlaybackSessionMybatisMapper;
import com.agentmusic.agentmusic_backend.persistence.mybatis.model.PlaybackSessionRecord;
import com.agentmusic.agentmusic_backend.persistence.repository.redis.RedisMybatisSessionRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class RedisMybatisSessionRepositoryTests {

    @Mock
    private PlaybackSessionMybatisMapper playbackSessionMybatisMapper;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    private RedisMybatisSessionRepository repository;

    @BeforeEach
    void setUp() {
        repository = new RedisMybatisSessionRepository(playbackSessionMybatisMapper, stringRedisTemplate);
    }

    @Test
    void findActiveByUserIdShouldFallbackToMybatisWhenRedisReadFails() {
        LocalDateTime now = LocalDateTime.now();
        PlaybackSessionRecord record = new PlaybackSessionRecord(
                "session-1",
                "demo-user",
                "track-1",
                "playlist-1",
                2,
                15000,
                true,
                PlaybackMode.SHUFFLE.name(),
                "device-1",
                now.minusSeconds(10),
                now.plusMinutes(10)
        );

        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(anyString())).thenThrow(new RuntimeException("redis unavailable"));
        when(playbackSessionMybatisMapper.selectLatestActiveByUserId("demo-user", now)).thenReturn(record);
        when(stringRedisTemplate.delete(anyString())).thenReturn(Boolean.TRUE);
        when(stringRedisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Boolean.TRUE);

        Optional<PlaybackSession> result = repository.findActiveByUserId("demo-user", now);

        assertThat(result)
                .isPresent()
                .get()
                .extracting(PlaybackSession::currentTrackId)
                .isEqualTo("track-1");
        verify(playbackSessionMybatisMapper).selectLatestActiveByUserId("demo-user", now);
    }

    @Test
    void saveShouldPersistToMybatisWhenRedisWriteFails() {
        LocalDateTime now = LocalDateTime.now();
        PlaybackSession session = new PlaybackSession(
                "session-2",
                "demo-user",
                "track-9",
                "playlist-3",
                4,
                32000,
                true,
                PlaybackMode.SEQUENTIAL,
                "device-7",
                now,
                now.plusMinutes(15)
        );

        when(stringRedisTemplate.delete(anyString())).thenThrow(new RuntimeException("redis unavailable"));

        PlaybackSession result = repository.save(session);

        assertThat(result).isEqualTo(session);
        verify(playbackSessionMybatisMapper).upsert(any(PlaybackSessionRecord.class));
    }
}
