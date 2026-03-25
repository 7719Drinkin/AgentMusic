package com.agentmusic.agentmusic_backend.service;

import com.agentmusic.agentmusic_backend.dto.ChatMessageDto;
import com.agentmusic.agentmusic_backend.dto.PlaybackSessionDto;
import com.agentmusic.agentmusic_backend.dto.PlaylistDto;
import java.util.List;
import java.util.Optional;

public interface BackendRuntimeFacade {

    List<PlaylistDto> getRecentPlaylists(String userId);

    List<ChatMessageDto> getRecentChatMessages(String userId);

    Optional<PlaybackSessionDto> getActiveSession(String userId);
}

