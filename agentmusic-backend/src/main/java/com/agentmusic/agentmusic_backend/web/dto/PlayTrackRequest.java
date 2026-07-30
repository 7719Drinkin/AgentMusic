package com.agentmusic.agentmusic_backend.web.dto;

import com.agentmusic.agentmusic_backend.domain.PlaybackMode;

public record PlayTrackRequest(
        String trackId,
        String playlistId,
        Integer trackIndex,
        String deviceId,
        PlaybackMode playbackMode
) {
}
