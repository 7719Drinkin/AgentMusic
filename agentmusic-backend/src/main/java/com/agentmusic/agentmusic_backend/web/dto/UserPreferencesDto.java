package com.agentmusic.agentmusic_backend.web.dto;

import java.util.List;

public record UserPreferencesDto(
        List<String> favoriteGenres,
        List<String> favoriteArtists,
        List<String> excludedGenres,
        String preferredLanguage,
        String moodPreference
) {
}

