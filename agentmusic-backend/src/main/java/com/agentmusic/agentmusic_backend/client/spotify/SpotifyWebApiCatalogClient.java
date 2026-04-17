package com.agentmusic.agentmusic_backend.client.spotify;

import com.agentmusic.agentmusic_backend.client.SpotifyCatalogClient;
import com.agentmusic.agentmusic_backend.domain.Artist;
import com.agentmusic.agentmusic_backend.domain.Track;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class SpotifyWebApiCatalogClient implements SpotifyCatalogClient {

    private static final String API_BASE_URL = "https://api.spotify.com/v1";
    private static final Logger log = LoggerFactory.getLogger(SpotifyWebApiCatalogClient.class);

    private final WebClient webClient;
    private final Clock clock;

    public SpotifyWebApiCatalogClient(WebClient.Builder webClientBuilder, Clock clock) {
        this.webClient = SpotifyWebClientFactory.create(webClientBuilder, API_BASE_URL);
        this.clock = clock;
    }

    @Override
    public Optional<Track> getTrack(String trackId, String accessToken) {
        TrackResponse response = webClient.get()
                .uri("/tracks/{trackId}", trackId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(TrackResponse.class)
                .onErrorResume(error -> {
                    log.warn("Spotify track lookup failed for trackId={}", trackId, error);
                    return Mono.empty();
                })
                .block();

        return Optional.ofNullable(response).map(this::toDomainTrack);
    }

    @Override
    public Optional<Artist> getArtist(String artistId, String accessToken) {
        ArtistResponse response = webClient.get()
                .uri("/artists/{artistId}", artistId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(ArtistResponse.class)
                .onErrorResume(error -> {
                    log.warn("Spotify artist lookup failed for artistId={}", artistId, error);
                    return Mono.empty();
                })
                .block();

        return Optional.ofNullable(response).map(this::toDomainArtist);
    }

    @Override
    public List<Track> searchTracks(String query, String accessToken, int limit) {
        SearchTracksResponse response = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/search")
                        .queryParam("q", query)
                        .queryParam("type", "track")
                        .queryParam("limit", limit)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(SearchTracksResponse.class)
                .onErrorResume(error -> {
                    log.warn("Spotify track search failed for query='{}'", query, error);
                    return Mono.just(new SearchTracksResponse(new TracksPage(List.of())));
                })
                .block();

        if (response == null || response.tracks() == null || response.tracks().items() == null) {
            return List.of();
        }
        return response.tracks().items().stream()
                .map(this::toDomainTrack)
                .toList();
    }

    private Track toDomainTrack(TrackResponse response) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneId.systemDefault());
        return new Track(
                response.id(),
                response.name(),
                response.firstArtistId(),
                response.album() == null ? null : response.album().name(),
                response.album() == null ? null : response.album().id(),
                response.durationMs(),
                response.previewUrl(),
                response.firstAlbumImageUrl(),
                now,
                now
        );
    }

    private Artist toDomainArtist(ArtistResponse response) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneId.systemDefault());
        Integer followers = response.followers() == null ? null : response.followers().total();
        return new Artist(
                response.id(),
                response.name(),
                null,
                response.firstImageUrl(),
                followers,
                now
        );
    }

    private record SearchTracksResponse(TracksPage tracks) {
    }

    private record TracksPage(List<TrackResponse> items) {
    }

    private record TrackResponse(
            String id,
            String name,
            AlbumResponse album,
            List<SimpleArtistResponse> artists,
            Integer durationMs,
            String previewUrl
    ) {
        @com.fasterxml.jackson.annotation.JsonProperty("duration_ms")
        public Integer durationMs() {
            return durationMs;
        }

        @com.fasterxml.jackson.annotation.JsonProperty("preview_url")
        public String previewUrl() {
            return previewUrl;
        }

        public String firstArtistId() {
            if (artists == null || artists.isEmpty()) {
                return null;
            }
            return artists.getFirst().id();
        }

        public String firstAlbumImageUrl() {
            if (album == null || album.images() == null || album.images().isEmpty()) {
                return null;
            }
            return album.images().getFirst().url();
        }
    }

    private record AlbumResponse(String id, String name, List<ImageResponse> images) {
    }

    private record SimpleArtistResponse(String id, String name) {
    }

    private record ArtistResponse(
            String id,
            String name,
            FollowersResponse followers,
            List<ImageResponse> images
    ) {
        public String firstImageUrl() {
            if (images == null || images.isEmpty()) {
                return null;
            }
            return images.getFirst().url();
        }
    }

    private record FollowersResponse(Integer total) {
    }

    private record ImageResponse(String url) {
    }
}
