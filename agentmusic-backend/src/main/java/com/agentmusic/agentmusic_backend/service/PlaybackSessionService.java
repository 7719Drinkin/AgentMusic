package com.agentmusic.agentmusic_backend.service;

import com.agentmusic.agentmusic_backend.domain.PlaybackMode;
import com.agentmusic.agentmusic_backend.web.dto.PlaybackSessionDto;
import java.util.Optional;

public interface PlaybackSessionService {

    PlaybackSessionDto saveSession(
            String userId,
            String sessionId,
            String currentTrackId,
            String currentPlaylistId,
            Integer currentTrackIndex,
            Integer currentPositionMs,
            boolean isPlaying,
            PlaybackMode playbackMode,
            String deviceId
    );

    Optional<PlaybackSessionDto> getActiveSession(String userId);
}
