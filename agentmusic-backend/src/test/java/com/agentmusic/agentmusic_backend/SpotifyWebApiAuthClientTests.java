package com.agentmusic.agentmusic_backend;

import com.agentmusic.agentmusic_backend.integration.spotify.SpotifyWebApiAuthClient;
import com.agentmusic.agentmusic_backend.config.SpotifyBridgeProperties;
import java.net.URI;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

class SpotifyWebApiAuthClientTests {

    @Test
    void buildAuthorizationUriShouldEncodeScopeParameter() {
        SpotifyBridgeProperties properties = new SpotifyBridgeProperties(
                true,
                "client-id",
                "client-secret",
                "http://127.0.0.1:8080/api/auth/spotify/callback",
                "bridge-user",
                null
        );
        SpotifyWebApiAuthClient client = new SpotifyWebApiAuthClient(
                WebClient.builder(),
                properties,
                Clock.systemUTC()
        );

        URI authorizationUri = client.buildAuthorizationUri("test-state");
        String uriValue = authorizationUri.toString();

        assertThat(uriValue).contains("scope=");
        assertThat(uriValue).doesNotContain("scope=user-read-private user-read-email");
        assertThat(uriValue).doesNotContain(" ");
        assertThat(uriValue).contains("%20");
        assertThat(uriValue).contains("streaming");
    }
}
