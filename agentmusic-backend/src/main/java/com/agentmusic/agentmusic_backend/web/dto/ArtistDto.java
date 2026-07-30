package com.agentmusic.agentmusic_backend.web.dto;

public record ArtistDto(
        String artistId,
        String name,
        String bio,
        String imageUrl,
        Integer followers
) {
}

