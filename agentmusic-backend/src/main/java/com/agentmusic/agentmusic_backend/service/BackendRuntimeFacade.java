package com.agentmusic.agentmusic_backend.service;

import com.agentmusic.agentmusic_backend.web.dto.ChatMessageDto;
import com.agentmusic.agentmusic_backend.web.dto.PlaybackSessionDto;
import com.agentmusic.agentmusic_backend.web.dto.PlaylistDto;
import java.util.List;
import java.util.Optional;

public interface BackendRuntimeFacade {

    List<PlaylistDto> getRecentPlaylists(String userId);

    List<ChatMessageDto> getRecentChatMessages(String userId);

    Optional<PlaybackSessionDto> getActiveSession(String userId);
}

