package com.agentmusic.agentmusic_backend.web.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@RestControllerAdvice
public class GlobalApiExceptionHandler {

    @ExceptionHandler(SpotifyBridgeAuthorizationException.class)
    public ResponseEntity<ApiErrorResponse> handleSpotifyAuthorization(SpotifyBridgeAuthorizationException exception) {
        return ResponseEntity.status(resolveAuthorizationStatus(exception))
                .body(new ApiErrorResponse(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(SpotifyPlaybackUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleSpotifyPlaybackUnavailable(SpotifyPlaybackUnavailableException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(NotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiErrorResponse(ApiErrorCodes.NOT_FOUND, exception.getMessage()));
    }

    @ExceptionHandler(WebClientRequestException.class)
    public ResponseEntity<ApiErrorResponse> handleWebClientRequest(WebClientRequestException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiErrorResponse(
                        ApiErrorCodes.NETWORK,
                        "Spotify service is temporarily unreachable. Check the network or DNS and try again."
                ));
    }

    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<ApiErrorResponse> handleWebClientResponse(WebClientResponseException exception) {
        return ResponseEntity.status(resolveStatus(exception))
                .body(new ApiErrorResponse(resolveCode(exception), resolveMessage(exception)));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(ApiErrorCodes.INVALID_REQUEST, exception.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalState(IllegalStateException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiErrorResponse(ApiErrorCodes.REQUEST_FAILURE, exception.getMessage()));
    }

    private HttpStatus resolveStatus(WebClientResponseException exception) {
        if (isAuthorizationFailure(exception)) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (isPlaybackRestricted(exception)) {
            return HttpStatus.CONFLICT;
        }
        if (isPlaybackNotFound(exception)) {
            return HttpStatus.CONFLICT;
        }
        if (exception.getStatusCode().is5xxServerError()) {
            return HttpStatus.BAD_GATEWAY;
        }
        return HttpStatus.valueOf(exception.getStatusCode().value());
    }

    private HttpStatus resolveAuthorizationStatus(SpotifyBridgeAuthorizationException exception) {
        if (ApiErrorCodes.BRIDGE_DISABLED.equals(exception.code())) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        return HttpStatus.UNAUTHORIZED;
    }

    private String resolveCode(WebClientResponseException exception) {
        if (isAuthorizationFailure(exception)) {
            return ApiErrorCodes.AUTHORIZATION;
        }
        if (isPlaybackRestricted(exception)) {
            return ApiErrorCodes.DEVICE_RESTRICTED;
        }
        if (isPlaybackNotFound(exception)) {
            return ApiErrorCodes.DEVICE_UNAVAILABLE;
        }
        if (exception.getStatusCode().is5xxServerError()) {
            return ApiErrorCodes.SERVER_FAILURE;
        }
        return ApiErrorCodes.REQUEST_FAILURE;
    }

    private String resolveMessage(WebClientResponseException exception) {
        if (isAuthorizationFailure(exception)) {
            return "Spotify bridge authorization expired or is invalid. Reconnect the bridge account.";
        }
        if (isPlaybackRestricted(exception)) {
            return "Spotify reported the selected playback device as restricted. Keep an active Web Player or desktop client available and try again.";
        }
        if (isPlaybackNotFound(exception)) {
            return "Spotify did not report an active playback device. Keep the same bridge account Web Player or desktop client online.";
        }
        if (exception.getStatusCode().is5xxServerError()) {
            return "Spotify service responded with a server error. Try again shortly.";
        }
        return exception.getMessage();
    }

    private boolean isAuthorizationFailure(WebClientResponseException exception) {
        int status = exception.getStatusCode().value();
        if (status == 401) {
            return true;
        }
        return (status == 400 || status == 403) && isAccountsRequest(exception);
    }

    private boolean isPlaybackRestricted(WebClientResponseException exception) {
        return exception.getStatusCode().value() == 403 && isPlaybackRequest(exception);
    }

    private boolean isPlaybackNotFound(WebClientResponseException exception) {
        return exception.getStatusCode().value() == 404 && isPlaybackRequest(exception);
    }

    private boolean isAccountsRequest(WebClientResponseException exception) {
        return exception.getRequest() != null
                && exception.getRequest().getURI() != null
                && exception.getRequest().getURI().getHost() != null
                && exception.getRequest().getURI().getHost().contains("accounts.spotify.com");
    }

    private boolean isPlaybackRequest(WebClientResponseException exception) {
        return exception.getRequest() != null
                && exception.getRequest().getURI() != null
                && exception.getRequest().getURI().getHost() != null
                && exception.getRequest().getURI().getHost().contains("spotify.com")
                && exception.getRequest().getURI().getPath() != null
                && exception.getRequest().getURI().getPath().contains("/me/player");
    }
}
