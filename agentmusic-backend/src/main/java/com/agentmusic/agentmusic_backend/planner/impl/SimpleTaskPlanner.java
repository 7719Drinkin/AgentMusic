package com.agentmusic.agentmusic_backend.planner.impl;

import com.agentmusic.agentmusic_backend.planner.AgentIntent;
import com.agentmusic.agentmusic_backend.planner.AgentPlan;
import com.agentmusic.agentmusic_backend.planner.PlanStep;
import com.agentmusic.agentmusic_backend.planner.PlanStepType;
import com.agentmusic.agentmusic_backend.planner.PlanningContext;
import com.agentmusic.agentmusic_backend.planner.TaskPlanner;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SimpleTaskPlanner implements TaskPlanner {

    @Override
    public AgentPlan createPlan(PlanningContext planningContext) {
        String message = planningContext.request().message() == null
                ? ""
                : planningContext.request().message().trim().toLowerCase();

        AgentIntent intent = classify(message);
        List<PlanStep> steps = new ArrayList<>();
        steps.add(new PlanStep(1, PlanStepType.READ_CHAT_CONTEXT, Map.of("limit", 20)));

        switch (intent) {
            case GET_ARTIST_INFO -> steps.add(new PlanStep(2, PlanStepType.LOOKUP_ARTIST, Map.of("query", message)));
            case CREATE_PLAYLIST -> steps.add(new PlanStep(2, PlanStepType.CREATE_RECOMMENDATION_PLAYLIST, Map.of("query", message)));
            case PLAYBACK_CONTROL -> {
                steps.add(new PlanStep(2, PlanStepType.READ_LOCAL_SESSION, Map.of()));
                steps.add(new PlanStep(3, PlanStepType.UPDATE_PLAYBACK_STATE, Map.of("query", message)));
            }
            case SEARCH_TRACK -> steps.add(new PlanStep(2, PlanStepType.SEARCH_TRACKS, Map.of("query", message)));
            case COMPOSITE_REQUEST -> {
                steps.add(new PlanStep(2, PlanStepType.SEARCH_TRACKS, Map.of("query", message)));
                steps.add(new PlanStep(3, PlanStepType.UPDATE_PLAYBACK_STATE, Map.of("query", message)));
            }
            case CHAT_ONLY, UNKNOWN -> {
            }
        }

        steps.add(new PlanStep(steps.size() + 1, PlanStepType.PERSIST_CHAT_REPLY, Map.of()));
        return new AgentPlan(intent, buildSummary(intent), List.copyOf(steps));
    }

    private AgentIntent classify(String message) {
        boolean mentionsPlay = message.contains("播放") || message.contains("暂停") || message.contains("切歌")
                || message.contains("shuffle") || message.contains("随机") || message.contains("repeat");
        boolean mentionsPlaylist = message.contains("歌单") || message.contains("playlist");
        boolean mentionsArtist = message.contains("歌手") || message.contains("artist");
        boolean mentionsSearch = message.contains("搜索") || message.contains("查") || message.contains("找");

        if (mentionsPlay && (mentionsSearch || mentionsArtist || mentionsPlaylist)) {
            return AgentIntent.COMPOSITE_REQUEST;
        }
        if (mentionsPlay) {
            return AgentIntent.PLAYBACK_CONTROL;
        }
        if (mentionsPlaylist) {
            return AgentIntent.CREATE_PLAYLIST;
        }
        if (mentionsArtist) {
            return AgentIntent.GET_ARTIST_INFO;
        }
        if (mentionsSearch) {
            return AgentIntent.SEARCH_TRACK;
        }
        return message.isBlank() ? AgentIntent.UNKNOWN : AgentIntent.CHAT_ONLY;
    }

    private String buildSummary(AgentIntent intent) {
        return switch (intent) {
            case CHAT_ONLY -> "Generate a conversational response without tool execution.";
            case SEARCH_TRACK -> "Search local or Spotify-backed track metadata.";
            case GET_ARTIST_INFO -> "Read artist metadata for the current request.";
            case CREATE_PLAYLIST -> "Build a recommendation playlist from the request.";
            case PLAYBACK_CONTROL -> "Read session and apply playback control.";
            case COMPOSITE_REQUEST -> "Combine discovery steps with playback control.";
            case UNKNOWN -> "Fallback conversational handling.";
        };
    }
}

