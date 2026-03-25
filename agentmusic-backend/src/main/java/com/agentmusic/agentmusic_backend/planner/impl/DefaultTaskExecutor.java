package com.agentmusic.agentmusic_backend.planner.impl;

import com.agentmusic.agentmusic_backend.domain.PlaybackMode;
import com.agentmusic.agentmusic_backend.dto.PlaybackSessionDto;
import com.agentmusic.agentmusic_backend.dto.TrackDto;
import com.agentmusic.agentmusic_backend.planner.AgentPlan;
import com.agentmusic.agentmusic_backend.planner.AgentIntent;
import com.agentmusic.agentmusic_backend.planner.PlannerExecutionResult;
import com.agentmusic.agentmusic_backend.planner.PlanningContext;
import com.agentmusic.agentmusic_backend.planner.TaskExecutor;
import com.agentmusic.agentmusic_backend.service.application.MusicQueryApplicationService;
import com.agentmusic.agentmusic_backend.service.application.PlaybackApplicationService;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DefaultTaskExecutor implements TaskExecutor {

    private final MusicQueryApplicationService musicQueryApplicationService;
    private final PlaybackApplicationService playbackApplicationService;

    public DefaultTaskExecutor(
            MusicQueryApplicationService musicQueryApplicationService,
            PlaybackApplicationService playbackApplicationService
    ) {
        this.musicQueryApplicationService = musicQueryApplicationService;
        this.playbackApplicationService = playbackApplicationService;
    }

    @Override
    public PlannerExecutionResult execute(AgentPlan plan, PlanningContext planningContext) {
        String message = planningContext.request().message() == null ? "" : planningContext.request().message();
        String userId = planningContext.request().userId();

        String reply = switch (plan.intent()) {
            case PLAYBACK_CONTROL -> executePlaybackControl(userId, message);
            case COMPOSITE_REQUEST -> executeCompositeRequest(userId, message);
            default -> "Planner skeleton ready. Intent=" + plan.intent()
                    + ", steps=" + plan.steps().size()
                    + ". Spotify bridge-mode execution wiring is pending for this intent.";
        };
        return new PlannerExecutionResult(plan, reply);
    }

    private String executePlaybackControl(String userId, String message) {
        if (containsPause(message)) {
            PlaybackSessionDto session = playbackApplicationService.pause(userId, null);
            return "Playback paused. Current local session device=" + session.deviceId() + ".";
        }

        PlaybackMode playbackMode = inferPlaybackMode(message);
        PlaybackSessionDto current = playbackApplicationService.syncBridgeState(userId).orElse(null);
        if (current != null && current.currentTrackId() != null) {
            PlaybackSessionDto session = playbackApplicationService.playTrack(
                    userId,
                    current.currentTrackId(),
                    current.deviceId(),
                    playbackMode
            );
            return "Playback updated using current track " + session.currentTrackId()
                    + " with mode " + session.playbackMode() + ".";
        }

        return "Playback control request received, but no track is currently available to resume.";
    }

    private String executeCompositeRequest(String userId, String message) {
        List<TrackDto> tracks = musicQueryApplicationService.searchTracks(message, 5);
        if (tracks.isEmpty()) {
            return "No matching track was found for the current request.";
        }

        TrackDto selected = tracks.getFirst();
        PlaybackMode playbackMode = inferPlaybackMode(message);
        PlaybackSessionDto session = playbackApplicationService.playTrack(
                userId,
                selected.trackId(),
                null,
                playbackMode
        );
        return "Selected track " + selected.title()
                + " and started playback in mode " + session.playbackMode() + ".";
    }

    private PlaybackMode inferPlaybackMode(String message) {
        String normalized = message.toLowerCase();
        if (normalized.contains("随机") || normalized.contains("shuffle")) {
            return PlaybackMode.SHUFFLE;
        }
        if (normalized.contains("单曲循环") || normalized.contains("single loop")) {
            return PlaybackMode.SINGLE_LOOP;
        }
        if (normalized.contains("列表循环") || normalized.contains("repeat all") || normalized.contains("repeat")) {
            return PlaybackMode.LIST_LOOP;
        }
        return PlaybackMode.SEQUENTIAL;
    }

    private boolean containsPause(String message) {
        String normalized = message.toLowerCase();
        return normalized.contains("暂停") || normalized.contains("pause");
    }
}
