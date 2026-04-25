package com.agentmusic.agentmusic_backend.web.dto;

import java.util.List;

public record AgentChatResponse(
        ChatMessageDto reply,
        PlaybackSessionDto session,
        List<PlaylistDto> recommendedPlaylists
) {
}
