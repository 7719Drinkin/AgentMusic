package com.agentmusic.agentmusic_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

import com.agentmusic.agentmusic_backend.integration.spotify.SpotifyBridgeDevice;
import com.agentmusic.agentmusic_backend.integration.spotify.SpotifyPlaybackClient;
import com.agentmusic.agentmusic_backend.integration.spotify.SpotifyPlaybackState;
import com.agentmusic.agentmusic_backend.config.SpotifyBridgeProperties;
import com.agentmusic.agentmusic_backend.domain.PlaybackMode;
import com.agentmusic.agentmusic_backend.web.dto.PlaybackSessionDto;
import com.agentmusic.agentmusic_backend.web.exception.SpotifyPlaybackUnavailableException;
import com.agentmusic.agentmusic_backend.service.PlaybackSessionService;
import com.agentmusic.agentmusic_backend.service.SpotifyBridgeAuthService;
import com.agentmusic.agentmusic_backend.service.impl.DefaultBridgePlaybackControlService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultBridgePlaybackControlServiceTests {

    @Mock
    private SpotifyPlaybackClient spotifyPlaybackClient;

    @Mock
    private SpotifyBridgeAuthService spotifyBridgeAuthService;

    @Mock
    private PlaybackSessionService playbackSessionService;

    @Test
    void playTrackShouldResolveActiveDeviceAndTransferBeforePlaying() {
        SpotifyBridgeProperties properties = new SpotifyBridgeProperties(
                true,
                "client-id",
                "client-secret",
                "http://127.0.0.1:8080/api/auth/spotify/callback",
                "bridge-user",
                ""
        );
        DefaultBridgePlaybackControlService service = new DefaultBridgePlaybackControlService(
                spotifyPlaybackClient,
                spotifyBridgeAuthService,
                playbackSessionService,
                properties
        );

        when(spotifyBridgeAuthService.getValidAccessToken()).thenReturn(Optional.of("token"));
        when(playbackSessionService.getActiveSession("demo-user")).thenReturn(Optional.empty());
        when(spotifyPlaybackClient.getAvailableDevices("token")).thenReturn(List.of(
                new SpotifyBridgeDevice("restricted", "Restricted", false, true, "Computer", 50),
                new SpotifyBridgeDevice("active-device", "Edge Player", true, false, "Computer", 80)
        ));
        when(spotifyPlaybackClient.getPlaybackState("token")).thenReturn(Optional.of(
                new SpotifyPlaybackState(null, 0, false, PlaybackMode.SEQUENTIAL, null)
        ));
        when(playbackSessionService.saveSession(
                eq("demo-user"),
                any(),
                eq("track-1"),
                any(),
                any(),
                eq(0),
                eq(true),
                eq(PlaybackMode.SHUFFLE),
                eq("active-device")
        )).thenReturn(new PlaybackSessionDto(
                "session-1",
                "track-1",
                null,
                null,
                0,
                true,
                PlaybackMode.SHUFFLE,
                "active-device",
                LocalDateTime.now()
        ));

        PlaybackSessionDto session = service.playTrack("demo-user", "track-1", PlaybackMode.SHUFFLE, null);

        verify(spotifyPlaybackClient, never()).transferPlayback("token", "active-device", false);
        verify(spotifyPlaybackClient).playTrack("token", "track-1", "active-device");
        assertEquals("active-device", session.deviceId());
        assertEquals("track-1", session.currentTrackId());
    }

    @Test
    void playTrackShouldStillPlayWhenModeUpdateFails() {
        SpotifyBridgeProperties properties = new SpotifyBridgeProperties(
                true,
                "client-id",
                "client-secret",
                "http://127.0.0.1:8080/api/auth/spotify/callback",
                "bridge-user",
                ""
        );
        DefaultBridgePlaybackControlService service = new DefaultBridgePlaybackControlService(
                spotifyPlaybackClient,
                spotifyBridgeAuthService,
                playbackSessionService,
                properties
        );

        when(spotifyBridgeAuthService.getValidAccessToken()).thenReturn(Optional.of("token"));
        when(playbackSessionService.getActiveSession("demo-user")).thenReturn(Optional.empty());
        when(spotifyPlaybackClient.getAvailableDevices("token")).thenReturn(List.of(
                new SpotifyBridgeDevice("active-device", "Edge Player", true, false, "Computer", 80)
        ));
        when(spotifyPlaybackClient.getPlaybackState("token")).thenReturn(Optional.of(
                new SpotifyPlaybackState(null, 0, false, PlaybackMode.SEQUENTIAL, null)
        ));
        doThrow(new RuntimeException("mode unavailable"))
                .when(spotifyPlaybackClient)
                .changePlaybackMode("token", PlaybackMode.SHUFFLE, "active-device");
        when(playbackSessionService.saveSession(
                eq("demo-user"),
                any(),
                eq("track-1"),
                any(),
                any(),
                eq(0),
                eq(true),
                eq(PlaybackMode.SHUFFLE),
                eq("active-device")
        )).thenReturn(new PlaybackSessionDto(
                "session-5",
                "track-1",
                null,
                null,
                0,
                true,
                PlaybackMode.SHUFFLE,
                "active-device",
                LocalDateTime.now()
        ));

        PlaybackSessionDto session = service.playTrack("demo-user", "track-1", PlaybackMode.SHUFFLE, null);

        verify(spotifyPlaybackClient).playTrack("token", "track-1", "active-device");
        verify(spotifyPlaybackClient).changePlaybackMode("token", PlaybackMode.SHUFFLE, "active-device");
        assertEquals("active-device", session.deviceId());
        assertEquals("track-1", session.currentTrackId());
    }

    @Test
    void playTrackShouldTransferInactiveDeviceBeforePlaying() {
        SpotifyBridgeProperties properties = new SpotifyBridgeProperties(
                true,
                "client-id",
                "client-secret",
                "http://127.0.0.1:8080/api/auth/spotify/callback",
                "bridge-user",
                ""
        );
        DefaultBridgePlaybackControlService service = new DefaultBridgePlaybackControlService(
                spotifyPlaybackClient,
                spotifyBridgeAuthService,
                playbackSessionService,
                properties
        );

        when(spotifyBridgeAuthService.getValidAccessToken()).thenReturn(Optional.of("token"));
        when(playbackSessionService.getActiveSession("demo-user")).thenReturn(Optional.empty());
        when(spotifyPlaybackClient.getAvailableDevices("token")).thenReturn(List.of(
                new SpotifyBridgeDevice("inactive-device", "Edge Player", false, false, "Computer", 80)
        ));
        when(spotifyPlaybackClient.getPlaybackState("token")).thenReturn(Optional.of(
                new SpotifyPlaybackState(null, 0, false, PlaybackMode.SEQUENTIAL, null)
        ));
        when(playbackSessionService.saveSession(
                eq("demo-user"),
                any(),
                eq("track-1"),
                any(),
                any(),
                eq(0),
                eq(true),
                eq(PlaybackMode.SEQUENTIAL),
                eq("inactive-device")
        )).thenReturn(new PlaybackSessionDto(
                "session-4",
                "track-1",
                null,
                null,
                0,
                true,
                PlaybackMode.SEQUENTIAL,
                "inactive-device",
                LocalDateTime.now()
        ));

        PlaybackSessionDto session = service.playTrack("demo-user", "track-1", PlaybackMode.SEQUENTIAL, "inactive-device");

        verify(spotifyPlaybackClient).transferPlayback("token", "inactive-device", false);
        verify(spotifyPlaybackClient).playTrack("token", "track-1", "inactive-device");
        assertEquals("inactive-device", session.deviceId());
    }

    @Test
    void playTrackShouldFailWhenNoControllableDevicesExist() {
        SpotifyBridgeProperties properties = new SpotifyBridgeProperties(
                true,
                "client-id",
                "client-secret",
                "http://127.0.0.1:8080/api/auth/spotify/callback",
                "bridge-user",
                ""
        );
        DefaultBridgePlaybackControlService service = new DefaultBridgePlaybackControlService(
                spotifyPlaybackClient,
                spotifyBridgeAuthService,
                playbackSessionService,
                properties
        );

        when(spotifyBridgeAuthService.getValidAccessToken()).thenReturn(Optional.of("token"));
        when(spotifyPlaybackClient.getAvailableDevices("token")).thenReturn(List.of());

        assertThrows(IllegalStateException.class, () ->
                service.playTrack("demo-user", "track-1", PlaybackMode.SEQUENTIAL, null)
        );

        verify(spotifyPlaybackClient, never()).playTrack(anyString(), anyString(), any());
    }

    @Test
    void transferPlaybackShouldPreferExplicitDeviceId() {
        SpotifyBridgeProperties properties = new SpotifyBridgeProperties(
                true,
                "client-id",
                "client-secret",
                "http://127.0.0.1:8080/api/auth/spotify/callback",
                "bridge-user",
                "default-device"
        );
        DefaultBridgePlaybackControlService service = new DefaultBridgePlaybackControlService(
                spotifyPlaybackClient,
                spotifyBridgeAuthService,
                playbackSessionService,
                properties
        );
        PlaybackSessionDto current = new PlaybackSessionDto(
                "session-2",
                "track-2",
                "playlist-1",
                1,
                5000,
                true,
                PlaybackMode.SEQUENTIAL,
                "default-device",
                LocalDateTime.now()
        );

        when(spotifyBridgeAuthService.getValidAccessToken()).thenReturn(Optional.of("token"));
        when(playbackSessionService.getActiveSession("demo-user")).thenReturn(Optional.of(current));
        when(spotifyPlaybackClient.getAvailableDevices("token")).thenReturn(List.of(
                new SpotifyBridgeDevice("default-device", "Default", true, false, "Computer", 50),
                new SpotifyBridgeDevice("explicit-device", "Speaker", false, false, "Speaker", 40)
        ));
        when(playbackSessionService.saveSession(
                eq("demo-user"),
                eq("session-2"),
                eq("track-2"),
                eq("playlist-1"),
                eq(1),
                eq(5000),
                eq(true),
                eq(PlaybackMode.SEQUENTIAL),
                eq("explicit-device")
        )).thenReturn(new PlaybackSessionDto(
                "session-2",
                "track-2",
                "playlist-1",
                1,
                5000,
                true,
                PlaybackMode.SEQUENTIAL,
                "explicit-device",
                LocalDateTime.now()
        ));

        PlaybackSessionDto session = service.transferPlayback("demo-user", "explicit-device", false);

        verify(spotifyPlaybackClient).transferPlayback("token", "explicit-device", false);
        assertEquals("explicit-device", session.deviceId());
    }

    @Test
    void transferPlaybackShouldFailWhenSelectedDeviceIsOffline() {
        SpotifyBridgeProperties properties = new SpotifyBridgeProperties(
                true,
                "client-id",
                "client-secret",
                "http://127.0.0.1:8080/api/auth/spotify/callback",
                "bridge-user",
                "default-device"
        );
        DefaultBridgePlaybackControlService service = new DefaultBridgePlaybackControlService(
                spotifyPlaybackClient,
                spotifyBridgeAuthService,
                playbackSessionService,
                properties
        );
        PlaybackSessionDto current = new PlaybackSessionDto(
                "session-7",
                "track-2",
                "playlist-1",
                1,
                5000,
                true,
                PlaybackMode.SEQUENTIAL,
                "default-device",
                LocalDateTime.now()
        );

        when(spotifyBridgeAuthService.getValidAccessToken()).thenReturn(Optional.of("token"));
        when(playbackSessionService.getActiveSession("demo-user")).thenReturn(Optional.of(current));
        when(spotifyPlaybackClient.getAvailableDevices("token")).thenReturn(List.of(
                new SpotifyBridgeDevice("default-device", "Default", true, false, "Computer", 50)
        ));

        assertThrows(
                SpotifyPlaybackUnavailableException.class,
                () -> service.transferPlayback("demo-user", "missing-device", false)
        );

        verify(spotifyPlaybackClient, never()).transferPlayback(anyString(), anyString(), anyBoolean());
    }

    @Test
    void playTrackShouldResumeWhenSameTrackIsPausedOnResolvedDevice() {
        SpotifyBridgeProperties properties = new SpotifyBridgeProperties(
                true,
                "client-id",
                "client-secret",
                "http://127.0.0.1:8080/api/auth/spotify/callback",
                "bridge-user",
                ""
        );
        DefaultBridgePlaybackControlService service = new DefaultBridgePlaybackControlService(
                spotifyPlaybackClient,
                spotifyBridgeAuthService,
                playbackSessionService,
                properties
        );
        PlaybackSessionDto current = new PlaybackSessionDto(
                "session-3",
                "track-3",
                "playlist-3",
                2,
                42000,
                false,
                PlaybackMode.SEQUENTIAL,
                "active-device",
                LocalDateTime.now()
        );

        when(spotifyBridgeAuthService.getValidAccessToken()).thenReturn(Optional.of("token"));
        when(playbackSessionService.getActiveSession("demo-user")).thenReturn(Optional.of(current));
        when(spotifyPlaybackClient.getAvailableDevices("token")).thenReturn(List.of(
                new SpotifyBridgeDevice("active-device", "Edge Player", true, false, "Computer", 80)
        ));
        when(spotifyPlaybackClient.getPlaybackState("token")).thenReturn(Optional.of(
                new SpotifyPlaybackState("track-3", 42000, false, PlaybackMode.SEQUENTIAL, "active-device")
        ));
        when(playbackSessionService.saveSession(
                eq("demo-user"),
                eq("session-3"),
                eq("track-3"),
                eq("playlist-3"),
                eq(2),
                eq(42000),
                eq(true),
                eq(PlaybackMode.SEQUENTIAL),
                eq("active-device")
        )).thenReturn(new PlaybackSessionDto(
                "session-3",
                "track-3",
                "playlist-3",
                2,
                42000,
                true,
                PlaybackMode.SEQUENTIAL,
                "active-device",
                LocalDateTime.now()
        ));

        PlaybackSessionDto session = service.playTrack("demo-user", "track-3", PlaybackMode.SEQUENTIAL, "active-device");

        verify(spotifyPlaybackClient, never()).playTrack("token", "track-3", "active-device");
        verify(spotifyPlaybackClient, never()).transferPlayback("token", "active-device", false);
        verify(spotifyPlaybackClient).transferPlayback("token", "active-device", true);
        assertEquals(42000, session.currentPositionMs());
        assertEquals("active-device", session.deviceId());
    }

    @Test
    void syncPlaybackStateShouldPreserveLocalPlaylistPlaybackMode() {
        SpotifyBridgeProperties properties = new SpotifyBridgeProperties(
                true,
                "client-id",
                "client-secret",
                "http://127.0.0.1:8080/api/auth/spotify/callback",
                "bridge-user",
                ""
        );
        DefaultBridgePlaybackControlService service = new DefaultBridgePlaybackControlService(
                spotifyPlaybackClient,
                spotifyBridgeAuthService,
                playbackSessionService,
                properties
        );
        PlaybackSessionDto current = new PlaybackSessionDto(
                "session-6",
                "track-6",
                "playlist-6",
                0,
                12000,
                true,
                PlaybackMode.SHUFFLE,
                "active-device",
                LocalDateTime.now()
        );
        PlaybackSessionDto saved = new PlaybackSessionDto(
                "session-6",
                "track-6",
                "playlist-6",
                0,
                12500,
                true,
                PlaybackMode.SHUFFLE,
                "active-device",
                LocalDateTime.now()
        );

        when(spotifyBridgeAuthService.getValidAccessToken()).thenReturn(Optional.of("token"));
        when(playbackSessionService.getActiveSession("demo-user")).thenReturn(Optional.of(current));
        when(spotifyPlaybackClient.getPlaybackState("token")).thenReturn(Optional.of(
                new SpotifyPlaybackState("track-6", 12500, true, PlaybackMode.SEQUENTIAL, "active-device")
        ));
        when(playbackSessionService.saveSession(
                eq("demo-user"),
                eq("session-6"),
                eq("track-6"),
                eq("playlist-6"),
                eq(0),
                eq(12500),
                eq(true),
                eq(PlaybackMode.SHUFFLE),
                eq("active-device")
        )).thenReturn(saved);

        PlaybackSessionDto result = service.syncPlaybackState("demo-user");

        assertEquals(PlaybackMode.SHUFFLE, result.playbackMode());
    }
}
