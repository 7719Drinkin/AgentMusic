package com.agentmusic.agentmusic_backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentmusic.agentmusic_backend.domain.ChatRole;
import com.agentmusic.agentmusic_backend.domain.PlaybackMode;
import com.agentmusic.agentmusic_backend.domain.User;
import com.agentmusic.agentmusic_backend.domain.UserPreferences;
import com.agentmusic.agentmusic_backend.service.BackendRuntimeFacade;
import com.agentmusic.agentmusic_backend.service.ChatMemoryService;
import com.agentmusic.agentmusic_backend.service.PlaybackSessionService;
import com.agentmusic.agentmusic_backend.service.PlaylistService;
import com.agentmusic.agentmusic_backend.service.UserContextService;
import com.agentmusic.agentmusic_backend.web.dto.PlaylistDto;
import com.agentmusic.agentmusic_backend.web.dto.TrackDto;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class BackendRuntimeFacadeIntegrationTests {

    @Autowired
    private UserContextService userContextService;

    @Autowired
    private PlaylistService playlistService;

    @Autowired
    private ChatMemoryService chatMemoryService;

    @Autowired
    private PlaybackSessionService playbackSessionService;

    @Autowired
    private BackendRuntimeFacade backendRuntimeFacade;

    @Test
    void runtimeFacadeShouldExposePlaylistChatAndSessionData() {
        String userId = "test-user-" + UUID.randomUUID();
        userContextService.save(new User(
                userId,
                "demo-" + userId,
                userId + "@example.com",
                null,
                new UserPreferences(
                        List.of("Cantopop"),
                        List.of("Eason Chan"),
                        List.of("Rap"),
                        "zh-HK",
                        "chill"
                ),
                null,
                null
        ));

        PlaylistDto playlist = playlistService.createRecommendedPlaylist(
                userId,
                "Evening Mix",
                List.of(
                        new TrackDto("track-1", "Song A", "artist-1", "Album A", "album-1", 180000, null, null),
                        new TrackDto("track-2", "Song B", "artist-2", "Album B", "album-2", 200000, null, null)
                )
        );
        chatMemoryService.appendMessage(userId, ChatRole.USER, "light cantopop songs", null);
        playbackSessionService.saveSession(
                userId,
                null,
                "track-1",
                playlist.id(),
                0,
                12000,
                true,
                PlaybackMode.SHUFFLE,
                "device-1"
        );

        assertThat(backendRuntimeFacade.getRecentPlaylists(userId))
                .extracting(PlaylistDto::id)
                .containsExactly(playlist.id());
        assertThat(backendRuntimeFacade.getRecentChatMessages(userId))
                .extracting("message")
                .contains("light cantopop songs");
        assertThat(backendRuntimeFacade.getActiveSession(userId))
                .isPresent()
                .get()
                .extracting("currentTrackId")
                .isEqualTo("track-1");
    }
}
