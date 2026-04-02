package com.agentmusic.agentmusic_backend.dto;

import com.agentmusic.agentmusic_backend.domain.PlaybackMode;

public record PlayTrackRequest(
        String trackId,
        String deviceId,
        PlaybackMode playbackMode
) {
}
