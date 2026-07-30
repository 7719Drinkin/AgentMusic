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
        when(spotifyCatalogClient.searchTracks(eq("track:\u6cb3 artist:\u5f20\u96e8\u751f"), eq("token"), eq(8)))
                .thenReturn(List.of(target, targetAlt, noise));
        when(spotifyCatalogClient.searchTracks(eq("track:\u6cb3"), eq("token"), eq(8)))
                .thenReturn(List.of(targetAlt, noise));
        when(spotifyCatalogClient.searchTracks(eq("\u5f20\u96e8\u751f \u6cb3"), eq("token"), eq(8)))
                .thenReturn(List.of(target, targetAlt, noise));
        when(spotifyCatalogClient.searchTracks(eq("\u6cb3"), eq("token"), eq(8)))
                .thenReturn(List.of(targetAlt, noise));
        when(spotifyCatalogClient.searchTracks(eq("artist:\u5f20\u96e8\u751f"), eq("token"), eq(8)))
                .thenReturn(List.of(artistTrack, target, noise));
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
    void searchTracksShouldPreserveStructuredSpotifyQuery() {
        DefaultMusicMetadataService service = createService();

        Track exact = track("track-dizzy-1", "\u53d1\u6655", "artist-zhang", "\u4e24\u4f0a\u6218\u4e89\u767d\u8272\u624d\u60c5");
        when(spotifyBridgeAuthService.getValidAccessToken()).thenReturn(Optional.of("token"));
        when(spotifyCatalogClient.searchTracks(
                eq("track:\u53d1\u6655 artist:\u5f20\u96e8\u751f album:\u4e24\u4f0a\u6218\u4e89\u767d\u8272\u624d\u60c5"),
                eq("token"),
                eq(8)
        )).thenReturn(List.of(exact));
        when(trackRepository.save(any(Track.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<Track> results = service.searchTracks(
                "track:\u53d1\u6655 artist:\u5f20\u96e8\u751f album:\u4e24\u4f0a\u6218\u4e89\u767d\u8272\u624d\u60c5",
                5
        );

        assertThat(results).extracting(Track::title).containsExactly("\u53d1\u6655");
        verify(spotifyCatalogClient).searchTracks(
                eq("track:\u53d1\u6655 artist:\u5f20\u96e8\u751f album:\u4e24\u4f0a\u6218\u4e89\u767d\u8272\u624d\u60c5"),
                eq("token"),
                eq(8)
        );
        verify(spotifyCatalogClient, never()).searchTracks(eq("\u53d1\u6655"), eq("token"), eq(8));
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

    @Test
    void searchTracksShouldAllowDeeperFetchForStructuredArtistOnlyQuery() {
        DefaultMusicMetadataService service = createService();

        when(spotifyBridgeAuthService.getValidAccessToken()).thenReturn(Optional.of("token"));
        when(spotifyCatalogClient.searchTracks(eq("artist:\u5f20\u96e8\u751f"), eq("token"), eq(20)))
                .thenReturn(List.of(
                        track("track-artist-1", "\u4e00\u5929\u5230\u665a\u6e38\u6cf3\u7684\u9b5a", "artist-zhang", "\u4e00\u5929\u5230\u665a\u6e38\u6cf3\u7684\u9b5a"),
                        track("track-artist-2", "\u53e3\u662f\u5fc3\u975e", "artist-zhang", "\u53e3\u662f\u5fc3\u975e")
                ));
        when(trackRepository.save(any(Track.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<Track> results = service.searchTracks("artist:\u5f20\u96e8\u751f", 20);

        assertThat(results).extracting(Track::title)
                .contains("\u4e00\u5929\u5230\u665a\u6e38\u6cf3\u7684\u9b5a", "\u53e3\u662f\u5fc3\u975e");
        verify(spotifyCatalogClient).searchTracks(eq("artist:\u5f20\u96e8\u751f"), eq("token"), eq(20));
        verify(spotifyCatalogClient, never()).searchTracks(eq("artist:\u5f20\u96e8\u751f"), eq("token"), eq(10));
    }

    @Test
    void searchArtistsShouldFetchAndPersistSpotifyArtists() {
        DefaultMusicMetadataService service = createService();

        when(spotifyBridgeAuthService.getValidAccessToken()).thenReturn(Optional.of("token"));
        when(spotifyCatalogClient.searchArtists(eq("\u5f20\u96e8\u751f"), eq("token"), eq(5)))
                .thenReturn(List.of(artist("artist-zhang", "Zhang Yu Sheng")));
        when(artistRepository.save(any(Artist.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<Artist> results = service.searchArtists("\u5f20\u96e8\u751f", 5);

        assertThat(results).extracting(Artist::artistId).containsExactly("artist-zhang");
        verify(spotifyCatalogClient).searchArtists(eq("\u5f20\u96e8\u751f"), eq("token"), eq(5));
        verify(artistRepository).save(any(Artist.class));
    }

    @Test
    void getArtistCatalogTracksShouldCombineTopTracksAndAlbumTracks() {
        DefaultMusicMetadataService service = createService();

        when(spotifyBridgeAuthService.getValidAccessToken()).thenReturn(Optional.of("token"));
        when(spotifyCatalogClient.getArtistAlbumIds(eq("artist-zhang"), eq("token"), eq(20)))
                .thenReturn(List.of("album-1", "album-2"));
        when(spotifyCatalogClient.getAlbumTracks(eq("album-1"), eq("token"), eq(50)))
                .thenReturn(List.of(
                        track("track-top-1", "\u53e3\u662f\u5fc3\u975e", "artist-zhang", "\u53e3\u662f\u5fc3\u975e"),
                        track("track-album-1", "\u5929\u5929\u60f3\u4f60", "artist-zhang", "\u5929\u5929\u60f3\u4f60"),
                        track("track-noise-1", "20 Min", "artist-other", "Luv Is Rage 2")
                ));
        when(spotifyCatalogClient.getAlbumTracks(eq("album-2"), eq("token"), eq(50)))
                .thenReturn(List.of(
                        track("track-top-2", "\u5927\u6d77", "artist-zhang", "\u5927\u6d77"),
                        track("track-album-2", "\u6211\u7684\u672a\u4f86\u4e0d\u662f\u5922", "artist-zhang", "\u6211\u7684\u672a\u4f86\u4e0d\u662f\u5922")
                ));
        when(trackRepository.save(any(Track.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<Track> results = service.getArtistCatalogTracks("artist-zhang", 10);

        assertThat(results).extracting(Track::trackId)
                .containsExactly("track-top-1", "track-top-2", "track-album-1", "track-album-2");
        assertThat(results).extracting(Track::artistId).containsOnly("artist-zhang");
        verify(spotifyCatalogClient).getArtistAlbumIds(eq("artist-zhang"), eq("token"), eq(20));
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
