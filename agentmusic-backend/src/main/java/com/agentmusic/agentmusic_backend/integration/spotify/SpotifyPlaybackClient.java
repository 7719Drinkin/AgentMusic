package com.agentmusic.agentmusic_backend.integration.spotify;

import com.agentmusic.agentmusic_backend.domain.PlaybackMode;
import java.util.List;
import java.util.Optional;

public interface SpotifyPlaybackClient {

    Optional<SpotifyPlaybackState> getPlaybackState(String accessToken);

    List<SpotifyBridgeDevice> getAvailableDevices(String accessToken);

    void transferPlayback(String accessToken, String deviceId, boolean play);

    void playTrack(String accessToken, String trackId, String deviceId);

    void resumePlayback(String accessToken, String deviceId);

    void pause(String accessToken, String deviceId);

    void nextTrack(String accessToken, String deviceId);

    void previousTrack(String accessToken, String deviceId);

    void seek(String accessToken, int positionMs, String deviceId);

    void changePlaybackMode(String accessToken, PlaybackMode playbackMode, String deviceId);
}
