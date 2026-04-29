package com.agentmusic.agentmusic_backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentmusic.agentmusic_backend.web.exception.ApiErrorCodes;
import com.agentmusic.agentmusic_backend.web.exception.ApiErrorResponse;
import com.agentmusic.agentmusic_backend.web.exception.GlobalApiExceptionHandler;
import com.agentmusic.agentmusic_backend.web.exception.SpotifyBridgeAuthorizationException;
import com.agentmusic.agentmusic_backend.web.exception.SpotifyPlaybackUnavailableException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

class GlobalApiExceptionHandlerTests {

    private final GlobalApiExceptionHandler handler = new GlobalApiExceptionHandler();

    @Test
    void shouldReturnAuthorizationCodeForSpotifyBridgeAuthorizationErrors() {
        var response = handler.handleSpotifyAuthorization(
                new SpotifyBridgeAuthorizationException("Spotify bridge authorization expired or is invalid.")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isEqualTo(new ApiErrorResponse(
                ApiErrorCodes.AUTHORIZATION,
                "Spotify bridge authorization expired or is invalid."
        ));
    }

    @Test
    void shouldReturnServiceUnavailableForBridgeDisabledErrors() {
        var response = handler.handleSpotifyAuthorization(
                new SpotifyBridgeAuthorizationException(
                        ApiErrorCodes.BRIDGE_DISABLED,
                        "Spotify bridge mode is disabled."
                )
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isEqualTo(new ApiErrorResponse(
                ApiErrorCodes.BRIDGE_DISABLED,
                "Spotify bridge mode is disabled."
        ));
    }

    @Test
    void shouldPreserveStructuredPlaybackErrorCode() {
        var response = handler.handleSpotifyPlaybackUnavailable(
                new SpotifyPlaybackUnavailableException(
                        ApiErrorCodes.DEVICE_RESTRICTED,
                        "Spotify detected devices, but all of them are restricted."
                )
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isEqualTo(new ApiErrorResponse(
                ApiErrorCodes.DEVICE_RESTRICTED,
                "Spotify detected devices, but all of them are restricted."
        ));
    }

    @Test
    void shouldPreserveDeviceOfflineErrorCode() {
        var response = handler.handleSpotifyPlaybackUnavailable(
                new SpotifyPlaybackUnavailableException(
                        ApiErrorCodes.DEVICE_OFFLINE,
                        "Selected Spotify device is offline or no longer available. Refresh devices and try again."
                )
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isEqualTo(new ApiErrorResponse(
                ApiErrorCodes.DEVICE_OFFLINE,
                "Selected Spotify device is offline or no longer available. Refresh devices and try again."
        ));
    }

    @Test
    void shouldMapWebClientRequestErrorsToNetworkCode() {
        var exception = new WebClientRequestException(
                new RuntimeException("dns timeout"),
                HttpMethod.GET,
                URI.create("https://api.spotify.com/v1/me/player"),
                HttpHeaders.EMPTY
        );

        var response = handler.handleWebClientRequest(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isEqualTo(new ApiErrorResponse(
                ApiErrorCodes.NETWORK,
                "Spotify service is temporarily unreachable. Check the network or DNS and try again."
        ));
    }

    @Test
    void shouldMapUnauthorizedWebClientResponseToAuthorizationCode() {
        var exception = WebClientResponseException.create(
                401,
                "Unauthorized",
                HttpHeaders.EMPTY,
                new byte[0],
                StandardCharsets.UTF_8
        );

        var response = handler.handleWebClientResponse(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isEqualTo(new ApiErrorResponse(
                ApiErrorCodes.AUTHORIZATION,
                "Spotify bridge authorization expired or is invalid. Reconnect the bridge account."
        ));
    }

    @Test
    void shouldMapPlaybackRestrictedWebClientResponseToDeviceRestrictedCode() {
        var exception = WebClientResponseException.create(
                403,
                "Forbidden",
                HttpHeaders.EMPTY,
                new byte[0],
                StandardCharsets.UTF_8,
                RequestEntity.put(URI.create("https://api.spotify.com/v1/me/player")).build()
        );

        var response = handler.handleWebClientResponse(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isEqualTo(new ApiErrorResponse(
                ApiErrorCodes.DEVICE_RESTRICTED,
                "Spotify reported the selected playback device as restricted. Keep an active Web Player or desktop client available and try again."
        ));
    }

    @Test
    void shouldMapPlaybackNotFoundWebClientResponseToDeviceUnavailableCode() {
        var exception = WebClientResponseException.create(
                404,
                "Not Found",
                HttpHeaders.EMPTY,
                new byte[0],
                StandardCharsets.UTF_8,
                RequestEntity.put(URI.create("https://api.spotify.com/v1/me/player")).build()
        );

        var response = handler.handleWebClientResponse(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isEqualTo(new ApiErrorResponse(
                ApiErrorCodes.DEVICE_UNAVAILABLE,
                "Spotify did not report an active playback device. Keep the same bridge account Web Player or desktop client online."
        ));
    }
}
