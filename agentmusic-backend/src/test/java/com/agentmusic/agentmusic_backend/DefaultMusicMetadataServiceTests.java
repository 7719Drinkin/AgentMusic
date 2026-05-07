package com.agentmusic.agentmusic_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentmusic.agentmusic_backend.domain.Artist;
import com.agentmusic.agentmusic_backend.domain.Track;
import com.agentmusic.agentmusic_backend.integration.spotify.SpotifyCatalogClient;
import com.agentmusic.agentmusic_backend.persistence.repository.ArtistRepository;
import com.agentmusic.agentmusic_backend.persistence.repository.TrackRepository;
import com.agentmusic.agentmusic_backend.service.SpotifyBridgeAuthService;
import com.agentmusic.agentmusic_backend.service.impl.DefaultMusicMetadataService;
import com.agentmusic.agentmusic_backend.service.impl.SearchQueryRefiner;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultMusicMetadataServiceTests {

    @Mock
    private TrackRepository trackRepository;

    @Mock
    private ArtistRepository artistRepository;

    @Mock
    private SpotifyCatalogClient spotifyCatalogClient;

    @Mock
    private SpotifyBridgeAuthService spotifyBridgeAuthService;

    private final Clock clock = Clock.fixed(Instant.parse("2026-05-07T03:00:00Z"), ZoneOffset.UTC);

    @Test
    void searchTracksShouldPrioritizeExplicitTitleAndArtistMatches() {
        DefaultMusicMetadataService service = createService();

        Track target = track("track-he-1", "\u6cb3", "artist-zhang", "\u53e3\u662f\u5fc3\u975e");
        Track targetAlt = track("track-he-2", "\u6cb3", "artist-zhang", "\u7cbe\u9009");
        Track artistTrack = track("track-artist-1", "\u5927\u6d77", "artist-zhang", "\u81ea\u7531");
        Track noise = track("track-noise-1", "\u5f20\u4e09\u7684\u6b4c", "artist-other", "8\u53c8\u4e8c\u5206\u4e4b\u4e00");

        when(spotifyBridgeAuthService.getValidAccessToken()).thenReturn(Optional.of("token"));
        when(spotifyCatalogClient.searchTracks(eq("\u5f20\u96e8\u751f \u6cb3"), eq("token"), eq(8)))
                .thenReturn(List.of(target, targetAlt, noise));
        when(spotifyCatalogClient.searchTracks(eq("\u6cb3"), eq("token"), eq(8)))
                .thenReturn(List.of(targetAlt, noise));
        when(spotifyCatalogClient.searchTracks(eq("\u5f20\u96e8\u751f"), eq("token"), eq(8)))
                .thenReturn(List.of(artistTrack, target, noise));
        when(trackRepository.save(any(Track.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(artistRepository.findById("artist-zhang"))
                .thenReturn(Optional.of(artist("artist-zhang", "\u5f20\u96e8\u751f")));
        when(artistRepository.findById("artist-other"))
                .thenReturn(Optional.of(artist("artist-other", "\u674e\u5bff\u5168")));

        List<Track> results = service.searchTracks(
                "\u7ed9\u6211\u63a8\u8350\u5f20\u96e8\u751f\u7684\u300a\u6cb3\u300b\u4ee5\u53ca\u4ed6\u7684\u5176\u4ed6\u6b4c\u66f2",
                5
        );

        assertThat(results).extracting(Track::title)
                .startsWith("\u6cb3", "\u6cb3", "\u5927\u6d77");
        assertThat(results).extracting(Track::artistId)
                .startsWith("artist-zhang", "artist-zhang", "artist-zhang");
    }

    @Test
    void searchTracksShouldCapPerCandidateLimitForLargeRequests() {
        DefaultMusicMetadataService service = createService();

        when(spotifyBridgeAuthService.getValidAccessToken()).thenReturn(Optional.of("token"));
        when(spotifyCatalogClient.searchTracks(eq("\u6cb3"), eq("token"), eq(10)))
                .thenReturn(List.of(track("track-he-1", "\u6cb3", "artist-zhang", "\u53e3\u662f\u5fc3\u975e")));
        when(trackRepository.save(any(Track.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<Track> results = service.searchTracks("\u6cb3", 20);

        assertThat(results).extracting(Track::title).contains("\u6cb3");
        verify(spotifyCatalogClient).searchTracks(eq("\u6cb3"), eq("token"), eq(10));
        verify(spotifyCatalogClient, never()).searchTracks(eq("\u6cb3"), eq("token"), eq(20));
    }

    private DefaultMusicMetadataService createService() {
        return new DefaultMusicMetadataService(
                trackRepository,
                artistRepository,
                spotifyCatalogClient,
                spotifyBridgeAuthService,
                new SearchQueryRefiner(),
                clock
        );
    }

    private Track track(String trackId, String title, String artistId, String albumName) {
        return new Track(trackId, title, artistId, albumName, albumName + "-id", 180000, null, null, null, null);
    }

    private Artist artist(String artistId, String name) {
        return new Artist(artistId, name, null, null, null, null);
    }
}
