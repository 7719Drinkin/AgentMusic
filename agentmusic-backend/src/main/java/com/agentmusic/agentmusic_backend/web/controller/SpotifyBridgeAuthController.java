package com.agentmusic.agentmusic_backend.web.controller;

import com.agentmusic.agentmusic_backend.web.dto.SpotifyBridgeAuthStatusDto;
import com.agentmusic.agentmusic_backend.web.dto.SpotifyWebPlaybackTokenDto;
import com.agentmusic.agentmusic_backend.service.SpotifyBridgeAuthService;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/spotify")
public class SpotifyBridgeAuthController {

    private final SpotifyBridgeAuthService spotifyBridgeAuthService;

    public SpotifyBridgeAuthController(SpotifyBridgeAuthService spotifyBridgeAuthService) {
        this.spotifyBridgeAuthService = spotifyBridgeAuthService;
    }

    @GetMapping("/login")
    public ResponseEntity<Void> login() {
        URI redirectUri = spotifyBridgeAuthService.createAuthorizationUri();
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, redirectUri.toString())
                .build();
    }

    @GetMapping("/callback")
    public SpotifyBridgeAuthStatusDto callback(
            @RequestParam String code,
            @RequestParam String state
    ) {
        return spotifyBridgeAuthService.handleAuthorizationCallback(code, state);
    }

    @GetMapping("/status")
    public SpotifyBridgeAuthStatusDto status() {
        return spotifyBridgeAuthService.getCurrentStatus();
    }

    @GetMapping("/web-playback-token")
    public SpotifyWebPlaybackTokenDto webPlaybackToken() {
        return spotifyBridgeAuthService.getWebPlaybackToken();
    }
}
