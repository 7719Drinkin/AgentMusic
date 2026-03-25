package com.agentmusic.agentmusic_backend.service.impl;

import com.agentmusic.agentmusic_backend.cache.RedisKeys;
import com.agentmusic.agentmusic_backend.dto.ChatMessageDto;
import com.agentmusic.agentmusic_backend.dto.PlaybackSessionDto;
import com.agentmusic.agentmusic_backend.dto.PlaylistDto;
import com.agentmusic.agentmusic_backend.service.BackendRuntimeFacade;
import com.agentmusic.agentmusic_backend.service.ChatMemoryService;
import com.agentmusic.agentmusic_backend.service.PlaybackSessionService;
import com.agentmusic.agentmusic_backend.service.PlaylistService;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class DefaultBackendRuntimeFacade implements BackendRuntimeFacade {

    private final PlaylistService playlistService;
    private final ChatMemoryService chatMemoryService;
    private final PlaybackSessionService playbackSessionService;

    public DefaultBackendRuntimeFacade(
            PlaylistService playlistService,
            ChatMemoryService chatMemoryService,
            PlaybackSessionService playbackSessionService
    ) {
        this.playlistService = playlistService;
        this.chatMemoryService = chatMemoryService;
        this.playbackSessionService = playbackSessionService;
    }

    @Override
    public List<PlaylistDto> getRecentPlaylists(String userId) {
        return playlistService.getRecentPlaylists(userId, RedisKeys.RECENT_PLAYLIST_LIMIT);
    }

    @Override
    public List<ChatMessageDto> getRecentChatMessages(String userId) {
        return chatMemoryService.getRecentMessages(userId, RedisKeys.SHORT_TERM_CHAT_LIMIT);
    }

    @Override
    public Optional<PlaybackSessionDto> getActiveSession(String userId) {
        return playbackSessionService.getActiveSession(userId);
    }
}

