package com.agentmusic.agentmusic_backend.dto;

import java.util.List;

public record AgentChatResponse(
        ChatMessageDto reply,
        PlaybackSessionDto session,
        List<PlaylistDto> recommendedPlaylists
) {
}
