package com.agentmusic.agentmusic_backend.integration.spotify;

import com.agentmusic.agentmusic_backend.integration.spotify.SpotifyCatalogClient;
import com.agentmusic.agentmusic_backend.domain.Artist;
import com.agentmusic.agentmusic_backend.domain.Track;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class SpotifyWebApiCatalogClient implements SpotifyCatalogClient {

    private static final String API_BASE_URL = "https://api.spotify.com/v1";
    private static final Logger log = LoggerFactory.getLogger(SpotifyWebApiCatalogClient.class);

    private final WebClient webClient;
    private final Clock clock;
    private final String searchMarket;

    public SpotifyWebApiCatalogClient(
            WebClient.Builder webClientBuilder,
            Clock clock,
            @Value("${spotify.search.market:}") String searchMarket
    ) {
        this.webClient = SpotifyWebClientFactory.create(webClientBuilder, API_BASE_URL);
        this.clock = clock;
        this.searchMarket = searchMarket == null ? "" : searchMarket.trim();
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
    public List<Artist> searchArtists(String query, String accessToken, int limit) {
        SearchArtistsResponse response = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/search")
                        .queryParam("q", query)
                        .queryParam("type", "artist")
                        .queryParam("limit", limit)
                        .queryParamIfPresent(
                                "market",
                                StringUtils.hasText(searchMarket) ? Optional.of(searchMarket) : Optional.empty()
                        )
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(SearchArtistsResponse.class)
                .onErrorResume(error -> {
                    log.warn("Spotify artist search failed for query='{}'", query, error);
                    return Mono.just(new SearchArtistsResponse(new ArtistsPage(List.of())));
                })
                .block();

        if (response == null || response.artists() == null || response.artists().items() == null) {
            return List.of();
        }
        return response.artists().items().stream()
                .map(this::toDomainArtist)
                .toList();
    }

    @Override
    public List<Track> getArtistTopTracks(String artistId, String accessToken, int limit) {
        ArtistTopTracksResponse response = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/artists/{artistId}/top-tracks")
                        .queryParamIfPresent(
                                "market",
                                StringUtils.hasText(searchMarket) ? Optional.of(searchMarket) : Optional.of("TW")
                        )
                        .build(artistId))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(ArtistTopTracksResponse.class)
                .onErrorResume(error -> {
                    log.warn("Spotify artist top tracks lookup failed for artistId={}", artistId, error);
                    return Mono.just(new ArtistTopTracksResponse(List.of()));
                })
                .block();

        if (response == null || response.tracks() == null) {
            return List.of();
        }
        return response.tracks().stream()
                .map(this::toDomainTrack)
                .limit(limit)
                .toList();
    }

    @Override
    public List<String> getArtistAlbumIds(String artistId, String accessToken, int limit) {
        int remaining = Math.max(0, limit);
        int offset = 0;
        java.util.LinkedHashSet<String> albumIds = new java.util.LinkedHashSet<>();
        while (remaining > 0) {
            int pageSize = Math.min(10, remaining);
            int currentOffset = offset;
            ArtistAlbumsResponse response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/artists/{artistId}/albums")
                            .queryParam("include_groups", "album,single")
                            .queryParam("limit", pageSize)
                            .queryParam("offset", currentOffset)
                            .queryParamIfPresent(
                                    "market",
                                    StringUtils.hasText(searchMarket) ? Optional.of(searchMarket) : Optional.empty()
                            )
                            .build(artistId))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(ArtistAlbumsResponse.class)
                    .onErrorResume(error -> {
                        log.warn("Spotify artist albums lookup failed for artistId={} offset={}", artistId, currentOffset, error);
                        return Mono.just(new ArtistAlbumsResponse(List.of()));
                    })
                    .block();

            if (response == null || response.items() == null || response.items().isEmpty()) {
                break;
            }
            response.items().stream()
                    .map(AlbumSummaryResponse::id)
                    .filter(StringUtils::hasText)
                    .forEach(albumIds::add);
            if (response.items().size() < pageSize) {
                break;
            }
            offset += pageSize;
            remaining = limit - albumIds.size();
        }
        return albumIds.stream().limit(limit).toList();
    }

    @Override
    public List<Track> getAlbumTracks(String albumId, String accessToken, int limit) {
        AlbumDetailsResponse response = webClient.get()
                .uri("/albums/{albumId}", albumId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(AlbumDetailsResponse.class)
                .onErrorResume(error -> {
                    log.warn("Spotify album lookup failed for albumId={}", albumId, error);
                    return Mono.empty();
                })
                .block();

        if (response == null || response.tracks() == null || response.tracks().items() == null) {
            return List.of();
        }
        return response.tracks().items().stream()
                .map(track -> toDomainTrack(response, track))
                .limit(limit)
                .toList();
    }

    @Override
    public List<Track> searchTracks(String query, String accessToken, int limit) {
        SearchTracksResponse response = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/search")
                        .queryParam("q", query)
                        .queryParam("type", "track")
                        .queryParam("limit", limit)
                        .queryParamIfPresent(
                                "market",
                                StringUtils.hasText(searchMarket) ? Optional.of(searchMarket) : Optional.empty()
                        )
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

    private Track toDomainTrack(AlbumDetailsResponse album, AlbumTrackResponse track) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneId.systemDefault());
        return new Track(
                track.id(),
                track.name(),
                track.firstArtistId(),
                album.name(),
                album.id(),
                track.durationMs(),
                track.previewUrl(),
                album.firstImageUrl(),
                now,
                now
        );
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

    private record SearchArtistsResponse(ArtistsPage artists) {
    }

    private record TracksPage(List<TrackResponse> items) {
    }

    private record ArtistsPage(List<ArtistResponse> items) {
    }

    private record ArtistTopTracksResponse(List<TrackResponse> tracks) {
    }

    private record ArtistAlbumsResponse(List<AlbumSummaryResponse> items) {
    }

    private record AlbumSummaryResponse(String id) {
    }

    private record AlbumDetailsResponse(
            String id,
            String name,
            List<ImageResponse> images,
            AlbumTracksPage tracks
    ) {
        public String firstImageUrl() {
            if (images == null || images.isEmpty()) {
                return null;
            }
            return images.getFirst().url();
        }
    }

    private record AlbumTracksPage(List<AlbumTrackResponse> items) {
    }

    private record AlbumTrackResponse(
            String id,
            String name,
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
