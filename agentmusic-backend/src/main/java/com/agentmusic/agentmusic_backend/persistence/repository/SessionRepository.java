package com.agentmusic.agentmusic_backend.persistence.repository;

import com.agentmusic.agentmusic_backend.domain.PlaybackSession;
import java.time.LocalDateTime;
import java.util.Optional;

public interface SessionRepository {

    PlaybackSession save(PlaybackSession playbackSession);

    Optional<PlaybackSession> findActiveByUserId(String userId, LocalDateTime now);
}

