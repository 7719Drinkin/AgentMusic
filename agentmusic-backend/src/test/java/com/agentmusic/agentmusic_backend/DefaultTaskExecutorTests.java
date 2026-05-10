package com.agentmusic.agentmusic_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentmusic.agentmusic_backend.planner.AgentIntent;
import com.agentmusic.agentmusic_backend.planner.AgentPlan;
import com.agentmusic.agentmusic_backend.planner.PlanStep;
import com.agentmusic.agentmusic_backend.planner.PlanStepType;
import com.agentmusic.agentmusic_backend.planner.PlannerExecutionResult;
import com.agentmusic.agentmusic_backend.planner.PlanningContext;
import com.agentmusic.agentmusic_backend.planner.impl.DefaultTaskExecutor;
import com.agentmusic.agentmusic_backend.service.RecommendationSelection;
import com.agentmusic.agentmusic_backend.service.RecommendationSelectionService;
import com.agentmusic.agentmusic_backend.service.RecommendationSpec;
import com.agentmusic.agentmusic_backend.service.application.MusicQueryApplicationService;
import com.agentmusic.agentmusic_backend.service.application.PlaybackApplicationService;
import com.agentmusic.agentmusic_backend.service.application.PlaylistApplicationService;
import com.agentmusic.agentmusic_backend.service.impl.SearchQueryRefiner;
import com.agentmusic.agentmusic_backend.web.dto.AgentChatRequest;
import com.agentmusic.agentmusic_backend.web.dto.CreatePlaylistRequest;
import com.agentmusic.agentmusic_backend.web.dto.PlaybackSessionDto;
import com.agentmusic.agentmusic_backend.web.dto.PlaylistDto;
import com.agentmusic.agentmusic_backend.web.dto.PlaylistTrackDto;
import com.agentmusic.agentmusic_backend.web.dto.TrackDto;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultTaskExecutorTests {

    private static final String RECOMMENDATION_MESSAGE =
            "\u63a8\u8350\u5f20\u96e8\u751f\u4e13\u8f91\u300a\u4e24\u4f0a\u6218\u4e89\u767d\u8272\u624d\u60c5\u300b\u91cc\u7684\u300a\u53d1\u6655\u300b\u4ee5\u53ca\u4ed6\u7684\u5176\u4ed6\u6b4c\u66f2";

    @Mock
    private MusicQueryApplicationService musicQueryApplicationService;

    @Mock
    private PlaybackApplicationService playbackApplicationService;

    @Mock
    private PlaylistApplicationService playlistApplicationService;

    @Mock
    private RecommendationSelectionService recommendationSelectionService;

    @Test
    void recommendPlaylistShouldCreatePlaylistFromRecommendationSelection() {
        DefaultTaskExecutor taskExecutor = createExecutor();
        List<TrackDto> selectedTracks = List.of(
                track("track-1", "\u767c\u6688", "artist-zhang", "\u5169\u4f0a\u6230\u722d-\u767d\u8272\u624d\u60c5"),
                track("track-2", "\u518d\u898b\u5973\u90ce", "artist-zhang", "\u5169\u4f0a\u6230\u722d-\u767d\u8272\u624d\u60c5"),
                track("track-3", "\u5f8c\u7a97", "artist-zhang", "\u5169\u4f0a\u6230\u722d-\u767d\u8272\u624d\u60c5")
        );
        when(recommendationSelectionService.buildSelection(any(PlanningContext.class)))
                .thenReturn(new RecommendationSelection(
                        new RecommendationSpec(
                                "\u5f20\u96e8\u751f",
                                "\u53d1\u6655",
                                "\u4e24\u4f0a\u6218\u4e89\u767d\u8272\u624d\u60c5",
                                12,
                                true,
                                true,
                                true,
                                true
                        ),
                        selectedTracks
                ));
        when(playlistApplicationService.createPlaylist(eq("demo-user"), any(CreatePlaylistRequest.class)))
                .thenAnswer(invocation -> {
                    CreatePlaylistRequest request = invocation.getArgument(1);
                    return new PlaylistDto(
                            "playlist-1",
                            request.name(),
                            1,
                            LocalDateTime.now(),
                            request.tracks().stream()
                                    .map(track -> new PlaylistTrackDto(
                                            "pt-" + track.trackId(),
                                            "playlist-1",
                                            request.tracks().indexOf(track),
                                            track,
                                            LocalDateTime.now()
                                    ))
                                    .toList()
                    );
                });

        PlannerExecutionResult result = taskExecutor.execute(
                recommendationPlan(),
                planningContext(false)
        );

        ArgumentCaptor<CreatePlaylistRequest> requestCaptor = ArgumentCaptor.forClass(CreatePlaylistRequest.class);
        verify(playlistApplicationService).createPlaylist(eq("demo-user"), requestCaptor.capture());
        assertThat(result.replyMessage()).contains("Created recommendation playlist");
        assertThat(requestCaptor.getValue().tracks()).extracting(TrackDto::title)
                .containsExactly("\u767c\u6688", "\u518d\u898b\u5973\u90ce", "\u5f8c\u7a97");
    }

    @Test
    void playRecommendationShouldStartPlaybackFromFirstSelectedTrack() {
        DefaultTaskExecutor taskExecutor = createExecutor();
        List<TrackDto> selectedTracks = List.of(
                track("track-1", "\u767c\u6688", "artist-zhang", "\u5169\u4f0a\u6230\u722d-\u767d\u8272\u624d\u60c5"),
                track("track-2", "\u518d\u898b\u5973\u90ce", "artist-zhang", "\u5169\u4f0a\u6230\u722d-\u767d\u8272\u624d\u60c5")
        );
        when(recommendationSelectionService.buildSelection(any(PlanningContext.class)))
                .thenReturn(new RecommendationSelection(
                        new RecommendationSpec(
                                "\u5f20\u96e8\u751f",
                                "\u53d1\u6655",
                                "\u4e24\u4f0a\u6218\u4e89\u767d\u8272\u624d\u60c5",
                                12,
                                true,
                                true,
                                true,
                                true
                        ),
                        selectedTracks
                ));
        when(playlistApplicationService.createPlaylist(eq("demo-user"), any(CreatePlaylistRequest.class)))
                .thenReturn(new PlaylistDto(
                        "playlist-2",
                        "Agent Recommendation - sample",
                        1,
                        LocalDateTime.now(),
                        List.of(
                                new PlaylistTrackDto("pt-1", "playlist-2", 0, selectedTracks.getFirst(), LocalDateTime.now()),
                                new PlaylistTrackDto("pt-2", "playlist-2", 1, selectedTracks.get(1), LocalDateTime.now())
                        )
                ));
        when(playbackApplicationService.playTrack(eq("demo-user"), eq("track-1"), eq("playlist-2"), eq(0), eq(null), any()))
                .thenReturn(new PlaybackSessionDto(
                        "session-1",
                        "track-1",
                        "playlist-2",
                        0,
                        0,
                        true,
                        com.agentmusic.agentmusic_backend.domain.PlaybackMode.SEQUENTIAL,
                        null,
                        LocalDateTime.now()
                ));

        PlannerExecutionResult result = taskExecutor.execute(
                playRecommendationPlan(),
                planningContext(false)
        );

        verify(playbackApplicationService).playTrack(eq("demo-user"), eq("track-1"), eq("playlist-2"), eq(0), eq(null), any());
        assertThat(result.replyMessage()).contains("started playback from \u767c\u6688");
    }

    private DefaultTaskExecutor createExecutor() {
        return new DefaultTaskExecutor(
                musicQueryApplicationService,
                playbackApplicationService,
                playlistApplicationService,
                new SearchQueryRefiner(),
                recommendationSelectionService
        );
    }

    private PlanningContext planningContext(boolean playNow) {
        return new PlanningContext(
                new AgentChatRequest("demo-user", RECOMMENDATION_MESSAGE, playNow),
                List.of(),
                List.of()
        );
    }

    private AgentPlan recommendationPlan() {
        return new AgentPlan(
                AgentIntent.RECOMMEND_PLAYLIST,
                "Recommend songs from Zhang Yusheng's album.",
                List.of(
                        new PlanStep(1, PlanStepType.READ_CHAT_CONTEXT, Map.of("limit", 20)),
                        new PlanStep(2, PlanStepType.READ_USER_PREFERENCES, Map.of()),
                        new PlanStep(3, PlanStepType.READ_PLAYLIST_HISTORY, Map.of("limit", 10)),
                        new PlanStep(4, PlanStepType.GENERATE_RECOMMENDATION_CANDIDATES, Map.of("query", RECOMMENDATION_MESSAGE)),
                        new PlanStep(5, PlanStepType.RANK_RECOMMENDATION_CANDIDATES, Map.of("query", RECOMMENDATION_MESSAGE)),
                        new PlanStep(6, PlanStepType.CREATE_RECOMMENDATION_PLAYLIST, Map.of("query", RECOMMENDATION_MESSAGE)),
                        new PlanStep(7, PlanStepType.PERSIST_CHAT_REPLY, Map.of())
                )
        );
    }

    private AgentPlan playRecommendationPlan() {
        return new AgentPlan(
                AgentIntent.PLAY_RECOMMENDATION,
                "Recommend and play songs from Zhang Yusheng's album.",
                List.of(
                        new PlanStep(1, PlanStepType.READ_CHAT_CONTEXT, Map.of("limit", 20)),
                        new PlanStep(2, PlanStepType.READ_USER_PREFERENCES, Map.of()),
                        new PlanStep(3, PlanStepType.READ_PLAYLIST_HISTORY, Map.of("limit", 10)),
                        new PlanStep(4, PlanStepType.GENERATE_RECOMMENDATION_CANDIDATES, Map.of("query", RECOMMENDATION_MESSAGE)),
                        new PlanStep(5, PlanStepType.RANK_RECOMMENDATION_CANDIDATES, Map.of("query", RECOMMENDATION_MESSAGE)),
                        new PlanStep(6, PlanStepType.CREATE_RECOMMENDATION_PLAYLIST, Map.of("query", RECOMMENDATION_MESSAGE)),
                        new PlanStep(7, PlanStepType.PERSIST_CHAT_REPLY, Map.of())
                )
        );
    }

    private TrackDto track(String trackId, String title, String artistId, String albumName) {
        return new TrackDto(trackId, title, artistId, albumName, "album-1", 180000, null, null);
    }
}
