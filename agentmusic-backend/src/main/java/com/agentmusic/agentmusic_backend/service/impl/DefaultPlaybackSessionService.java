package com.agentmusic.agentmusic_backend.service.impl;

import com.agentmusic.agentmusic_backend.domain.PlaybackMode;
import com.agentmusic.agentmusic_backend.domain.PlaybackSession;
import com.agentmusic.agentmusic_backend.dto.PlaybackSessionDto;
import com.agentmusic.agentmusic_backend.mapper.DomainDtoMapper;
import com.agentmusic.agentmusic_backend.repository.SessionRepository;
import com.agentmusic.agentmusic_backend.service.PlaybackSessionService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DefaultPlaybackSessionService implements PlaybackSessionService {

    private static final long SESSION_EXPIRATION_HOURS = 24;

    private final SessionRepository sessionRepository;
    private final Clock clock;

    public DefaultPlaybackSessionService(SessionRepository sessionRepository, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.clock = clock;
    }

    @Override
    public PlaybackSessionDto saveSession(
            String userId,
            String sessionId,
            String currentTrackId,
            Integer currentPositionMs,
            boolean isPlaying,
            PlaybackMode playbackMode,
            String deviceId
    ) {
        LocalDateTime now = LocalDateTime.now(clock);
        PlaybackSession playbackSession = new PlaybackSession(
                sessionId == null || sessionId.isBlank() ? UUID.randomUUID().toString() : sessionId,
                userId,
                currentTrackId,
                currentPositionMs,
                isPlaying,
                playbackMode,
                deviceId,
                now,
                now.plusHours(SESSION_EXPIRATION_HOURS)
        );
        sessionRepository.save(playbackSession);
        return DomainDtoMapper.toDto(playbackSession);
    }

    @Override
    public Optional<PlaybackSessionDto> getActiveSession(String userId) {
        return sessionRepository.findActiveByUserId(userId, LocalDateTime.now(clock))
                .map(DomainDtoMapper::toDto);
    }
}

