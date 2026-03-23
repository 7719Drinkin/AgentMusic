package com.agentmusic.agentmusic_backend.dto;

public record ArtistDto(
        String artistId,
        String name,
        String bio,
        String imageUrl,
        Integer followers
) {
}

