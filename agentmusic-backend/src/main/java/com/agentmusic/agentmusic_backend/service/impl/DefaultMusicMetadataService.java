package com.agentmusic.agentmusic_backend.service.impl;

import com.agentmusic.agentmusic_backend.integration.spotify.SpotifyCatalogClient;
import com.agentmusic.agentmusic_backend.domain.Artist;
import com.agentmusic.agentmusic_backend.domain.Track;
import com.agentmusic.agentmusic_backend.persistence.repository.ArtistRepository;
import com.agentmusic.agentmusic_backend.persistence.repository.TrackRepository;
import com.agentmusic.agentmusic_backend.service.MusicMetadataService;
import com.agentmusic.agentmusic_backend.service.SpotifyBridgeAuthService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class DefaultMusicMetadataService implements MusicMetadataService {

    private final TrackRepository trackRepository;
    private final ArtistRepository artistRepository;
    private final SpotifyCatalogClient spotifyCatalogClient;
    private final SpotifyBridgeAuthService spotifyBridgeAuthService;
    private final SearchQueryRefiner searchQueryRefiner;
    private final Clock clock;

    public DefaultMusicMetadataService(
            TrackRepository trackRepository,
            ArtistRepository artistRepository,
            SpotifyCatalogClient spotifyCatalogClient,
            SpotifyBridgeAuthService spotifyBridgeAuthService,
            SearchQueryRefiner searchQueryRefiner,
            Clock clock
    ) {
        this.trackRepository = trackRepository;
        this.artistRepository = artistRepository;
        this.spotifyCatalogClient = spotifyCatalogClient;
        this.spotifyBridgeAuthService = spotifyBridgeAuthService;
        this.searchQueryRefiner = searchQueryRefiner;
        this.clock = clock;
    }

    @Override
    public Track saveTrack(Track track) {
        LocalDateTime now = LocalDateTime.now(clock);
        Track normalized = new Track(
                track.trackId(),
                track.title(),
                track.artistId(),
                track.albumName(),
                track.albumId(),
                track.durationMs(),
                track.previewUrl(),
                track.albumImageUrl(),
                now,
                now
        );
        return trackRepository.save(normalized);
    }

    @Override
    public Optional<Track> findTrack(String trackId) {
        return trackRepository.findById(trackId)
                .map(track -> {
                    Track touched = new Track(
                            track.trackId(),
                            track.title(),
                            track.artistId(),
                            track.albumName(),
                            track.albumId(),
                            track.durationMs(),
                            track.previewUrl(),
                            track.albumImageUrl(),
                            track.updatedAt(),
                            LocalDateTime.now(clock)
                    );
                    trackRepository.save(touched);
                    return touched;
                });
    }

    @Override
    public Optional<Track> findTrackOrFetch(String trackId) {
        Optional<Track> local = findTrack(trackId);
        if (local.isPresent()) {
            return local;
        }
        Optional<Track> spotifyTrack = spotifyBridgeAuthService.getValidAccessToken()
                .flatMap(accessToken -> spotifyCatalogClient.getTrack(trackId, accessToken))
                .map(this::saveTrack);
        if (spotifyTrack.isPresent()) {
            return spotifyTrack;
        }
        return Optional.empty();
    }

    @Override
    public Artist saveArtist(Artist artist) {
        Artist normalized = new Artist(
                artist.artistId(),
                artist.name(),
                artist.bio(),
                artist.imageUrl(),
                artist.followers(),
                LocalDateTime.now(clock)
        );
        return artistRepository.save(normalized);
    }

    @Override
    public Optional<Artist> findArtist(String artistId) {
        return artistRepository.findById(artistId);
    }

    @Override
    public Optional<Artist> findArtistOrFetch(String artistId) {
        Optional<Artist> local = findArtist(artistId);
        if (local.isPresent()) {
            return local;
        }
        Optional<Artist> spotifyArtist = spotifyBridgeAuthService.getValidAccessToken()
                .flatMap(accessToken -> spotifyCatalogClient.getArtist(artistId, accessToken))
                .map(this::saveArtist);
        if (spotifyArtist.isPresent()) {
            return spotifyArtist;
        }
        return Optional.empty();
    }

    @Override
    public List<Track> searchTracks(String query, int limit) {
        SearchQueryRefiner.SearchQueryHints hints = searchQueryRefiner.analyze(query);
        if (hints.candidates().isEmpty()) {
            return List.of();
        }

        Optional<String> accessToken = spotifyBridgeAuthService.getValidAccessToken();
        if (accessToken.isEmpty()) {
            return List.of();
        }

        Map<String, Track> aggregated = new LinkedHashMap<>();
        // Spotify search has been unstable for long CJK natural-language queries when the single-request page
        // size is too large. Keep each upstream candidate fetch in a conservative band, then merge locally.
        int perCandidateLimit = Math.min(Math.max(limit, 8), 10);
        for (String candidate : hints.candidates()) {
            List<Track> spotifyTracks = spotifyCatalogClient.searchTracks(candidate, accessToken.get(), perCandidateLimit).stream()
                    .map(this::saveTrack)
                    .toList();
            for (Track spotifyTrack : spotifyTracks) {
                aggregated.putIfAbsent(spotifyTrack.trackId(), spotifyTrack);
            }
        }

        if (aggregated.isEmpty()) {
            return List.of();
        }

        Map<String, String> artistNameCache = new java.util.HashMap<>();
        return aggregated.values().stream()
                .sorted(Comparator
                        .comparingInt((Track track) -> scoreTrack(track, hints, artistNameCache))
                        .reversed()
                        .thenComparing(Track::title, String.CASE_INSENSITIVE_ORDER))
                .limit(limit)
                .toList();
    }

    private int scoreTrack(
            Track track,
            SearchQueryRefiner.SearchQueryHints hints,
            Map<String, String> artistNameCache
    ) {
        int score = 0;
        String normalizedTitle = normalizeForMatching(track.title());
        String normalizedAlbum = normalizeForMatching(track.albumName());
        String normalizedArtist = normalizeForMatching(resolveArtistName(track.artistId(), artistNameCache));

        for (String explicitTitle : hints.explicitTitles()) {
            String normalizedExplicitTitle = normalizeForMatching(explicitTitle);
            if (normalizedExplicitTitle.isBlank()) {
                continue;
            }
            if (normalizedTitle.equals(normalizedExplicitTitle)) {
                score += 200;
            } else if (normalizedTitle.contains(normalizedExplicitTitle)) {
                score += 120;
            } else if (normalizedAlbum.contains(normalizedExplicitTitle)) {
                score += 35;
            }
        }

        for (String keyword : hints.contextKeywords()) {
            String normalizedKeyword = normalizeForMatching(keyword);
            if (normalizedKeyword.isBlank()) {
                continue;
            }
            if (normalizedArtist.equals(normalizedKeyword)) {
                score += 90;
            } else if (normalizedArtist.contains(normalizedKeyword)) {
                score += 60;
            } else if (normalizedTitle.contains(normalizedKeyword)) {
                score += 24;
            } else if (normalizedAlbum.contains(normalizedKeyword)) {
                score += 12;
            }
        }

        return score;
    }

    private String resolveArtistName(String artistId, Map<String, String> artistNameCache) {
        if (artistId == null || artistId.isBlank()) {
            return "";
        }
        return artistNameCache.computeIfAbsent(
                artistId,
                id -> findArtistOrFetch(id).map(Artist::name).orElse("")
        );
    }

    private String normalizeForMatching(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Punct}]+", "");
    }
}
