package com.agentmusic.agentmusic_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.agentmusic.agentmusic_backend.domain.PlaybackMode;
import com.agentmusic.agentmusic_backend.dto.PlaybackSessionDto;
import com.agentmusic.agentmusic_backend.service.BridgePlaybackControlService;
import com.agentmusic.agentmusic_backend.service.PlaybackSessionService;
import com.agentmusic.agentmusic_backend.service.PlaylistService;
import com.agentmusic.agentmusic_backend.service.application.impl.DefaultPlaybackApplicationService;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultPlaybackApplicationServiceTests {

    @Mock
    private PlaybackSessionService playbackSessionService;

    @Mock
    private BridgePlaybackControlService bridgePlaybackControlService;

    @Mock
    private PlaylistService playlistService;

    @InjectMocks
    private DefaultPlaybackApplicationService playbackApplicationService;

    @Test
    void playTrackShouldPreserveResolvedPositionFromBridgeControlService() {
        PlaybackSessionDto bridgeSession = new PlaybackSessionDto(
                "session-1",
                "track-1",
                "playlist-1",
                2,
                42000,
                true,
                PlaybackMode.SEQUENTIAL,
                "device-1",
                LocalDateTime.now()
        );
        PlaybackSessionDto savedSession = new PlaybackSessionDto(
                "session-1",
                "track-1",
                "playlist-1",
                2,
                42000,
                true,
                PlaybackMode.SEQUENTIAL,
                "device-1",
                LocalDateTime.now()
        );

        when(bridgePlaybackControlService.playTrack("demo-user", "track-1", PlaybackMode.SEQUENTIAL, "device-1"))
                .thenReturn(bridgeSession);
        when(playbackSessionService.saveSession(
                eq("demo-user"),
                eq("session-1"),
                eq("track-1"),
                eq("playlist-1"),
                eq(2),
                eq(42000),
                eq(true),
                eq(PlaybackMode.SEQUENTIAL),
                eq("device-1")
        )).thenReturn(savedSession);

        PlaybackSessionDto result = playbackApplicationService.playTrack(
                "demo-user",
                "track-1",
                "playlist-1",
                2,
                "device-1",
                PlaybackMode.SEQUENTIAL
        );

        assertEquals(42000, result.currentPositionMs());
    }

    @Test
    void playTrackFallbackShouldReuseExistingSessionContext() {
        PlaybackSessionDto current = new PlaybackSessionDto(
                "session-2",
                "track-1",
                "playlist-1",
                1,
                15000,
                false,
                PlaybackMode.SEQUENTIAL,
                "device-1",
                LocalDateTime.now()
        );
        PlaybackSessionDto fallback = new PlaybackSessionDto(
                "session-2",
                "track-2",
                "playlist-9",
                3,
                0,
                true,
                PlaybackMode.SHUFFLE,
                "device-1",
                LocalDateTime.now()
        );

        when(bridgePlaybackControlService.playTrack("demo-user", "track-2", PlaybackMode.SHUFFLE, null))
                .thenThrow(new RuntimeException("spotify unavailable"));
        when(playbackSessionService.getActiveSession("demo-user")).thenReturn(Optional.of(current));
        when(playbackSessionService.saveSession(
                eq("demo-user"),
                eq("session-2"),
                eq("track-2"),
                eq("playlist-9"),
                eq(3),
                eq(0),
                eq(true),
                eq(PlaybackMode.SHUFFLE),
                eq("device-1")
        )).thenReturn(fallback);

        PlaybackSessionDto result = playbackApplicationService.playTrack(
                "demo-user",
                "track-2",
                "playlist-9",
                3,
                null,
                PlaybackMode.SHUFFLE
        );

        assertEquals("session-2", result.sessionId());
        assertEquals("device-1", result.deviceId());
    }
}
