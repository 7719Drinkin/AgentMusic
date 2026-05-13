package com.agentmusic.agentmusic_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentmusic.agentmusic_backend.config.AgentChatProperties;
import com.agentmusic.agentmusic_backend.config.OpenAiProperties;
import com.agentmusic.agentmusic_backend.planner.PlanningContext;
import com.agentmusic.agentmusic_backend.service.RecommendationRequestMode;
import com.agentmusic.agentmusic_backend.service.RecommendationSelection;
import com.agentmusic.agentmusic_backend.service.RecommendationSpec;
import com.agentmusic.agentmusic_backend.service.application.MusicQueryApplicationService;
import com.agentmusic.agentmusic_backend.service.impl.LlmBackedRecommendationSelectionService;
import com.agentmusic.agentmusic_backend.service.impl.SearchQueryRefiner;
import com.agentmusic.agentmusic_backend.web.dto.AgentChatRequest;
import com.agentmusic.agentmusic_backend.web.dto.ArtistDto;
import com.agentmusic.agentmusic_backend.web.dto.ChatMessageDto;
import com.agentmusic.agentmusic_backend.web.dto.TrackDto;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LlmBackedRecommendationSelectionServiceTests {

    @Mock
    private MusicQueryApplicationService musicQueryApplicationService;

    @Test
    void buildSelectionShouldPreferExplicitTrackThenSameAlbumWhenLlmIsDisabled() {
        LlmBackedRecommendationSelectionService service = createService(false);
        String message = "\u63a8\u8350\u5f20\u96e8\u751f\u4e13\u8f91\u300a\u4e24\u4f0a\u6218\u4e89\u767d\u8272\u624d\u60c5\u300b\u91cc\u7684\u300a\u53d1\u6655\u300b\u4ee5\u53ca\u4ed6\u7684\u5176\u4ed6\u6b4c\u66f2";

        when(musicQueryApplicationService.searchTracks(anyString(), anyInt())).thenReturn(List.of());
        when(musicQueryApplicationService.searchTracks(
                eq("track:\u53d1\u6655 artist:\u5f20\u96e8\u751f album:\u4e24\u4f0a\u6218\u4e89\u767d\u8272\u624d\u60c5"),
                anyInt()
        )).thenReturn(List.of(
                track("track-1", "\u767c\u6688", "artist-zhang", "\u5169\u4f0a\u6230\u722d-\u767d\u8272\u624d\u60c5"),
                track("track-2", "\u518d\u898b\u5973\u90ce", "artist-zhang", "\u5169\u4f0a\u6230\u722d-\u767d\u8272\u624d\u60c5"),
                track("track-3", "\u5f8c\u7a97", "artist-zhang", "\u5169\u4f0a\u6230\u722d-\u767d\u8272\u624d\u60c5")
        ));
        when(musicQueryApplicationService.searchTracks(eq("track:\u53d1\u6655 artist:\u5f20\u96e8\u751f"), anyInt()))
                .thenReturn(List.of(track("track-4", "\u767c\u6688", "artist-zhang", "\u5982\u71d5\u76e4\u65cb\u800c\u4f86\u7684\u601d\u5ff5")));
        when(musicQueryApplicationService.searchTracks(eq("artist:\u5f20\u96e8\u751f album:\u4e24\u4f0a\u6218\u4e89\u767d\u8272\u624d\u60c5"), anyInt()))
                .thenReturn(List.of(track("track-5", "\u672a\u77e5", "artist-zhang", "\u5169\u4f0a\u6230\u722d-\u767d\u8272\u624d\u60c5")));
        when(musicQueryApplicationService.searchTracks(eq("artist:\u5f20\u96e8\u751f"), anyInt()))
                .thenReturn(List.of(track("track-6", "\u5927\u6d77", "artist-zhang", "\u5927\u6d77")));
        when(musicQueryApplicationService.getArtist("artist-zhang"))
                .thenReturn(Optional.of(new ArtistDto("artist-zhang", "Zhang Yu Sheng", null, null, null)));

        RecommendationSelection selection = service.buildSelection(new PlanningContext(
                new AgentChatRequest("demo-user", message, false),
                List.of(),
                List.of()
        ));

        assertThat(selection.spec().track()).isEqualTo("\u53d1\u6655");
        assertThat(selection.spec().artist()).isEqualTo("\u5f20\u96e8\u751f");
        assertThat(selection.spec().album()).isEqualTo("\u4e24\u4f0a\u6218\u4e89\u767d\u8272\u624d\u60c5");
        assertThat(selection.tracks()).extracting(TrackDto::title)
                .startsWith("\u767c\u6688", "\u518d\u898b\u5973\u90ce", "\u5f8c\u7a97", "\u672a\u77e5");
    }

    @Test
    void buildSelectionShouldHonorExplicitTrackCountInUserMessage() {
        LlmBackedRecommendationSelectionService service = createService(false);
        String message = "\u63a8\u835020\u9996\u5f20\u96e8\u751f\u7684\u6b4c";

        when(musicQueryApplicationService.searchTracks(anyString(), anyInt())).thenReturn(List.of());

        RecommendationSelection selection = service.buildSelection(new PlanningContext(
                new AgentChatRequest("demo-user", message, false),
                List.of(
                        new ChatMessageDto("m1", com.agentmusic.agentmusic_backend.domain.ChatRole.USER, message, Map.of(), LocalDateTime.now())
                ),
                List.of()
        ));

        assertThat(selection.spec().desiredTrackCount()).isEqualTo(20);
        assertThat(selection.tracks()).isEmpty();
    }

    @Test
    void buildSelectionShouldKeepArtistOnlyRequestsInStrictArtistMode() {
        LlmBackedRecommendationSelectionService service = createService(false);
        String message = "\u63a8\u835020\u9996\u5f20\u96e8\u751f\u7684\u6b4c";

        when(musicQueryApplicationService.searchArtists(eq("\u5f20\u96e8\u751f"), eq(5)))
                .thenReturn(List.of(new ArtistDto("artist-zhang", "Zhang Yu Sheng", null, null, null)));
        when(musicQueryApplicationService.getArtistCatalogTracks(eq("artist-zhang"), eq(40)))
                .thenReturn(List.of(
                        track("track-1", "\u4e00\u5929\u5230\u665a\u6e38\u6cf3\u7684\u9b5a", "artist-zhang", "\u4e00\u5929\u5230\u665a\u6e38\u6cf3\u7684\u9b5a"),
                        track("track-2", "\u53e3\u662f\u5fc3\u975e", "artist-zhang", "\u53e3\u662f\u5fc3\u975e"),
                        track("track-3", "20 Min", "artist-other", "Luv Is Rage 2")
                ));

        RecommendationSelection selection = service.buildSelection(new PlanningContext(
                new AgentChatRequest("demo-user", message, false),
                List.of(),
                List.of()
        ));

        assertThat(selection.spec().artist()).isEqualTo("\u5f20\u96e8\u751f");
        assertThat(selection.spec().desiredTrackCount()).isEqualTo(20);
        assertThat(selection.tracks()).extracting(TrackDto::artistId)
                .containsOnly("artist-zhang");
        verify(musicQueryApplicationService).searchArtists(eq("\u5f20\u96e8\u751f"), eq(5));
        verify(musicQueryApplicationService).getArtistCatalogTracks(eq("artist-zhang"), eq(40));
    }

    @Test
    void buildSelectionShouldPreferSameArtistAndAlbumForExplicitTrackVariant() {
        LlmBackedRecommendationSelectionService service = createService(false);
        String message = "\u63a8\u8350\u5f20\u96e8\u751f\u4e13\u8f91\u300a\u4e24\u4f0a\u6218\u4e89\u7ea2\u8272\u70ed\u60c5\u300b\u91cc\u7684\u300a\u6211\u6700\u6df1\u7231\u7684\u4eba\u4f24\u6211\u6700\u6df1\u300b\u4ee5\u53ca\u5f20\u96e8\u751f\u7684\u5176\u4ed6\u6b4c\u66f2";

        when(musicQueryApplicationService.searchArtists(eq("\u5f20\u96e8\u751f"), eq(5)))
                .thenReturn(List.of(new ArtistDto("artist-zhang", "Zhang Yu Sheng", null, null, null)));
        when(musicQueryApplicationService.searchTracks(anyString(), anyInt())).thenReturn(List.of());
        when(musicQueryApplicationService.searchTracks(
                eq("track:\u6211\u6700\u6df1\u7231\u7684\u4eba\u4f24\u6211\u6700\u6df1 artist:\u5f20\u96e8\u751f album:\u4e24\u4f0a\u6218\u4e89\u7ea2\u8272\u70ed\u60c5"),
                anyInt()
        )).thenReturn(List.of(
                track("wrong-track", "\u6211\u6700\u6df1\u7231\u7684\u4eba\u4f24\u6211\u6700\u6df1", "artist-other", "Other Album")
        ));
        when(musicQueryApplicationService.getArtistCatalogTracks(eq("artist-zhang"), eq(24)))
                .thenReturn(List.of(
                        track("right-track", "\u6700\u611b\u7684\u4eba\u50b7\u6211\u6700\u6df1", "artist-zhang", "\u5169\u4f0a\u6230\u722d-\u7d05\u8272\u71b1\u60c5"),
                        track("album-track", "\u611b\u60c5\u2026", "artist-zhang", "\u5169\u4f0a\u6230\u722d-\u7d05\u8272\u71b1\u60c5"),
                        track("other-track", "\u5927\u6d77", "artist-zhang", "\u5927\u6d77")
                ));

        RecommendationSelection selection = service.buildSelection(new PlanningContext(
                new AgentChatRequest("demo-user", message, false),
                List.of(),
                List.of()
        ));

        assertThat(selection.spec().artist()).isEqualTo("\u5f20\u96e8\u751f");
        assertThat(selection.spec().track()).isEqualTo("\u6211\u6700\u6df1\u7231\u7684\u4eba\u4f24\u6211\u6700\u6df1");
        assertThat(selection.spec().album()).isEqualTo("\u4e24\u4f0a\u6218\u4e89\u7ea2\u8272\u70ed\u60c5");
        assertThat(selection.tracks()).extracting(TrackDto::artistId).containsOnly("artist-zhang");
        assertThat(selection.tracks()).extracting(TrackDto::title)
                .startsWith("\u6700\u611b\u7684\u4eba\u50b7\u6211\u6700\u6df1", "\u611b\u60c5\u2026");
    }

    @Test
    void buildSelectionShouldKeepAlbumOnlyRequestsInsideAlbumBoundary() {
        LlmBackedRecommendationSelectionService service = createService(false);
        String message = "\u63a8\u8350\u8c2d\u548f\u9e9f\u300a\u4e16\u5916\u6843\u6e90\u300b\u4e13\u8f91\u91cc\u7684\u6b4c\u66f2";

        when(musicQueryApplicationService.searchArtists(anyString(), eq(5)))
                .thenReturn(List.of(new ArtistDto("artist-alan", "Alan Tam", null, null, null)));
        when(musicQueryApplicationService.searchTracks(anyString(), anyInt())).thenReturn(List.of());
        when(musicQueryApplicationService.getArtistCatalogTracks(eq("artist-alan"), eq(24)))
                .thenReturn(List.of(
                        track("album-1", "\u66f2\u76ee1", "artist-alan", "\u4e16\u5916\u6843\u6e90"),
                        track("album-2", "\u66f2\u76ee2", "artist-alan", "\u4e16\u5916\u6843\u6e90"),
                        track("album-3", "\u66f2\u76ee3", "artist-alan", "\u4e16\u5916\u6843\u6e90"),
                        track("album-4", "\u66f2\u76ee4", "artist-alan", "\u4e16\u5916\u6843\u6e90"),
                        track("album-5", "\u66f2\u76ee5", "artist-alan", "\u4e16\u5916\u6843\u6e90"),
                        track("album-6", "\u66f2\u76ee6", "artist-alan", "\u4e16\u5916\u6843\u6e90"),
                        track("album-7", "\u66f2\u76ee7", "artist-alan", "\u4e16\u5916\u6843\u6e90"),
                        track("album-8", "\u66f2\u76ee8", "artist-alan", "\u4e16\u5916\u6843\u6e90"),
                        track("album-9", "\u66f2\u76ee9", "artist-alan", "\u4e16\u5916\u6843\u6e90"),
                        track("album-10", "\u66f2\u76ee10", "artist-alan", "\u4e16\u5916\u6843\u6e90"),
                        track("wrong-same-album-1", "\u5176\u4ed6\u7248\u672c1", "artist-other", "\u4e16\u5916\u6843\u6e90"),
                        track("wrong-same-album-2", "\u5176\u4ed6\u7248\u672c2", "artist-other-2", "\u4e16\u5916\u6843\u6e90"),
                        track("other-1", "\u5176\u4ed61", "artist-alan", "\u5176\u4ed6\u4e13\u8f91"),
                        track("other-2", "\u5176\u4ed62", "artist-alan", "\u53e6\u4e00\u5f35\u4e13\u8f91")
                ));

        RecommendationSelection selection = service.buildSelection(new PlanningContext(
                new AgentChatRequest("demo-user", message, false),
                List.of(),
                List.of()
        ));

        assertThat(selection.spec().album()).isEqualTo("\u4e16\u5916\u6843\u6e90");
        assertThat(selection.spec().wantAdditionalTracks()).isFalse();
        assertThat(selection.tracks()).hasSize(10);
        assertThat(selection.tracks()).extracting(TrackDto::artistId).containsOnly("artist-alan");
        assertThat(selection.tracks()).extracting(TrackDto::albumName).containsOnly("\u4e16\u5916\u6843\u6e90");
    }

    @Test
    void buildSelectionShouldUseThemeAwareQueriesForBroadRequests() {
        LlmBackedRecommendationSelectionService service = createService(false);
        String message = "\u7ed9\u6211\u6765\u70b990\u5e74\u4ee3\u7684\u7ca4\u8bed\u6b4c";

        when(musicQueryApplicationService.searchTracks(anyString(), anyInt())).thenReturn(List.of());
        when(musicQueryApplicationService.searchTracks(
                argThat(query -> query != null && query.contains("90s") && query.contains("cantopop")),
                anyInt()
        ))
                .thenReturn(List.of(
                        track("track-1", "\u8bb2\u4e0d\u51fa\u518d\u89c1", "artist-a", "\u7ecf\u5178\u7ca4\u8bed"),
                        track("track-2", "\u4e00\u751f\u4f55\u6c42", "artist-b", "\u7ecf\u5178\u7ca4\u8bed")
                ));
        when(musicQueryApplicationService.getArtist("artist-a"))
                .thenReturn(Optional.of(new ArtistDto("artist-a", "歌手A", null, null, null)));
        when(musicQueryApplicationService.getArtist("artist-b"))
                .thenReturn(Optional.of(new ArtistDto("artist-b", "歌手B", null, null, null)));

        RecommendationSelection selection = service.buildSelection(new PlanningContext(
                new AgentChatRequest("demo-user", message, false),
                List.of(),
                List.of()
        ));

        assertThat(selection.spec().requestMode()).isEqualTo(RecommendationRequestMode.THEME_AWARE);
        assertThat(selection.spec().language()).isEqualTo("cantonese");
        assertThat(selection.spec().era()).isEqualTo("1990s");
        assertThat(selection.spec().genre()).isEqualTo("cantopop");
        assertThat(selection.tracks()).extracting(TrackDto::title)
                .containsExactlyInAnyOrder("\u8bb2\u4e0d\u51fa\u518d\u89c1", "\u4e00\u751f\u4f55\u6c42");
        verify(musicQueryApplicationService, org.mockito.Mockito.atLeastOnce()).searchTracks(
                argThat(query -> query != null && query.contains("90s") && query.contains("cantopop")),
                anyInt()
        );
    }

    @Test
    void buildSelectionShouldUseThemeSeedArtistCatalogWhenThemeSearchIsEmpty() {
        LlmBackedRecommendationSelectionService service = createService(false);
        String message = "\u7ed9\u6211\u6765\u70b990\u5e74\u4ee3\u7684\u7ca4\u8bed\u6b4c";

        when(musicQueryApplicationService.searchTracks(anyString(), anyInt())).thenReturn(List.of());
        when(musicQueryApplicationService.searchArtists(eq("\u5f20\u5b66\u53cb"), eq(5)))
                .thenReturn(List.of(new ArtistDto("artist-jacky", "张学友", null, null, null)));
        when(musicQueryApplicationService.getArtistCatalogTracks(eq("artist-jacky"), anyInt()))
                .thenReturn(List.of(
                        track("track-jacky-1", "\u543b\u522b", "artist-jacky", "\u543b\u522b"),
                        track("track-jacky-2", "\u6bcf\u5929\u7231\u4f60\u591a\u4e00\u4e9b", "artist-jacky", "\u771f\u60c5\u6d41\u9732")
                ));

        RecommendationSelection selection = service.buildSelection(new PlanningContext(
                new AgentChatRequest("demo-user", message, false),
                List.of(),
                List.of()
        ));

        assertThat(selection.spec().requestMode()).isEqualTo(RecommendationRequestMode.THEME_AWARE);
        assertThat(selection.spec().seedArtists()).contains("\u5f20\u5b66\u53cb");
        assertThat(selection.tracks()).extracting(TrackDto::title)
                .containsExactly("\u543b\u522b", "\u6bcf\u5929\u7231\u4f60\u591a\u4e00\u4e9b");
        verify(musicQueryApplicationService).searchArtists(eq("\u5f20\u5b66\u53cb"), eq(5));
        verify(musicQueryApplicationService).getArtistCatalogTracks(eq("artist-jacky"), anyInt());
    }

    @Test
    void buildSelectionShouldAvoidThemeDuplicateTitlesAndLiveVariantsWhenAlternativesExist() {
        LlmBackedRecommendationSelectionService service = createService(false);
        String message = "\u7ed9\u6211\u6765\u70b990\u5e74\u4ee3\u7684\u7ca4\u8bed\u6b4c";

        when(musicQueryApplicationService.searchTracks(anyString(), anyInt()))
                .thenReturn(List.of(
                        track("track-1", "\u5343\u5343\u95cb\u6b4c", "artist-a", "Chinese Pop 90s"),
                        track("track-2", "\u5343\u5343\u95cb\u6b4c", "artist-b", "\u5343\u5343\u95cb\u6b4c"),
                        track("track-3", "\u4e00\u751f\u4f55\u6c42", "artist-c", "\u7ecf\u5178\u7ca4\u8bed"),
                        track("track-4", "\u671d\u8056 - Live", "artist-d", "\u6f14\u5531\u6703 Live"),
                        track("track-5", "\u73b0\u4ee3\u7231\u60c5\u6545\u4e8b", "artist-e", "\u7ecf\u5178\u7ca4\u8bed"),
                        track("track-6", "\u53cb\u60c5\u5c81\u6708", "artist-f", "\u7ecf\u5178\u7ca4\u8bed"),
                        track("track-7", "\u76f8\u9022\u4f55\u5fc5\u66fe\u76f8\u8bc6", "artist-g", "\u7ecf\u5178\u7ca4\u8bed"),
                        track("track-8", "\u5bb9\u6613\u53d7\u4f24\u7684\u5973\u4eba", "artist-h", "\u7ecf\u5178\u7ca4\u8bed"),
                        track("track-9", "\u8bb2\u4e0d\u51fa\u518d\u89c1", "artist-i", "\u7ecf\u5178\u7ca4\u8bed"),
                        track("track-10", "\u6bcf\u5929\u7231\u4f60\u591a\u4e00\u4e9b", "artist-j", "\u7ecf\u5178\u7ca4\u8bed"),
                        track("track-11", "\u6211\u6068\u6211\u75f4\u5fc3", "artist-k", "\u7ecf\u5178\u7ca4\u8bed"),
                        track("track-12", "\u7ea2\u65e5", "artist-l", "\u7ecf\u5178\u7ca4\u8bed"),
                        track("track-13", "\u98ce\u7ee7\u7eed\u5439", "artist-m", "\u7ecf\u5178\u7ca4\u8bed"),
                        track("track-14", "\u504f\u504f\u559c\u6b22\u4f60", "artist-n", "\u7ecf\u5178\u7ca4\u8bed"),
                        track("track-15", "Love The 90's", "artist-o", "Love The 90's"),
                        track("track-16", "Intro", "artist-p", "\u7ecf\u5178\u7ca4\u8bed")
                ));
        when(musicQueryApplicationService.getArtist(anyString()))
                .thenAnswer(invocation -> Optional.of(new ArtistDto(
                        invocation.getArgument(0),
                        invocation.getArgument(0),
                        null,
                        null,
                        null
                )));

        RecommendationSelection selection = service.buildSelection(new PlanningContext(
                new AgentChatRequest("demo-user", message, false),
                List.of(),
                List.of()
        ));

        assertThat(selection.spec().requestMode()).isEqualTo(RecommendationRequestMode.THEME_AWARE);
        assertThat(selection.tracks()).hasSize(selection.spec().desiredTrackCount());
        assertThat(selection.tracks()).extracting(TrackDto::title)
                .doesNotContain("\u671d\u8056 - Live", "Love The 90's", "Intro");
        assertThat(selection.tracks().stream()
                .filter(track -> "\u5343\u5343\u95cb\u6b4c".equals(track.title()))
                .count()).isEqualTo(1);
    }

    @Test
    void repairResolvedSpecShouldHonorAlbumOnlyRequestModeForEquivalentPhrases() {
        LlmBackedRecommendationSelectionService service = createService(false);
        SearchQueryRefiner refiner = new SearchQueryRefiner();
        RecommendationSpec llmSpec = new RecommendationSpec(
                RecommendationRequestMode.ALBUM_ONLY,
                "\u5f20\u96e8\u751f",
                "\u4e24\u4f0a\u6218\u4e89\u767d\u8272\u624d\u60c5",
                null,
                12,
                true,
                true,
                true,
                false
        );

        RecommendationSpec normalizedPrefix = invokeRepairResolvedSpec(
                service,
                llmSpec,
                refiner.analyze("\u63a8\u8350\u5f20\u96e8\u751f\u4e13\u8f91\u300a\u4e24\u4f0a\u6218\u4e89\u767d\u8272\u624d\u60c5\u300b\u91cc\u7684\u6b4c")
        );
        RecommendationSpec normalizedSuffix = invokeRepairResolvedSpec(
                service,
                llmSpec,
                refiner.analyze("\u63a8\u8350\u5f20\u96e8\u751f\u300a\u4e24\u4f0a\u6218\u4e89\u767d\u8272\u624d\u60c5\u300b\u4e13\u8f91\u91cc\u7684\u6b4c\u66f2")
        );

        assertThat(normalizedPrefix.requestMode()).isEqualTo(RecommendationRequestMode.ALBUM_ONLY);
        assertThat(normalizedPrefix.track()).isNull();
        assertThat(normalizedPrefix.album()).isEqualTo("\u4e24\u4f0a\u6218\u4e89\u767d\u8272\u624d\u60c5");
        assertThat(normalizedPrefix.wantAdditionalTracks()).isFalse();
        assertThat(normalizedPrefix.preferSameAlbum()).isTrue();

        assertThat(normalizedSuffix.requestMode()).isEqualTo(RecommendationRequestMode.ALBUM_ONLY);
        assertThat(normalizedSuffix.track()).isNull();
        assertThat(normalizedSuffix.album()).isEqualTo("\u4e24\u4f0a\u6218\u4e89\u767d\u8272\u624d\u60c5");
        assertThat(normalizedSuffix.wantAdditionalTracks()).isFalse();
        assertThat(normalizedSuffix.preferSameAlbum()).isTrue();
    }

    @Test
    void repairResolvedSpecShouldCoerceMisclassifiedAlbumOnlyPromptBackToAlbumOnly() {
        LlmBackedRecommendationSelectionService service = createService(false);
        SearchQueryRefiner refiner = new SearchQueryRefiner();
        RecommendationSpec llmSpec = new RecommendationSpec(
                RecommendationRequestMode.ENTITY_CONSTRAINED,
                "\u5f20\u96e8\u751f",
                null,
                "\u4e24\u4f0a\u6218\u4e89\u767d\u8272\u624d\u60c5",
                12,
                true,
                false,
                true,
                true
        );

        RecommendationSpec normalized = invokeRepairResolvedSpec(
                service,
                llmSpec,
                refiner.analyze("\u63a8\u8350\u5f20\u96e8\u751f\u4e13\u8f91\u300a\u4e24\u4f0a\u6218\u4e89\u767d\u8272\u624d\u60c5\u300b\u91cc\u7684\u6b4c")
        );

        assertThat(normalized.requestMode()).isEqualTo(RecommendationRequestMode.ALBUM_ONLY);
        assertThat(normalized.track()).isNull();
        assertThat(normalized.album()).isEqualTo("\u4e24\u4f0a\u6218\u4e89\u767d\u8272\u624d\u60c5");
        assertThat(normalized.wantAdditionalTracks()).isFalse();
        assertThat(normalized.preferSameAlbum()).isTrue();
    }

    @Test
    void repairResolvedSpecShouldPreserveExplicitTrackInsideEntityConstrainedScope() {
        LlmBackedRecommendationSelectionService service = createService(false);
        SearchQueryRefiner refiner = new SearchQueryRefiner();
        RecommendationSpec llmSpec = new RecommendationSpec(
                RecommendationRequestMode.ENTITY_CONSTRAINED,
                "\u5f20\u96e8\u751f",
                "\u53d1\u6655",
                "\u4e24\u4f0a\u6218\u4e89\u767d\u8272\u624d\u60c5",
                12,
                true,
                true,
                true,
                true
        );

        RecommendationSpec normalized = invokeRepairResolvedSpec(
                service,
                llmSpec,
                refiner.analyze("\u63a8\u8350\u5f20\u96e8\u751f\u4e13\u8f91\u300a\u4e24\u4f0a\u6218\u4e89\u767d\u8272\u624d\u60c5\u300b\u91cc\u7684\u300a\u53d1\u6655\u300b\u4ee5\u53ca\u4ed6\u7684\u5176\u4ed6\u6b4c\u66f2")
        );

        assertThat(normalized.requestMode()).isEqualTo(RecommendationRequestMode.ENTITY_CONSTRAINED);
        assertThat(normalized.track()).isEqualTo("\u53d1\u6655");
        assertThat(normalized.album()).isEqualTo("\u4e24\u4f0a\u6218\u4e89\u767d\u8272\u624d\u60c5");
        assertThat(normalized.wantAdditionalTracks()).isTrue();
    }

    @Test
    void repairResolvedSpecShouldClearTrackAndAlbumForArtistOnlyMode() {
        LlmBackedRecommendationSelectionService service = createService(false);
        SearchQueryRefiner refiner = new SearchQueryRefiner();
        RecommendationSpec llmSpec = new RecommendationSpec(
                RecommendationRequestMode.ARTIST_ONLY,
                "\u5f20\u96e8\u751f",
                "\u53d1\u6655",
                "\u4e24\u4f0a\u6218\u4e89\u767d\u8272\u624d\u60c5",
                20,
                false,
                true,
                true,
                true
        );

        RecommendationSpec normalized = invokeRepairResolvedSpec(
                service,
                llmSpec,
                refiner.analyze("\u63a8\u835020\u9996\u5f20\u96e8\u751f\u7684\u6b4c")
        );

        assertThat(normalized.requestMode()).isEqualTo(RecommendationRequestMode.ARTIST_ONLY);
        assertThat(normalized.artist()).isEqualTo("\u5f20\u96e8\u751f");
        assertThat(normalized.track()).isNull();
        assertThat(normalized.album()).isNull();
        assertThat(normalized.wantAdditionalTracks()).isTrue();
        assertThat(normalized.preferSameArtist()).isTrue();
        assertThat(normalized.preferSameAlbum()).isFalse();
    }

    private LlmBackedRecommendationSelectionService createService(boolean liveLlmEnabled) {
        return new LlmBackedRecommendationSelectionService(
                new OpenAiProperties("", "https://api.moonshot.cn/v1", new OpenAiProperties.Chat("moonshot-v1-8k"), null),
                new AgentChatProperties(liveLlmEnabled, "system", "plan-v1", 12, 0.0, 1, 1, 800),
                musicQueryApplicationService,
                new SearchQueryRefiner()
        );
    }

    private RecommendationSpec invokeRepairResolvedSpec(
            LlmBackedRecommendationSelectionService service,
            RecommendationSpec spec,
            SearchQueryRefiner.SearchQueryHints hints
    ) {
        try {
            Method method = LlmBackedRecommendationSelectionService.class.getDeclaredMethod(
                    "repairResolvedSpec",
                    RecommendationSpec.class,
                    SearchQueryRefiner.SearchQueryHints.class
            );
            method.setAccessible(true);
            return (RecommendationSpec) method.invoke(service, spec, hints);
        } catch (NoSuchMethodException | IllegalAccessException exception) {
            throw new AssertionError(exception);
        } catch (InvocationTargetException exception) {
            throw new AssertionError(exception.getCause());
        }
    }

    private TrackDto track(String trackId, String title, String artistId, String albumName) {
        return new TrackDto(trackId, title, artistId, albumName, "album-1", 180000, null, null);
    }
}
