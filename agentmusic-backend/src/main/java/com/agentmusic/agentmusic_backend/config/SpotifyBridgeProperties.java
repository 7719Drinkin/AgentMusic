package com.agentmusic.agentmusic_backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spotify.bridge")
public record SpotifyBridgeProperties(
        boolean enabled,
        String clientId,
        String clientSecret,
        String redirectUri,
        String systemUserId,
        String defaultDeviceId
) {
}

