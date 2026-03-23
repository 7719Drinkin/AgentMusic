package com.agentmusic.agentmusic_backend.dto;

import com.agentmusic.agentmusic_backend.domain.PlaybackMode;
import java.time.LocalDateTime;

public record PlaybackSessionDto(
        String sessionId,
        String currentTrackId,
        Integer currentPositionMs,
        boolean isPlaying,
        PlaybackMode playbackMode,
        String deviceId,
        LocalDateTime lastUpdated
) {
}

