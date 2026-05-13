package com.agentmusic.agentmusic_backend.service;

import java.util.List;

public record RecommendationSpec(
        RecommendationRequestMode requestMode,
        String artist,
        String track,
        String album,
        String language,
        String era,
        String genre,
        String mood,
        String scene,
        List<String> seedArtists,
        int desiredTrackCount,
        boolean wantAdditionalTracks,
        boolean mustIncludeExplicitTrack,
        boolean preferSameArtist,
        boolean preferSameAlbum
) {
    public RecommendationSpec {
        seedArtists = seedArtists == null ? List.of() : List.copyOf(seedArtists);
    }

    public RecommendationSpec(
            RecommendationRequestMode requestMode,
            String artist,
            String track,
            String album,
            int desiredTrackCount,
            boolean wantAdditionalTracks,
            boolean mustIncludeExplicitTrack,
            boolean preferSameArtist,
            boolean preferSameAlbum
    ) {
        this(
                requestMode,
                artist,
                track,
                album,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                desiredTrackCount,
                wantAdditionalTracks,
                mustIncludeExplicitTrack,
                preferSameArtist,
                preferSameAlbum
        );
    }

    public RecommendationSpec(
            String artist,
            String track,
            String album,
            int desiredTrackCount,
            boolean wantAdditionalTracks,
            boolean mustIncludeExplicitTrack,
            boolean preferSameArtist,
            boolean preferSameAlbum
    ) {
        this(
                RecommendationRequestMode.infer(artist, track, album, wantAdditionalTracks),
                artist,
                track,
                album,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                desiredTrackCount,
                wantAdditionalTracks,
                mustIncludeExplicitTrack,
                preferSameArtist,
                preferSameAlbum
        );
    }
}
