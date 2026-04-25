package com.agentmusic.agentmusic_backend.persistence.repository.file;

import com.agentmusic.agentmusic_backend.integration.spotify.SpotifyToken;
import com.agentmusic.agentmusic_backend.persistence.repository.SpotifyBridgeTokenRepository;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

@Repository
public class FileSpotifyBridgeTokenRepository implements SpotifyBridgeTokenRepository {

    private static final Logger log = LoggerFactory.getLogger(FileSpotifyBridgeTokenRepository.class);
    private static final String TOKEN_FILE_NAME = ".spotify-bridge-token.properties";
    private static final String ACCESS_TOKEN_KEY = "accessToken";
    private static final String REFRESH_TOKEN_KEY = "refreshToken";
    private static final String TOKEN_TYPE_KEY = "tokenType";
    private static final String SCOPES_KEY = "scopes";
    private static final String EXPIRES_AT_KEY = "expiresAt";

    private final Path tokenFilePath;
    private final AtomicReference<SpotifyToken> cachedToken = new AtomicReference<>();

    public FileSpotifyBridgeTokenRepository() {
        this(resolveDefaultTokenFilePath());
    }

    public FileSpotifyBridgeTokenRepository(Path tokenFilePath) {
        this.tokenFilePath = tokenFilePath;
        this.cachedToken.set(readFromDisk().orElse(null));
    }

    @Override
    public void save(SpotifyToken spotifyToken) {
        try {
            Path parent = tokenFilePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Properties properties = new Properties();
            properties.setProperty(ACCESS_TOKEN_KEY, nullSafe(spotifyToken.accessToken()));
            properties.setProperty(REFRESH_TOKEN_KEY, nullSafe(spotifyToken.refreshToken()));
            properties.setProperty(TOKEN_TYPE_KEY, nullSafe(spotifyToken.tokenType()));
            properties.setProperty(SCOPES_KEY, joinScopes(spotifyToken.scopes()));
            properties.setProperty(EXPIRES_AT_KEY, spotifyToken.expiresAt() == null ? "" : spotifyToken.expiresAt().toString());
            try (OutputStream outputStream = Files.newOutputStream(tokenFilePath)) {
                properties.store(outputStream, "Spotify bridge token");
            }
            cachedToken.set(spotifyToken);
        } catch (IOException error) {
            throw new IllegalStateException("Failed to persist Spotify bridge token.", error);
        }
    }

    @Override
    public Optional<SpotifyToken> findCurrent() {
        SpotifyToken cached = cachedToken.get();
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<SpotifyToken> fromDisk = readFromDisk();
        fromDisk.ifPresent(cachedToken::set);
        return fromDisk;
    }

    private Optional<SpotifyToken> readFromDisk() {
        if (!Files.exists(tokenFilePath)) {
            return Optional.empty();
        }
        try (InputStream inputStream = Files.newInputStream(tokenFilePath)) {
            Properties properties = new Properties();
            properties.load(inputStream);
            String accessToken = properties.getProperty(ACCESS_TOKEN_KEY);
            if (accessToken == null || accessToken.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new SpotifyToken(
                    accessToken,
                    emptyToNull(properties.getProperty(REFRESH_TOKEN_KEY)),
                    emptyToNull(properties.getProperty(TOKEN_TYPE_KEY)),
                    splitScopes(properties.getProperty(SCOPES_KEY)),
                    parseInstant(properties.getProperty(EXPIRES_AT_KEY))
            ));
        } catch (IOException error) {
            log.warn("Failed to read Spotify bridge token from {}", tokenFilePath, error);
            return Optional.empty();
        }
    }

    private static Path resolveDefaultTokenFilePath() {
        Path currentDirectory = Path.of("").toAbsolutePath().normalize();
        Path backendDirectory = currentDirectory.resolve("agentmusic-backend");
        if (Files.isDirectory(backendDirectory)) {
            return backendDirectory.resolve(TOKEN_FILE_NAME);
        }
        return currentDirectory.resolve(TOKEN_FILE_NAME);
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String joinScopes(Set<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return "";
        }
        return scopes.stream()
                .sorted()
                .collect(Collectors.joining(","));
    }

    private static Set<String> splitScopes(String scopesValue) {
        if (scopesValue == null || scopesValue.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(scopesValue.split(","))
                .map(String::trim)
                .filter(scope -> !scope.isEmpty())
                .collect(Collectors.toSet());
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Instant.parse(value);
    }
}
