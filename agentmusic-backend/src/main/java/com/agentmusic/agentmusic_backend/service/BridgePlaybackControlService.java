package com.agentmusic.agentmusic_backend.service;

import com.agentmusic.agentmusic_backend.integration.spotify.SpotifyBridgeDevice;
import com.agentmusic.agentmusic_backend.domain.PlaybackMode;
import com.agentmusic.agentmusic_backend.web.dto.PlaybackSessionDto;
import java.util.List;

public interface BridgePlaybackControlService {

    PlaybackSessionDto playTrack(String userId, String trackId, PlaybackMode playbackMode, String deviceId);

    PlaybackSessionDto pause(String userId, String deviceId);

    PlaybackSessionDto nextTrack(String userId, String deviceId);

    PlaybackSessionDto previousTrack(String userId, String deviceId);

    PlaybackSessionDto seek(String userId, int positionMs, String deviceId);

    PlaybackSessionDto changePlaybackMode(String userId, PlaybackMode playbackMode, String deviceId);

    PlaybackSessionDto syncPlaybackState(String userId);

    List<SpotifyBridgeDevice> getAvailableDevices(String userId);

    PlaybackSessionDto transferPlayback(String userId, String deviceId, boolean play);
}
