package com.agentmusic.agentmusic_backend.service.application;

import com.agentmusic.agentmusic_backend.dto.PlaybackSessionDto;
import com.agentmusic.agentmusic_backend.dto.UpdatePlaybackSessionRequest;
import java.util.Optional;

public interface PlaybackApplicationService {

    Optional<PlaybackSessionDto> getActiveSession(String userId);

    PlaybackSessionDto updateSession(String userId, UpdatePlaybackSessionRequest request);
}

