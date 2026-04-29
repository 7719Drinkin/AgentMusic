package com.agentmusic.agentmusic_backend.service.impl;

import com.agentmusic.agentmusic_backend.integration.spotify.SpotifyAuthClient;
import com.agentmusic.agentmusic_backend.integration.spotify.SpotifyToken;
import com.agentmusic.agentmusic_backend.config.SpotifyBridgeProperties;
import com.agentmusic.agentmusic_backend.web.dto.SpotifyBridgeAuthStatusDto;
import com.agentmusic.agentmusic_backend.web.exception.ApiErrorCodes;
import com.agentmusic.agentmusic_backend.web.exception.SpotifyBridgeAuthorizationException;
import com.agentmusic.agentmusic_backend.persistence.repository.SpotifyBridgeTokenRepository;
import com.agentmusic.agentmusic_backend.service.SpotifyBridgeAuthService;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Service
public class DefaultSpotifyBridgeAuthService implements SpotifyBridgeAuthService {

    private static final long STATE_TTL_SECONDS = 600;
    private static final long REFRESH_SKEW_SECONDS = 60;

    private final SpotifyAuthClient spotifyAuthClient;
    private final SpotifyBridgeTokenRepository spotifyBridgeTokenRepository;
    private final SpotifyBridgeProperties spotifyBridgeProperties;
    private final Clock clock;
    private final Map<String, Instant> validStates = new ConcurrentHashMap<>();

    public DefaultSpotifyBridgeAuthService(
            SpotifyAuthClient spotifyAuthClient,
            SpotifyBridgeTokenRepository spotifyBridgeTokenRepository,
            SpotifyBridgeProperties spotifyBridgeProperties,
            Clock clock
    ) {
        this.spotifyAuthClient = spotifyAuthClient;
        this.spotifyBridgeTokenRepository = spotifyBridgeTokenRepository;
        this.spotifyBridgeProperties = spotifyBridgeProperties;
        this.clock = clock;
    }

    @Override
    public URI createAuthorizationUri() {
        ensureBridgeEnabled();
        String state = UUID.randomUUID().toString();
        validStates.put(state, Instant.now(clock).plusSeconds(STATE_TTL_SECONDS));
        return spotifyAuthClient.buildAuthorizationUri(state);
    }

    @Override
    public SpotifyBridgeAuthStatusDto handleAuthorizationCallback(String code, String state) {
        ensureBridgeEnabled();
        validateState(state);
        SpotifyToken spotifyToken = spotifyAuthClient.exchangeAuthorizationCode(code);
        spotifyBridgeTokenRepository.save(spotifyToken);
        return toStatus(spotifyToken);
    }

    @Override
    public Optional<String> getValidAccessToken() {
        if (!spotifyBridgeProperties.enabled()) {
            return Optional.empty();
        }
        return spotifyBridgeTokenRepository.findCurrent()
                .map(this::refreshIfNeeded)
                .map(SpotifyToken::accessToken);
    }

    @Override
    public SpotifyBridgeAuthStatusDto getCurrentStatus() {
        return spotifyBridgeTokenRepository.findCurrent()
                .map(this::toStatus)
                .orElseGet(() -> new SpotifyBridgeAuthStatusDto(
                        spotifyBridgeProperties.enabled(),
                        false,
                        spotifyBridgeProperties.systemUserId(),
                        spotifyBridgeProperties.redirectUri(),
                        Set.of(),
                        null
                ));
    }

    private SpotifyToken refreshIfNeeded(SpotifyToken currentToken) {
        Instant threshold = Instant.now(clock).plusSeconds(REFRESH_SKEW_SECONDS);
        if (currentToken.expiresAt().isAfter(threshold)) {
            return currentToken;
        }
        SpotifyToken refreshed;
        try {
            refreshed = spotifyAuthClient.refreshAccessToken(currentToken.refreshToken());
        } catch (WebClientResponseException exception) {
            if (exception.getStatusCode().value() == 400
                    || exception.getStatusCode().value() == 401
                    || exception.getStatusCode().value() == 403) {
                throw new SpotifyBridgeAuthorizationException(
                        "Spotify bridge authorization expired or is invalid. Reconnect the bridge account."
                );
            }
            throw exception;
        }
        SpotifyToken normalized = refreshed.refreshToken() == null || refreshed.refreshToken().isBlank()
                ? new SpotifyToken(
                        refreshed.accessToken(),
                        currentToken.refreshToken(),
                        refreshed.tokenType(),
                        refreshed.scopes(),
                        refreshed.expiresAt()
                )
                : refreshed;
        spotifyBridgeTokenRepository.save(normalized);
        return normalized;
    }

    private SpotifyBridgeAuthStatusDto toStatus(SpotifyToken spotifyToken) {
        return new SpotifyBridgeAuthStatusDto(
                spotifyBridgeProperties.enabled(),
                true,
                spotifyBridgeProperties.systemUserId(),
                spotifyBridgeProperties.redirectUri(),
                spotifyToken.scopes(),
                spotifyToken.expiresAt()
        );
    }

    private void validateState(String state) {
        Instant expiresAt = validStates.remove(state);
        if (state == null || expiresAt == null || expiresAt.isBefore(Instant.now(clock))) {
            throw new SpotifyBridgeAuthorizationException(
                    ApiErrorCodes.AUTHORIZATION_STATE,
                    "Spotify bridge authorization state is invalid or expired."
            );
        }
    }

    private void ensureBridgeEnabled() {
        if (!spotifyBridgeProperties.enabled()) {
            throw new SpotifyBridgeAuthorizationException(
                    ApiErrorCodes.BRIDGE_DISABLED,
                    "Spotify bridge mode is disabled."
            );
        }
    }
}
