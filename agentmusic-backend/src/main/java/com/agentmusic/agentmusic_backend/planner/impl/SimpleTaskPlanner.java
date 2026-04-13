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
            case ARTIST_LOOKUP -> steps.add(new PlanStep(2, PlanStepType.LOOKUP_ARTIST, Map.of("query", message)));
            case TRACK_LOOKUP -> steps.add(new PlanStep(2, PlanStepType.LOOKUP_TRACK, Map.of("query", message)));
            case RECOMMEND_PLAYLIST -> appendRecommendationSteps(steps, message, false);
            case PLAY_RECOMMENDATION -> appendRecommendationSteps(steps, message, true);
            case PLAYLIST_HISTORY_ACCESS -> steps.add(new PlanStep(2, PlanStepType.READ_PLAYLIST_HISTORY, Map.of("limit", 10)));
            case PLAYBACK_CONTROL -> {
                steps.add(new PlanStep(2, PlanStepType.READ_LOCAL_SESSION, Map.of()));
                steps.add(new PlanStep(3, PlanStepType.UPDATE_PLAYBACK_STATE, Map.of("query", message)));
            }
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
        boolean mentionsPlay = containsAny(
                message,
                "播放",
                "播一首",
                "放歌",
                "继续播放",
                "开始播放",
                "play",
                "resume",
                "shuffle",
                "repeat",
                "随机"
        );
        boolean mentionsPause = containsAny(message, "暂停", "pause", "stop");
        boolean mentionsPlaylist = containsAny(message, "歌单", "playlist", "mix");
        boolean mentionsArtist = containsAny(message, "歌手", "艺人", "artist");
        boolean mentionsSearch = containsAny(message, "搜索", "查找", "搜一个", "查一个", "search", "find");
        boolean mentionsHistory = containsAny(message, "历史歌单", "上次推荐", "上一版歌单", "之前的歌单", "最近推荐");
        boolean recommendOnly = containsAny(
                message,
                "先不播放",
                "不要播放",
                "不要直接播放",
                "先别播",
                "我先看看",
                "先看",
                "recommend only"
        );
        boolean mentionsRecommendation = containsAny(
                message,
                "推荐",
                "来点",
                "给我来",
                "来一些",
                "来一首",
                "歌单",
                "mix",
                "适合",
                "想听"
        );

        if (mentionsHistory) {
            return AgentIntent.PLAYLIST_HISTORY_ACCESS;
        }
        if (mentionsRecommendation && recommendOnly) {
            return AgentIntent.RECOMMEND_PLAYLIST;
        }
        if (mentionsRecommendation) {
            return AgentIntent.PLAY_RECOMMENDATION;
        }
        if (mentionsPlay && (mentionsSearch || mentionsArtist || mentionsPlaylist) && !mentionsPause) {
            return AgentIntent.COMPOSITE_REQUEST;
        }
        if (mentionsPlay) {
            return AgentIntent.PLAYBACK_CONTROL;
        }
        if (mentionsArtist) {
            return AgentIntent.ARTIST_LOOKUP;
        }
        if (mentionsSearch) {
            return AgentIntent.TRACK_LOOKUP;
        }
        return message.isBlank() ? AgentIntent.UNKNOWN : AgentIntent.CHAT_ONLY;
    }

    private String buildSummary(AgentIntent intent) {
        return switch (intent) {
            case CHAT_ONLY -> "Generate a conversational response without tool execution.";
            case TRACK_LOOKUP -> "Search local or Spotify-backed track metadata.";
            case ARTIST_LOOKUP -> "Read artist metadata for the current request.";
            case RECOMMEND_PLAYLIST -> "Build and persist a recommendation playlist without autoplay.";
            case PLAY_RECOMMENDATION -> "Build a recommendation playlist first, then start playback.";
            case PLAYLIST_HISTORY_ACCESS -> "Read recent local recommendation playlists.";
            case PLAYBACK_CONTROL -> "Read session and apply playback control.";
            case COMPOSITE_REQUEST -> "Combine discovery steps with playback control.";
            case UNKNOWN -> "Fallback conversational handling.";
        };
    }

    private void appendRecommendationSteps(List<PlanStep> steps, String message, boolean autoPlay) {
        steps.add(new PlanStep(2, PlanStepType.READ_USER_PREFERENCES, Map.of()));
        steps.add(new PlanStep(3, PlanStepType.READ_PLAYLIST_HISTORY, Map.of("limit", 10)));
        steps.add(new PlanStep(4, PlanStepType.GENERATE_RECOMMENDATION_CANDIDATES, Map.of("query", message)));
        steps.add(new PlanStep(5, PlanStepType.RANK_RECOMMENDATION_CANDIDATES, Map.of("query", message)));
        steps.add(new PlanStep(6, PlanStepType.CREATE_RECOMMENDATION_PLAYLIST, Map.of("query", message)));
        if (autoPlay) {
            steps.add(new PlanStep(7, PlanStepType.UPDATE_PLAYBACK_STATE, Map.of("query", message)));
        }
    }

    private boolean containsAny(String message, String... keywords) {
        for (String keyword : keywords) {
            if (message.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
