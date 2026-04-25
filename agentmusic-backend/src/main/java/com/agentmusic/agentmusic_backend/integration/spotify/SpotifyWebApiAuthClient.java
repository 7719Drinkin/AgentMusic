package com.agentmusic.agentmusic_backend.integration.spotify;

import com.agentmusic.agentmusic_backend.integration.spotify.SpotifyAuthClient;
import com.agentmusic.agentmusic_backend.integration.spotify.SpotifyToken;
import com.agentmusic.agentmusic_backend.config.SpotifyBridgeProperties;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class SpotifyWebApiAuthClient implements SpotifyAuthClient {

    private static final String AUTHORIZE_URL = "https://accounts.spotify.com/authorize";
    private static final String TOKEN_URL = "https://accounts.spotify.com/api/token";
    private static final Set<String> DEFAULT_SCOPES = Set.of(
            "user-read-private",
            "user-read-email",
            "user-read-playback-state",
            "user-read-currently-playing",
            "user-modify-playback-state",
            "playlist-read-private",
            "playlist-modify-private",
            "playlist-modify-public",
            "user-library-read",
            "user-top-read"
    );

    private final WebClient webClient;
    private final SpotifyBridgeProperties spotifyBridgeProperties;
    private final Clock clock;

    public SpotifyWebApiAuthClient(
            WebClient.Builder webClientBuilder,
            SpotifyBridgeProperties spotifyBridgeProperties,
            Clock clock
    ) {
        this.webClient = SpotifyWebClientFactory.create(webClientBuilder);
        this.spotifyBridgeProperties = spotifyBridgeProperties;
        this.clock = clock;
    }

    @Override
    public URI buildAuthorizationUri(String state) {
        return UriComponentsBuilder.fromHttpUrl(AUTHORIZE_URL)
                .queryParam("response_type", "code")
                .queryParam("client_id", spotifyBridgeProperties.clientId())
                .queryParam("scope", String.join(" ", DEFAULT_SCOPES))
                .queryParam("redirect_uri", spotifyBridgeProperties.redirectUri())
                .queryParam("state", state)
                .build()
                .encode()
                .toUri();
    }

    @Override
    public SpotifyToken exchangeAuthorizationCode(String code) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "authorization_code");
        formData.add("code", code);
        formData.add("redirect_uri", spotifyBridgeProperties.redirectUri());
        return requestToken(formData);
    }

    @Override
    public SpotifyToken refreshAccessToken(String refreshToken) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "refresh_token");
        formData.add("refresh_token", refreshToken);
        return requestToken(formData);
    }

    private SpotifyToken requestToken(MultiValueMap<String, String> formData) {
        SpotifyTokenResponse response = webClient.post()
                .uri(TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header(HttpHeaders.AUTHORIZATION, basicAuthorizationHeader())
                .bodyValue(formData)
                .retrieve()
                .bodyToMono(SpotifyTokenResponse.class)
                .block();

        if (response == null || response.accessToken() == null || response.tokenType() == null || response.expiresIn() == null) {
            throw new IllegalStateException("Spotify token response is incomplete.");
        }

        Set<String> scopes = response.scope() == null || response.scope().isBlank()
                ? DEFAULT_SCOPES
                : new LinkedHashSet<>(Arrays.asList(response.scope().split("\\s+")));

        return new SpotifyToken(
                response.accessToken(),
                response.refreshToken(),
                response.tokenType(),
                scopes,
                Instant.now(clock).plusSeconds(response.expiresIn())
        );
    }

    private String basicAuthorizationHeader() {
        String raw = spotifyBridgeProperties.clientId() + ":" + spotifyBridgeProperties.clientSecret();
        String encoded = Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }

    private record SpotifyTokenResponse(
            @com.fasterxml.jackson.annotation.JsonProperty("access_token") String accessToken,
            @com.fasterxml.jackson.annotation.JsonProperty("token_type") String tokenType,
            @com.fasterxml.jackson.annotation.JsonProperty("scope") String scope,
            @com.fasterxml.jackson.annotation.JsonProperty("expires_in") Long expiresIn,
            @com.fasterxml.jackson.annotation.JsonProperty("refresh_token") String refreshToken
    ) {
    }
}
