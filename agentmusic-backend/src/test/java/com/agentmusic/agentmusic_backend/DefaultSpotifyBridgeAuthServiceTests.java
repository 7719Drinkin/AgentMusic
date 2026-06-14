package com.agentmusic.agentmusic_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.agentmusic.agentmusic_backend.config.SpotifyBridgeProperties;
import com.agentmusic.agentmusic_backend.integration.spotify.SpotifyAuthClient;
import com.agentmusic.agentmusic_backend.integration.spotify.SpotifyToken;
import com.agentmusic.agentmusic_backend.persistence.repository.SpotifyBridgeTokenRepository;
import com.agentmusic.agentmusic_backend.service.impl.DefaultSpotifyBridgeAuthService;
import com.agentmusic.agentmusic_backend.web.exception.ApiErrorCodes;
import com.agentmusic.agentmusic_backend.web.exception.SpotifyBridgeAuthorizationException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultSpotifyBridgeAuthServiceTests {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-14T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private SpotifyAuthClient spotifyAuthClient;

    @Mock
    private SpotifyBridgeTokenRepository spotifyBridgeTokenRepository;

    @Test
    void getWebPlaybackTokenShouldReturnShortLivedAccessTokenMetadata() {
        DefaultSpotifyBridgeAuthService service = new DefaultSpotifyBridgeAuthService(
                spotifyAuthClient,
                spotifyBridgeTokenRepository,
                properties(true),
                CLOCK
        );
        SpotifyToken token = new SpotifyToken(
                "access-token",
                "refresh-token",
                "Bearer",
                Set.of("streaming", "user-read-private", "user-read-email"),
                Instant.parse("2026-06-14T01:00:00Z")
        );
        when(spotifyBridgeTokenRepository.findCurrent()).thenReturn(Optional.of(token));

        var response = service.getWebPlaybackToken();

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.expiresAt()).isEqualTo(Instant.parse("2026-06-14T01:00:00Z"));
        assertThat(response.scopes()).contains("streaming", "user-read-private", "user-read-email");
        assertThat(response.missingScopes()).isEmpty();
    }

    @Test
    void getWebPlaybackTokenShouldFailWhenStreamingScopeIsMissing() {
        DefaultSpotifyBridgeAuthService service = new DefaultSpotifyBridgeAuthService(
                spotifyAuthClient,
                spotifyBridgeTokenRepository,
                properties(true),
                CLOCK
        );
        SpotifyToken token = new SpotifyToken(
                "access-token",
                "refresh-token",
                "Bearer",
                Set.of("user-read-private", "user-read-email"),
                Instant.parse("2026-06-14T01:00:00Z")
        );
        when(spotifyBridgeTokenRepository.findCurrent()).thenReturn(Optional.of(token));

        SpotifyBridgeAuthorizationException exception = assertThrows(
                SpotifyBridgeAuthorizationException.class,
                service::getWebPlaybackToken
        );

        assertThat(exception.code()).isEqualTo(ApiErrorCodes.SCOPE_MISSING);
        assertThat(exception.getMessage()).contains("streaming");
    }

    private SpotifyBridgeProperties properties(boolean enabled) {
        return new SpotifyBridgeProperties(
                enabled,
                "client-id",
                "client-secret",
                "http://127.0.0.1:8080/api/auth/spotify/callback",
                "bridge-user",
                null
        );
    }
}
