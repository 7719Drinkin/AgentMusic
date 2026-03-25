package com.agentmusic.agentmusic_backend.service.application.impl;

import com.agentmusic.agentmusic_backend.dto.PlaybackSessionDto;
import com.agentmusic.agentmusic_backend.dto.UpdatePlaybackSessionRequest;
import com.agentmusic.agentmusic_backend.service.PlaybackSessionService;
import com.agentmusic.agentmusic_backend.service.application.PlaybackApplicationService;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class DefaultPlaybackApplicationService implements PlaybackApplicationService {

    private final PlaybackSessionService playbackSessionService;

    public DefaultPlaybackApplicationService(PlaybackSessionService playbackSessionService) {
        this.playbackSessionService = playbackSessionService;
    }

    @Override
    public Optional<PlaybackSessionDto> getActiveSession(String userId) {
        return playbackSessionService.getActiveSession(userId);
    }

    @Override
    public PlaybackSessionDto updateSession(String userId, UpdatePlaybackSessionRequest request) {
        return playbackSessionService.saveSession(
                userId,
                request.sessionId(),
                request.currentTrackId(),
                request.currentPositionMs(),
                request.isPlaying(),
                request.playbackMode(),
                request.deviceId()
        );
    }
}

