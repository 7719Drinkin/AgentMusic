package com.agentmusic.agentmusic_backend.persistence.mybatis.model;

import java.time.LocalDateTime;

public record PlaybackSessionRecord(
        String userId,
        String currentTrackId,
        Integer currentPositionMs,
        Boolean isPlaying,
        String playbackMode,
        String deviceId,
        String currentPlaylistId,
        Integer currentTrackIndex,
        LocalDateTime updatedAt
) {
}
