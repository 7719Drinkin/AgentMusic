package com.agentmusic.agentmusic_backend.integration.spotify;

import java.net.URI;

public interface SpotifyAuthClient {

    URI buildAuthorizationUri(String state);

    SpotifyToken exchangeAuthorizationCode(String code);

    SpotifyToken refreshAccessToken(String refreshToken);
}

