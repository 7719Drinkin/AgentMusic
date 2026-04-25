package com.agentmusic.agentmusic_backend.persistence.redis;

import java.time.Duration;

public final class RedisKeys {

    public static final Duration SESSION_TTL = Duration.ofHours(1);
    public static final Duration PLAYLIST_TRACKS_TTL = Duration.ofHours(1);
    public static final Duration TRACK_INFO_TTL = Duration.ofHours(24);
    public static final Duration ARTIST_BIO_TTL = Duration.ofDays(7);
    public static final int RECENT_PLAYLIST_LIMIT = 10;
    public static final int SHORT_TERM_CHAT_LIMIT = 50;

    private RedisKeys() {
    }

    public static String userSession(String userId) {
        return "user:session:" + userId;
    }

    public static String userPlaylists(String userId) {
        return "user:playlists:" + userId;
    }

    public static String playlistTracks(String playlistId) {
        return "playlist:tracks:" + playlistId;
    }

    public static String trackInfo(String trackId) {
        return "track:info:" + trackId;
    }

    public static String artistBio(String artistId) {
        return "artist:bio:" + artistId;
    }

    public static String chatHistory(String userId) {
        return "chat:history:" + userId;
    }
}

