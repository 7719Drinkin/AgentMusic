package com.agentmusic.agentmusic_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.agentmusic.agentmusic_backend.domain.PlaybackMode;
import com.agentmusic.agentmusic_backend.dto.PlaybackSessionDto;
import com.agentmusic.agentmusic_backend.dto.PlaylistDto;
import com.agentmusic.agentmusic_backend.dto.PlaylistTrackDto;
import com.agentmusic.agentmusic_backend.dto.TrackDto;
import com.agentmusic.agentmusic_backend.service.BridgePlaybackControlService;
import com.agentmusic.agentmusic_backend.service.PlaybackSessionService;
import com.agentmusic.agentmusic_backend.service.PlaylistService;
import com.agentmusic.agentmusic_backend.service.application.impl.DefaultPlaybackApplicationService;
import java.time.LocalDateTime;
import java.util.List;
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

    @Test
    void nextTrackShouldUseLocalPlaylistContext() {
        PlaybackSessionDto current = new PlaybackSessionDto(
                "session-3",
                "track-1",
                "playlist-1",
                0,
                12000,
                true,
                PlaybackMode.SHUFFLE,
                "device-1",
                LocalDateTime.now()
        );
        PlaylistDto playlist = new PlaylistDto(
                "playlist-1",
                "推荐歌单",
                1,
                LocalDateTime.now(),
                List.of(
                        new PlaylistTrackDto("pt-1", "playlist-1", 0,
                                new TrackDto("track-1", "Song A", "artist-1", "album", "album-1", 180000, null, null)),
                        new PlaylistTrackDto("pt-2", "playlist-1", 1,
                                new TrackDto("track-2", "Song B", "artist-2", "album", "album-2", 200000, null, null))
                )
        );
        PlaybackSessionDto bridgeSession = new PlaybackSessionDto(
                "session-3",
                "track-2",
                "playlist-1",
                1,
                0,
                true,
                PlaybackMode.SHUFFLE,
                "device-1",
                LocalDateTime.now()
        );

        when(playbackSessionService.getActiveSession("demo-user")).thenReturn(Optional.of(current));
        when(playlistService.getPlaylistById("playlist-1")).thenReturn(Optional.of(playlist));
        when(bridgePlaybackControlService.playTrack("demo-user", "track-2", PlaybackMode.SHUFFLE, "device-1"))
                .thenReturn(bridgeSession);
        when(playbackSessionService.saveSession(
                eq("demo-user"),
                eq("session-3"),
                eq("track-2"),
                eq("playlist-1"),
                eq(1),
                eq(0),
                eq(true),
                eq(PlaybackMode.SHUFFLE),
                eq("device-1")
        )).thenReturn(bridgeSession);

        PlaybackSessionDto result = playbackApplicationService.nextTrack("demo-user", null);

        assertEquals("track-2", result.currentTrackId());
        assertEquals(1, result.currentTrackIndex());
    }
}
