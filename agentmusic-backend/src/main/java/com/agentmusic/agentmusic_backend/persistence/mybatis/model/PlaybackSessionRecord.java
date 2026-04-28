package com.agentmusic.agentmusic_backend.persistence.mybatis.model;

import java.time.LocalDateTime;

public record PlaybackSessionRecord(
        String sessionId,
        String userId,
        String currentTrackId,
        String currentPlaylistId,
        Integer currentTrackIndex,
        Integer currentPositionMs,
        Boolean isPlaying,
        String playbackMode,
        String deviceId,
        LocalDateTime lastUpdated,
        LocalDateTime expiresAt
) {
}
