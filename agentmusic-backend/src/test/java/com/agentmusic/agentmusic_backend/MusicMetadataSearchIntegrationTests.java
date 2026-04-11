package com.agentmusic.agentmusic_backend;

import com.agentmusic.agentmusic_backend.dto.TrackDto;
import com.agentmusic.agentmusic_backend.service.SpotifyBridgeAuthService;
import com.agentmusic.agentmusic_backend.service.application.MusicQueryApplicationService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest
class MusicMetadataSearchIntegrationTests {

    @Autowired
    private MusicQueryApplicationService musicQueryApplicationService;

    @MockitoBean
    private SpotifyBridgeAuthService spotifyBridgeAuthService;

    @Test
    void shouldReturnDemoTracksForNaturalLanguageRecommendationQuery() {
        when(spotifyBridgeAuthService.getValidAccessToken()).thenReturn(Optional.empty());

        List<TrackDto> tracks = musicQueryApplicationService.searchTracks("给我来点轻松的粤语歌，随机播放", 10);

        assertThat(tracks).isNotEmpty();
        assertThat(tracks).extracting(TrackDto::trackId).contains("demo-track-1");
    }
}
