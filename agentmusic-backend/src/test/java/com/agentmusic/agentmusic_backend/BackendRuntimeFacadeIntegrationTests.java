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
        String userId = "test-" + UUID.randomUUID().toString().substring(0, 8);
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
                        new TrackDto("1A2b3C4d5E6f7G8h9I0j1K", "Song A", "artist-1", "Album A", "album-1", 180000, null, null),
                        new TrackDto("2B3c4D5e6F7g8H9i0J1k2L", "Song B", "artist-2", "Album B", "album-2", 200000, null, null)
                )
        );
        chatMemoryService.appendMessage(userId, ChatRole.USER, "light cantopop songs", null);
        playbackSessionService.saveSession(
                userId,
                null,
                "1A2b3C4d5E6f7G8h9I0j1K",
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
                .isEqualTo("1A2b3C4d5E6f7G8h9I0j1K");
    }
}
