package com.agentmusic.agentmusic_backend;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import com.agentmusic.agentmusic_backend.domain.PlaybackMode;
import com.agentmusic.agentmusic_backend.web.dto.PlaybackSessionDto;
import com.agentmusic.agentmusic_backend.web.dto.PlaylistDto;
import com.agentmusic.agentmusic_backend.web.dto.PlaylistTrackDto;
import com.agentmusic.agentmusic_backend.web.dto.TrackDto;
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
    void playTrackShouldPropagateBridgeFailure() {
        when(bridgePlaybackControlService.playTrack("demo-user", "track-2", PlaybackMode.SHUFFLE, null))
                .thenThrow(new RuntimeException("spotify unavailable"));

        assertThrows(RuntimeException.class, () -> playbackApplicationService.playTrack(
                "demo-user",
                "track-2",
                "playlist-9",
                3,
                null,
                PlaybackMode.SHUFFLE
        ));

        verify(playbackSessionService, never()).saveSession(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                anyBoolean(),
                any(),
                any()
        );
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
                                new TrackDto("track-1", "Song A", "artist-1", "album", "album-1", 180000, null, null),
                                LocalDateTime.now()),
                        new PlaylistTrackDto("pt-2", "playlist-1", 1,
                                new TrackDto("track-2", "Song B", "artist-2", "album", "album-2", 200000, null, null),
                                LocalDateTime.now())
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

    @Test
    void nextTrackShouldNotWrapWhenPlaybackModeIsSequential() {
        PlaybackSessionDto current = new PlaybackSessionDto(
                "session-4",
                "track-2",
                "playlist-1",
                1,
                12000,
                true,
                PlaybackMode.SEQUENTIAL,
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
                                new TrackDto("track-1", "Song A", "artist-1", "album", "album-1", 180000, null, null),
                                LocalDateTime.now()),
                        new PlaylistTrackDto("pt-2", "playlist-1", 1,
                                new TrackDto("track-2", "Song B", "artist-2", "album", "album-2", 200000, null, null),
                                LocalDateTime.now())
                )
        );

        when(playbackSessionService.getActiveSession("demo-user")).thenReturn(Optional.of(current));
        when(playlistService.getPlaylistById("playlist-1")).thenReturn(Optional.of(playlist));

        PlaybackSessionDto result = playbackApplicationService.nextTrack("demo-user", null);

        assertEquals("track-2", result.currentTrackId());
        assertEquals(1, result.currentTrackIndex());
        verifyNoInteractions(bridgePlaybackControlService);
    }

    @Test
    void syncBridgeStateShouldRebindToNewestPlaylistWhenRemoteTrackChanges() {
        PlaybackSessionDto previous = new PlaybackSessionDto(
                "session-5",
                "old-track",
                "old-playlist",
                3,
                12000,
                true,
                PlaybackMode.SEQUENTIAL,
                "device-1",
                LocalDateTime.now()
        );
        PlaybackSessionDto synced = new PlaybackSessionDto(
                "session-5",
                "shared-track",
                "old-playlist",
                3,
                1500,
                true,
                PlaybackMode.SEQUENTIAL,
                "device-1",
                LocalDateTime.now()
        );
        PlaybackSessionDto reconciled = new PlaybackSessionDto(
                "session-5",
                "shared-track",
                "new-playlist",
                0,
                1500,
                true,
                PlaybackMode.SEQUENTIAL,
                "device-1",
                LocalDateTime.now()
        );
        PlaylistDto newPlaylist = playlist(
                "new-playlist",
                new PlaylistTrackDto("pt-new", "new-playlist", 0,
                        new TrackDto("shared-track", "Song A", "artist-1", "album", "album-1", 180000, null, null),
                        LocalDateTime.now())
        );
        PlaylistDto oldPlaylist = playlist(
                "old-playlist",
                new PlaylistTrackDto("pt-old", "old-playlist", 3,
                        new TrackDto("shared-track", "Song A", "artist-1", "album", "album-1", 180000, null, null),
                        LocalDateTime.now())
        );

        when(playbackSessionService.getActiveSession("demo-user")).thenReturn(Optional.of(previous));
        when(bridgePlaybackControlService.syncPlaybackState("demo-user")).thenReturn(synced);
        when(playlistService.getRecentPlaylists("demo-user", 10)).thenReturn(List.of(newPlaylist, oldPlaylist));
        when(playbackSessionService.saveSession(
                eq("demo-user"),
                eq("session-5"),
                eq("shared-track"),
                eq("new-playlist"),
                eq(0),
                eq(1500),
                eq(true),
                eq(PlaybackMode.SEQUENTIAL),
                eq("device-1")
        )).thenReturn(reconciled);

        Optional<PlaybackSessionDto> result = playbackApplicationService.syncBridgeState("demo-user");

        assertEquals("new-playlist", result.orElseThrow().currentPlaylistId());
        assertEquals(0, result.orElseThrow().currentTrackIndex());
    }

    @Test
    void syncBridgeStateShouldKeepPlaylistWhenRemoteTrackStillMatchesPreviousSession() {
        PlaybackSessionDto previous = new PlaybackSessionDto(
                "session-6",
                "track-1",
                "playlist-1",
                1,
                12000,
                true,
                PlaybackMode.SEQUENTIAL,
                "device-1",
                LocalDateTime.now()
        );
        PlaybackSessionDto synced = new PlaybackSessionDto(
                "session-6",
                "track-1",
                "playlist-1",
                1,
                1500,
                true,
                PlaybackMode.SEQUENTIAL,
                "device-1",
                LocalDateTime.now()
        );
        PlaylistDto playlist = playlist(
                "playlist-1",
                new PlaylistTrackDto("pt-1", "playlist-1", 1,
                        new TrackDto("track-1", "Song A", "artist-1", "album", "album-1", 180000, null, null),
                        LocalDateTime.now())
        );

        when(playbackSessionService.getActiveSession("demo-user")).thenReturn(Optional.of(previous));
        when(bridgePlaybackControlService.syncPlaybackState("demo-user")).thenReturn(synced);
        when(playlistService.getPlaylistById("playlist-1")).thenReturn(Optional.of(playlist));

        Optional<PlaybackSessionDto> result = playbackApplicationService.syncBridgeState("demo-user");

        assertEquals("playlist-1", result.orElseThrow().currentPlaylistId());
        assertEquals(1, result.orElseThrow().currentTrackIndex());
    }

    private PlaylistDto playlist(String playlistId, PlaylistTrackDto... tracks) {
        return new PlaylistDto(
                playlistId,
                "推荐歌单",
                1,
                LocalDateTime.now(),
                List.of(tracks)
        );
    }
}
