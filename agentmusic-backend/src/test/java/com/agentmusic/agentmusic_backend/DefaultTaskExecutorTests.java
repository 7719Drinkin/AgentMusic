package com.agentmusic.agentmusic_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
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
import com.agentmusic.agentmusic_backend.service.application.MusicQueryApplicationService;
import com.agentmusic.agentmusic_backend.service.application.PlaybackApplicationService;
import com.agentmusic.agentmusic_backend.service.application.PlaylistApplicationService;
import com.agentmusic.agentmusic_backend.service.impl.SearchQueryRefiner;
import com.agentmusic.agentmusic_backend.web.dto.AgentChatRequest;
import com.agentmusic.agentmusic_backend.web.dto.CreatePlaylistRequest;
import com.agentmusic.agentmusic_backend.web.dto.PlaylistDto;
import com.agentmusic.agentmusic_backend.web.dto.PlaylistTrackDto;
import com.agentmusic.agentmusic_backend.web.dto.TrackDto;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultTaskExecutorTests {

    private static final String RECOMMENDATION_MESSAGE =
            "\u7ed9\u6211\u63a8\u8350\u5f20\u96e8\u751f\u7684\u300a\u6cb3\u300b\u4ee5\u53ca\u4ed6\u7684\u5176\u4ed6\u6b4c\u66f2";
    private static final String STRUCTURED_QUERY = "track:\u6cb3 artist:\u5f20\u96e8\u751f";
    private static final String TRACK_HE = "\u6cb3";
    private static final String TRACK_DAHAI = "\u5927\u6d77";
    private static final String TRACK_FEI = "\u53e3\u662f\u5fc3\u975e";
    private static final String TRACK_MISS = "\u5929\u5929\u60f3\u4f60";
    private static final String TRACK_FISH = "\u4e00\u5929\u5230\u665a\u6e38\u6cf3\u7684\u9c7c";
    private static final String TRACK_NOISE = "\u5f20\u4e09\u7684\u6b4c";
    private static final String TRACK_DIZZY = "\u53d1\u6655";

    @Mock
    private MusicQueryApplicationService musicQueryApplicationService;

    @Mock
    private PlaybackApplicationService playbackApplicationService;

    @Mock
    private PlaylistApplicationService playlistApplicationService;

    @InjectMocks
    private DefaultTaskExecutor taskExecutor;

    @Test
    void recommendPlaylistShouldKeepExplicitTitleThenFillSameArtistTracks() {
        taskExecutor = new DefaultTaskExecutor(
                musicQueryApplicationService,
                playbackApplicationService,
                playlistApplicationService,
                new SearchQueryRefiner()
        );

        List<TrackDto> searchResults = List.of(
                track("t-he-1", TRACK_HE, "artist-zhang"),
                track("t-he-2", TRACK_HE, "artist-zhang"),
                track("t-sea", TRACK_DAHAI, "artist-zhang"),
                track("t-port", TRACK_FEI, "artist-zhang"),
                track("t-air", TRACK_MISS, "artist-zhang"),
                track("t-noise", TRACK_NOISE, "artist-other"),
                track("t-fish", TRACK_FISH, "artist-zhang")
        );
        when(musicQueryApplicationService.searchTracks(eq(STRUCTURED_QUERY), anyInt())).thenReturn(searchResults);
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

        AgentPlan plan = new AgentPlan(
                AgentIntent.RECOMMEND_PLAYLIST,
                "Recommend Zhang Yusheng songs including He.",
                List.of(
                        new PlanStep(1, PlanStepType.READ_CHAT_CONTEXT, Map.of("limit", 20)),
                        new PlanStep(2, PlanStepType.READ_USER_PREFERENCES, Map.of()),
                        new PlanStep(3, PlanStepType.READ_PLAYLIST_HISTORY, Map.of("limit", 10)),
                        new PlanStep(4, PlanStepType.GENERATE_RECOMMENDATION_CANDIDATES, Map.of("query", STRUCTURED_QUERY)),
                        new PlanStep(5, PlanStepType.RANK_RECOMMENDATION_CANDIDATES, Map.of("query", STRUCTURED_QUERY)),
                        new PlanStep(6, PlanStepType.CREATE_RECOMMENDATION_PLAYLIST, Map.of("query", STRUCTURED_QUERY)),
                        new PlanStep(7, PlanStepType.PERSIST_CHAT_REPLY, Map.of())
                )
        );

        PlannerExecutionResult result = taskExecutor.execute(
                plan,
                new PlanningContext(
                        new AgentChatRequest("demo-user", RECOMMENDATION_MESSAGE, false),
                        List.of(),
                        List.of()
                )
        );

        ArgumentCaptor<CreatePlaylistRequest> requestCaptor = ArgumentCaptor.forClass(CreatePlaylistRequest.class);
        verify(playlistApplicationService).createPlaylist(eq("demo-user"), requestCaptor.capture());

        assertThat(result.replyMessage()).contains("Created recommendation playlist");
        assertThat(requestCaptor.getValue().tracks()).extracting(TrackDto::title)
                .startsWith(TRACK_HE, TRACK_DAHAI, TRACK_FEI, TRACK_MISS, TRACK_FISH);
        assertThat(requestCaptor.getValue().tracks()).extracting(TrackDto::artistId)
                .doesNotContain("artist-other");
    }

    @Test
    void recommendPlaylistShouldUseLatestMessageQueryWhenPlannerReturnsEnglishRewrite() {
        taskExecutor = new DefaultTaskExecutor(
                musicQueryApplicationService,
                playbackApplicationService,
                playlistApplicationService,
                new SearchQueryRefiner()
        );

        when(musicQueryApplicationService.searchTracks(eq(STRUCTURED_QUERY), anyInt())).thenReturn(List.of());

        AgentPlan plan = new AgentPlan(
                AgentIntent.RECOMMEND_PLAYLIST,
                "Recommend songs by Zhang Yusheng including He.",
                List.of(
                        new PlanStep(1, PlanStepType.READ_CHAT_CONTEXT, Map.of("limit", 20)),
                        new PlanStep(2, PlanStepType.READ_USER_PREFERENCES, Map.of()),
                        new PlanStep(3, PlanStepType.READ_PLAYLIST_HISTORY, Map.of("limit", 10)),
                        new PlanStep(4, PlanStepType.GENERATE_RECOMMENDATION_CANDIDATES, Map.of("query", "He of Tom Chang")),
                        new PlanStep(5, PlanStepType.RANK_RECOMMENDATION_CANDIDATES, Map.of("query", "He of Tom Chang")),
                        new PlanStep(6, PlanStepType.CREATE_RECOMMENDATION_PLAYLIST, Map.of("query", "He of Tom Chang")),
                        new PlanStep(7, PlanStepType.PERSIST_CHAT_REPLY, Map.of())
                )
        );

        PlannerExecutionResult result = taskExecutor.execute(
                plan,
                new PlanningContext(
                        new AgentChatRequest("demo-user", RECOMMENDATION_MESSAGE, false),
                        List.of(),
                        List.of()
                )
        );

        assertThat(result.replyMessage()).isEqualTo("No suitable tracks were found for the current recommendation request.");
        verify(musicQueryApplicationService).searchTracks(eq(STRUCTURED_QUERY), anyInt());
    }

    @Test
    void recommendPlaylistShouldFallbackToDominantArtistWhenExplicitTitleQueryIsNoisy() {
        taskExecutor = new DefaultTaskExecutor(
                musicQueryApplicationService,
                playbackApplicationService,
                playlistApplicationService,
                new SearchQueryRefiner()
        );

        String message = "\u63a8\u8350\u5f20\u96e8\u751f\u7684\u300a\u53d1\u6655\u300b\u4ee5\u53ca\u4ed6\u7684\u5176\u4ed6\u6b4c\u66f2";
        List<TrackDto> noisyTracks = List.of(
                track("noise-1", "Call Girl", "artist-daze"),
                track("noise-2", "Sea Of Love", "artist-daze"),
                track("noise-3", "Superhero", "artist-daze"),
                track("noise-4", "Together Forever", "artist-daze"),
                track("noise-5", "Cyber Pet", "artist-daze")
        );
        List<TrackDto> dominantArtistTracks = List.of(
                track("zhang-1", TRACK_FISH, "artist-zhang"),
                track("other-1", "\u4e09\u751f\u4e09\u4e16", "artist-other"),
                track("zhang-2", TRACK_FEI, "artist-zhang"),
                track("zhang-3", TRACK_DAHAI, "artist-zhang"),
                track("zhang-4", TRACK_MISS, "artist-zhang"),
                track("zhang-5", "\u6211\u7684\u672a\u6765\u4e0d\u662f\u68a6", "artist-zhang")
        );

        when(musicQueryApplicationService.searchTracks(argThat(query -> query != null && query.contains("track:" + TRACK_DIZZY)), anyInt()))
                .thenReturn(noisyTracks);
        when(musicQueryApplicationService.searchTracks(eq("artist:\u5f20\u96e8\u751f"), anyInt()))
                .thenReturn(dominantArtistTracks);
        when(playlistApplicationService.createPlaylist(eq("demo-user"), any(CreatePlaylistRequest.class)))
                .thenAnswer(invocation -> {
                    CreatePlaylistRequest request = invocation.getArgument(1);
                    return new PlaylistDto(
                            "playlist-2",
                            request.name(),
                            1,
                            LocalDateTime.now(),
                            request.tracks().stream()
                                    .map(track -> new PlaylistTrackDto(
                                            "pt-" + track.trackId(),
                                            "playlist-2",
                                            request.tracks().indexOf(track),
                                            track,
                                            LocalDateTime.now()
                                    ))
                                    .toList()
                    );
                });

        AgentPlan plan = new AgentPlan(
                AgentIntent.RECOMMEND_PLAYLIST,
                "Recommend Zhang Yusheng songs including dizzy.",
                List.of(
                        new PlanStep(1, PlanStepType.READ_CHAT_CONTEXT, Map.of("limit", 20)),
                        new PlanStep(2, PlanStepType.READ_USER_PREFERENCES, Map.of()),
                        new PlanStep(3, PlanStepType.READ_PLAYLIST_HISTORY, Map.of("limit", 10)),
                        new PlanStep(4, PlanStepType.GENERATE_RECOMMENDATION_CANDIDATES, Map.of("query", message)),
                        new PlanStep(5, PlanStepType.RANK_RECOMMENDATION_CANDIDATES, Map.of("query", message)),
                        new PlanStep(6, PlanStepType.CREATE_RECOMMENDATION_PLAYLIST, Map.of("query", message)),
                        new PlanStep(7, PlanStepType.PERSIST_CHAT_REPLY, Map.of())
                )
        );

        PlannerExecutionResult result = taskExecutor.execute(
                plan,
                new PlanningContext(
                        new AgentChatRequest("demo-user", message, false),
                        List.of(),
                        List.of()
                )
        );

        ArgumentCaptor<CreatePlaylistRequest> requestCaptor = ArgumentCaptor.forClass(CreatePlaylistRequest.class);
        verify(playlistApplicationService).createPlaylist(eq("demo-user"), requestCaptor.capture());

        assertThat(result.replyMessage()).contains("Created recommendation playlist");
        assertThat(requestCaptor.getValue().tracks()).extracting(TrackDto::artistId)
                .startsWith("artist-zhang", "artist-zhang", "artist-zhang", "artist-zhang", "artist-zhang");
        assertThat(requestCaptor.getValue().tracks()).extracting(TrackDto::title)
                .startsWith(TRACK_FISH, TRACK_FEI, TRACK_DAHAI, TRACK_MISS);
    }

    @Test
    void recommendPlaylistShouldTreatTraditionalAndSimplifiedTitleAsSameTrack() {
        taskExecutor = new DefaultTaskExecutor(
                musicQueryApplicationService,
                playbackApplicationService,
                playlistApplicationService,
                new SearchQueryRefiner()
        );

        String message = "\u63a8\u8350\u5f20\u96e8\u751f\u4e13\u8f91\u300a\u4e24\u4f0a\u6218\u4e89\u767d\u8272\u624d\u60c5\u300b\u91cc\u7684\u300a\u53d1\u6655\u300b\u4ee5\u53ca\u4ed6\u7684\u5176\u4ed6\u6b4c\u66f2";
        List<TrackDto> structuredResults = List.of(
                track("zhang-dizzy", "\u767c\u6688", "artist-zhang", "\u5169\u4f0a\u6230\u722d-\u767d\u8272\u624d\u60c5"),
                track("zhang-again", "\u518d\u898b\u5973\u90ce", "artist-zhang", "\u5169\u4f0a\u6230\u722d-\u767d\u8272\u624d\u60c5"),
                track("zhang-back", "\u5f8c\u7a97", "artist-zhang", "\u5169\u4f0a\u6230\u722d-\u767d\u8272\u624d\u60c5")
        );

        when(musicQueryApplicationService.searchTracks(
                eq("track:\u53d1\u6655 artist:\u5f20\u96e8\u751f album:\u4e24\u4f0a\u6218\u4e89\u767d\u8272\u624d\u60c5"),
                anyInt()
        )).thenReturn(structuredResults);
        when(playlistApplicationService.createPlaylist(eq("demo-user"), any(CreatePlaylistRequest.class)))
                .thenAnswer(invocation -> {
                    CreatePlaylistRequest request = invocation.getArgument(1);
                    return new PlaylistDto(
                            "playlist-3",
                            request.name(),
                            1,
                            LocalDateTime.now(),
                            request.tracks().stream()
                                    .map(track -> new PlaylistTrackDto(
                                            "pt-" + track.trackId(),
                                            "playlist-3",
                                            request.tracks().indexOf(track),
                                            track,
                                            LocalDateTime.now()
                                    ))
                                    .toList()
                    );
                });

        AgentPlan plan = new AgentPlan(
                AgentIntent.RECOMMEND_PLAYLIST,
                "Recommend songs from Zhang Yusheng's album and other songs.",
                List.of(
                        new PlanStep(1, PlanStepType.READ_CHAT_CONTEXT, Map.of("limit", 20)),
                        new PlanStep(2, PlanStepType.READ_USER_PREFERENCES, Map.of()),
                        new PlanStep(3, PlanStepType.READ_PLAYLIST_HISTORY, Map.of("limit", 10)),
                        new PlanStep(4, PlanStepType.GENERATE_RECOMMENDATION_CANDIDATES, Map.of("query", message)),
                        new PlanStep(5, PlanStepType.RANK_RECOMMENDATION_CANDIDATES, Map.of("query", message)),
                        new PlanStep(6, PlanStepType.CREATE_RECOMMENDATION_PLAYLIST, Map.of("query", message)),
                        new PlanStep(7, PlanStepType.PERSIST_CHAT_REPLY, Map.of())
                )
        );

        taskExecutor.execute(
                plan,
                new PlanningContext(
                        new AgentChatRequest("demo-user", message, false),
                        List.of(),
                        List.of()
                )
        );

        ArgumentCaptor<CreatePlaylistRequest> requestCaptor = ArgumentCaptor.forClass(CreatePlaylistRequest.class);
        verify(playlistApplicationService).createPlaylist(eq("demo-user"), requestCaptor.capture());

        assertThat(requestCaptor.getValue().tracks()).extracting(TrackDto::title)
                .startsWith("\u767c\u6688", "\u518d\u898b\u5973\u90ce", "\u5f8c\u7a97");
    }

    private TrackDto track(String trackId, String title, String artistId) {
        return track(trackId, title, artistId, "album");
    }

    private TrackDto track(String trackId, String title, String artistId, String albumName) {
        return new TrackDto(trackId, title, artistId, albumName, "album-1", 180000, null, null);
    }
}
