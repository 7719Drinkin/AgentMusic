package com.agentmusic.agentmusic_backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentmusic.agentmusic_backend.web.exception.ApiErrorCodes;
import com.agentmusic.agentmusic_backend.web.exception.ApiErrorResponse;
import com.agentmusic.agentmusic_backend.web.exception.GlobalApiExceptionHandler;
import com.agentmusic.agentmusic_backend.web.exception.SpotifyBridgeAuthorizationException;
import com.agentmusic.agentmusic_backend.web.exception.SpotifyPlaybackUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

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
}
