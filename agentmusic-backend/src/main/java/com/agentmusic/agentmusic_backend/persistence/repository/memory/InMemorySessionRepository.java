package com.agentmusic.agentmusic_backend.persistence.repository.memory;

import com.agentmusic.agentmusic_backend.domain.PlaybackSession;
import com.agentmusic.agentmusic_backend.persistence.repository.SessionRepository;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemorySessionRepository implements SessionRepository {

    private final Map<String, PlaybackSession> sessions = new ConcurrentHashMap<>();

    @Override
    public PlaybackSession save(PlaybackSession playbackSession) {
        sessions.put(playbackSession.id(), playbackSession);
        return playbackSession;
    }

    @Override
    public Optional<PlaybackSession> findActiveByUserId(String userId, LocalDateTime now) {
        return sessions.values().stream()
                .filter(session -> session.userId().equals(userId))
                .filter(session -> session.expiresAt().isAfter(now))
                .max(Comparator.comparing(PlaybackSession::lastUpdated));
    }
}

