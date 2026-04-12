package com.agentmusic.agentmusic_backend.client.spotify;

import com.agentmusic.agentmusic_backend.client.SpotifyBridgeDevice;
import com.agentmusic.agentmusic_backend.client.SpotifyPlaybackClient;
import com.agentmusic.agentmusic_backend.client.SpotifyPlaybackState;
import com.agentmusic.agentmusic_backend.domain.PlaybackMode;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class SpotifyWebApiPlaybackClient implements SpotifyPlaybackClient {

    private static final String API_BASE_URL = "https://api.spotify.com/v1";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private final WebClient webClient;

    public SpotifyWebApiPlaybackClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl(API_BASE_URL).build();
    }

    @Override
    public Optional<SpotifyPlaybackState> getPlaybackState(String accessToken) {
        CurrentPlaybackResponse response = webClient.get()
                .uri("/me/player")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(CurrentPlaybackResponse.class)
                .timeout(REQUEST_TIMEOUT)
                .onErrorComplete()
                .block();

        if (response == null) {
            return Optional.empty();
        }
        return Optional.of(new SpotifyPlaybackState(
                response.item() == null ? null : response.item().id(),
                response.progressMs(),
                Boolean.TRUE.equals(response.isPlaying()),
                toPlaybackMode(response.shuffleState(), response.repeatState()),
                response.device() == null ? null : response.device().id()
        ));
    }

    @Override
    public List<SpotifyBridgeDevice> getAvailableDevices(String accessToken) {
        DevicesResponse response = webClient.get()
                .uri("/me/player/devices")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(DevicesResponse.class)
                .timeout(REQUEST_TIMEOUT)
                .onErrorReturn(new DevicesResponse(List.of()))
                .block();

        if (response == null || response.devices() == null) {
            return List.of();
        }
        return response.devices().stream()
                .map(device -> new SpotifyBridgeDevice(
                        device.id(),
                        device.name(),
                        Boolean.TRUE.equals(device.isActive()),
                        Boolean.TRUE.equals(device.isRestricted()),
                        device.type(),
                        device.volumePercent()
                ))
                .toList();
    }

    @Override
    public void transferPlayback(String accessToken, String deviceId, boolean play) {
        TransferPlaybackRequest body = new TransferPlaybackRequest(List.of(deviceId), play);
        webClient.put()
                .uri(uriBuilder -> uriBuilder.path("/me/player").build())
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .timeout(REQUEST_TIMEOUT)
                .block();
    }

    @Override
    public void playTrack(String accessToken, String trackId, String deviceId) {
        PlayRequest body = new PlayRequest(List.of("spotify:track:" + trackId));
        webClient.put()
                .uri(uriBuilder -> uriBuilder
                        .path("/me/player/play")
                        .queryParamIfPresent("device_id", Optional.ofNullable(blankToNull(deviceId)))
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .timeout(REQUEST_TIMEOUT)
                .block();
    }

    @Override
    public void resumePlayback(String accessToken, String deviceId) {
        webClient.put()
                .uri(uriBuilder -> uriBuilder
                        .path("/me/player/play")
                        .queryParamIfPresent("device_id", Optional.ofNullable(blankToNull(deviceId)))
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .toBodilessEntity()
                .timeout(REQUEST_TIMEOUT)
                .block();
    }

    @Override
    public void pause(String accessToken, String deviceId) {
        webClient.put()
                .uri(uriBuilder -> uriBuilder
                        .path("/me/player/pause")
                        .queryParamIfPresent("device_id", Optional.ofNullable(blankToNull(deviceId)))
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .toBodilessEntity()
                .timeout(REQUEST_TIMEOUT)
                .block();
    }

    @Override
    public void nextTrack(String accessToken, String deviceId) {
        webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/me/player/next")
                        .queryParamIfPresent("device_id", Optional.ofNullable(blankToNull(deviceId)))
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .toBodilessEntity()
                .timeout(REQUEST_TIMEOUT)
                .block();
    }

    @Override
    public void previousTrack(String accessToken, String deviceId) {
        webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/me/player/previous")
                        .queryParamIfPresent("device_id", Optional.ofNullable(blankToNull(deviceId)))
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .toBodilessEntity()
                .timeout(REQUEST_TIMEOUT)
                .block();
    }

    @Override
    public void seek(String accessToken, int positionMs, String deviceId) {
        webClient.put()
                .uri(uriBuilder -> uriBuilder
                        .path("/me/player/seek")
                        .queryParam("position_ms", positionMs)
                        .queryParamIfPresent("device_id", Optional.ofNullable(blankToNull(deviceId)))
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .toBodilessEntity()
                .timeout(REQUEST_TIMEOUT)
                .block();
    }

    @Override
    public void changePlaybackMode(String accessToken, PlaybackMode playbackMode, String deviceId) {
        boolean shuffle = playbackMode == PlaybackMode.SHUFFLE;
        String repeatState = switch (playbackMode) {
            case SEQUENTIAL, SHUFFLE -> "off";
            case LIST_LOOP -> "context";
            case SINGLE_LOOP -> "track";
        };

        webClient.put()
                .uri(uriBuilder -> uriBuilder
                        .path("/me/player/shuffle")
                        .queryParam("state", shuffle)
                        .queryParamIfPresent("device_id", Optional.ofNullable(blankToNull(deviceId)))
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .toBodilessEntity()
                .timeout(REQUEST_TIMEOUT)
                .block();

        webClient.put()
                .uri(uriBuilder -> uriBuilder
                        .path("/me/player/repeat")
                        .queryParam("state", repeatState)
                        .queryParamIfPresent("device_id", Optional.ofNullable(blankToNull(deviceId)))
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .toBodilessEntity()
                .timeout(REQUEST_TIMEOUT)
                .block();
    }

    private PlaybackMode toPlaybackMode(Boolean shuffleState, String repeatState) {
        if (Boolean.TRUE.equals(shuffleState)) {
            return PlaybackMode.SHUFFLE;
        }
        if ("track".equalsIgnoreCase(repeatState)) {
            return PlaybackMode.SINGLE_LOOP;
        }
        if ("context".equalsIgnoreCase(repeatState)) {
            return PlaybackMode.LIST_LOOP;
        }
        return PlaybackMode.SEQUENTIAL;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private record PlayRequest(List<String> uris) {
    }

    private record DevicesResponse(List<DeviceResponse> devices) {
    }

    private record TransferPlaybackRequest(List<String> deviceIds, boolean play) {
        @com.fasterxml.jackson.annotation.JsonProperty("device_ids")
        public List<String> deviceIds() {
            return deviceIds;
        }
    }

    private record DeviceResponse(
            String id,
            String name,
            String type,
            Boolean isActive,
            Boolean isRestricted,
            Integer volumePercent
    ) {
        @com.fasterxml.jackson.annotation.JsonProperty("is_active")
        public Boolean isActive() {
            return isActive;
        }

        @com.fasterxml.jackson.annotation.JsonProperty("is_restricted")
        public Boolean isRestricted() {
            return isRestricted;
        }

        @com.fasterxml.jackson.annotation.JsonProperty("volume_percent")
        public Integer volumePercent() {
            return volumePercent;
        }
    }

    private record CurrentPlaybackResponse(
            DeviceResponse device,
            TrackItem item,
            Integer progressMs,
            Boolean isPlaying,
            Boolean shuffleState,
            String repeatState
    ) {
        @com.fasterxml.jackson.annotation.JsonProperty("progress_ms")
        public Integer progressMs() {
            return progressMs;
        }

        @com.fasterxml.jackson.annotation.JsonProperty("is_playing")
        public Boolean isPlaying() {
            return isPlaying;
        }

        @com.fasterxml.jackson.annotation.JsonProperty("shuffle_state")
        public Boolean shuffleState() {
            return shuffleState;
        }

        @com.fasterxml.jackson.annotation.JsonProperty("repeat_state")
        public String repeatState() {
            return repeatState;
        }
    }

    private record TrackItem(String id) {
    }
}
