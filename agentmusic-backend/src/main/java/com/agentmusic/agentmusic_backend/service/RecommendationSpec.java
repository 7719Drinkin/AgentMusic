package com.agentmusic.agentmusic_backend.service;

public record RecommendationSpec(
        String artist,
        String track,
        String album,
        int desiredTrackCount,
        boolean wantAdditionalTracks,
        boolean mustIncludeExplicitTrack,
        boolean preferSameArtist,
        boolean preferSameAlbum
) {
}
