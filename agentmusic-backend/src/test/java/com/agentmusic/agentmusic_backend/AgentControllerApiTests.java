package com.agentmusic.agentmusic_backend;

import com.agentmusic.agentmusic_backend.domain.PlaybackMode;
import com.agentmusic.agentmusic_backend.dto.PlaybackSessionDto;
import com.agentmusic.agentmusic_backend.dto.TrackDto;
import com.agentmusic.agentmusic_backend.service.application.MusicQueryApplicationService;
import com.agentmusic.agentmusic_backend.service.application.PlaybackApplicationService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AgentControllerApiTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MusicQueryApplicationService musicQueryApplicationService;

    @MockitoBean
    private PlaybackApplicationService playbackApplicationService;

    @Test
    void chatApiShouldCreateRecommendationPlaylistWithoutPlaybackWhenUserSaysRecommendOnly() throws Exception {
        when(musicQueryApplicationService.searchTracks(anyString(), anyInt())).thenReturn(sampleTracks());

        mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "planner-api-user-1",
                                  "message": "给我推荐点轻松的粤语歌，先不要播放",
                                  "voiceInput": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply.metadata.intent").value("RECOMMEND_PLAYLIST"))
                .andExpect(jsonPath("$.reply.message").value(org.hamcrest.Matchers.containsString("Created recommendation playlist")))
                .andExpect(jsonPath("$.recommendedPlaylists.length()").value(1))
                .andExpect(jsonPath("$.recommendedPlaylists[0].tracks.length()").value(5));

        verify(playbackApplicationService, never()).playTrack(any(), any(), any(), any());
    }

    @Test
    void chatApiShouldUsePlayRecommendationAsDefaultRecommendationPath() throws Exception {
        when(musicQueryApplicationService.searchTracks(anyString(), anyInt())).thenReturn(sampleTracks());
        when(playbackApplicationService.playTrack(anyString(), anyString(), any(), any()))
                .thenReturn(new PlaybackSessionDto(
                        "session-1",
                        "track-1",
                        0,
                        true,
                        PlaybackMode.SHUFFLE,
                        "device-1",
                        LocalDateTime.now()
                ));
        when(playbackApplicationService.syncBridgeState(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "planner-api-user-2",
                                  "message": "给我来点轻松的粤语歌，随机播放",
                                  "voiceInput": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply.metadata.intent").value("PLAY_RECOMMENDATION"))
                .andExpect(jsonPath("$.reply.message").value(org.hamcrest.Matchers.containsString("started playback")))
                .andExpect(jsonPath("$.recommendedPlaylists.length()").value(1));
    }

    private List<TrackDto> sampleTracks() {
        return List.of(
                new TrackDto("track-1", "Song A", "artist-1", "Album A", "album-1", 180000, null, null),
                new TrackDto("track-2", "Song B", "artist-2", "Album B", "album-2", 200000, null, null),
                new TrackDto("track-3", "Song C", "artist-3", "Album C", "album-3", 220000, null, null),
                new TrackDto("track-4", "Song D", "artist-4", "Album D", "album-4", 210000, null, null),
                new TrackDto("track-5", "Song E", "artist-5", "Album E", "album-5", 205000, null, null)
        );
    }
}
