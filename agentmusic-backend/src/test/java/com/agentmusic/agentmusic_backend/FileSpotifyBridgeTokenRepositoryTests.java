package com.agentmusic.agentmusic_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.agentmusic.agentmusic_backend.client.SpotifyToken;
import com.agentmusic.agentmusic_backend.repository.file.FileSpotifyBridgeTokenRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FileSpotifyBridgeTokenRepositoryTests {

    @Test
    void shouldPersistTokenAcrossRepositoryInstances() throws IOException {
        Path tempDir = Files.createTempDirectory(Path.of("build"), "spotify-token-test-");
        Path tokenFilePath = tempDir.resolve(".spotify-bridge-token.properties");
        SpotifyToken spotifyToken = new SpotifyToken(
                "access-token",
                "refresh-token",
                "Bearer",
                Set.of("user-read-private"),
                Instant.parse("2026-04-12T00:00:00Z")
        );

        FileSpotifyBridgeTokenRepository writer = new FileSpotifyBridgeTokenRepository(tokenFilePath);
        writer.save(spotifyToken);

        FileSpotifyBridgeTokenRepository reader = new FileSpotifyBridgeTokenRepository(tokenFilePath);

        assertTrue(reader.findCurrent().isPresent());
        assertEquals(spotifyToken, reader.findCurrent().orElseThrow());

        Files.deleteIfExists(tokenFilePath);
        Files.deleteIfExists(tempDir);
    }
}
