package com.agentmusic.agentmusic_backend.service;

import com.agentmusic.agentmusic_backend.web.dto.SpotifyBridgeAuthStatusDto;
import java.net.URI;
import java.util.Optional;

public interface SpotifyBridgeAuthService {

    URI createAuthorizationUri();

    SpotifyBridgeAuthStatusDto handleAuthorizationCallback(String code, String state);

    Optional<String> getValidAccessToken();

    SpotifyBridgeAuthStatusDto getCurrentStatus();
}

