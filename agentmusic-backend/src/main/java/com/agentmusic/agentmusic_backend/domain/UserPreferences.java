package com.agentmusic.agentmusic_backend.domain;

import java.util.List;

public record UserPreferences(
        List<String> favoriteGenres,
        List<String> favoriteArtists,
        List<String> excludedGenres,
        String preferredLanguage,
        String moodPreference
) {
}

