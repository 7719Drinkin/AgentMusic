package com.agentmusic.agentmusic_backend.service;

public record RecommendationSpec(
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
                desiredTrackCount,
                wantAdditionalTracks,
                mustIncludeExplicitTrack,
                preferSameArtist,
                preferSameAlbum
        );
    }
}
