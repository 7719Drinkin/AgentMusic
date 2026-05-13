package com.agentmusic.agentmusic_backend.service.application.impl;

import com.agentmusic.agentmusic_backend.domain.ChatRole;
import com.agentmusic.agentmusic_backend.planner.AgentIntent;
import com.agentmusic.agentmusic_backend.planner.AgentPlan;
import com.agentmusic.agentmusic_backend.planner.PlannerExecutionResult;
import com.agentmusic.agentmusic_backend.planner.PlanningContext;
import com.agentmusic.agentmusic_backend.planner.TaskExecutor;
import com.agentmusic.agentmusic_backend.planner.TaskPlanner;
import com.agentmusic.agentmusic_backend.planner.TaskPlanningResult;
import com.agentmusic.agentmusic_backend.planner.impl.LlmBackedTaskPlanner;
import com.agentmusic.agentmusic_backend.service.BackendRuntimeFacade;
import com.agentmusic.agentmusic_backend.service.ChatMemoryService;
import com.agentmusic.agentmusic_backend.service.LiveChatReplyService;
import com.agentmusic.agentmusic_backend.service.application.AgentApplicationService;
import com.agentmusic.agentmusic_backend.service.application.AgentChatStreamListener;
import com.agentmusic.agentmusic_backend.web.dto.AgentChatRequest;
import com.agentmusic.agentmusic_backend.web.dto.AgentChatResponse;
import com.agentmusic.agentmusic_backend.web.dto.ChatMessageDto;
import com.agentmusic.agentmusic_backend.web.dto.PlaylistDto;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DefaultAgentApplicationService implements AgentApplicationService {

    private final ChatMemoryService chatMemoryService;
    private final BackendRuntimeFacade backendRuntimeFacade;
    private final TaskPlanner taskPlanner;
    private final TaskExecutor taskExecutor;
    private final LiveChatReplyService liveChatReplyService;

    public DefaultAgentApplicationService(
            ChatMemoryService chatMemoryService,
            BackendRuntimeFacade backendRuntimeFacade,
            TaskPlanner taskPlanner,
            TaskExecutor taskExecutor,
            LiveChatReplyService liveChatReplyService
    ) {
        this.chatMemoryService = chatMemoryService;
        this.backendRuntimeFacade = backendRuntimeFacade;
        this.taskPlanner = taskPlanner;
        this.taskExecutor = taskExecutor;
        this.liveChatReplyService = liveChatReplyService;
    }

    @Override
    public AgentChatResponse chat(AgentChatRequest request) {
        return chat(request, AgentChatStreamListener.noop());
    }

    @Override
    public AgentChatResponse chat(AgentChatRequest request, AgentChatStreamListener streamListener) {
        AgentChatStreamListener listener = streamListener == null
                ? AgentChatStreamListener.noop()
                : streamListener;
        listener.onStatus("已收到消息，正在读取对话上下文...");

        ChatMessageDto userMessage = chatMemoryService.appendMessage(
                request.userId(),
                ChatRole.USER,
                request.message(),
                Map.of("voiceInput", request.voiceInput())
        );

        List<ChatMessageDto> recentHistory = chatMemoryService.getRecentMessages(request.userId(), 20).stream()
                .filter(message -> !message.id().equals(userMessage.id()))
                .toList();
        List<String> recentRecommendationSummaries = backendRuntimeFacade.getRecentPlaylists(request.userId()).stream()
                .limit(3)
                .map(this::summarizePlaylist)
                .filter(StringUtils::hasText)
                .toList();

        listener.onStatus("正在让 Kimi 理解当前请求...");
        PlanningContext planningContext = new PlanningContext(
                request,
                recentHistory,
                recentRecommendationSummaries
        );
        TaskPlanningResult planningResult = taskPlanner.createPlan(planningContext);
        AgentPlan plan = planningResult.plan();
        listener.onStatus(resolvePlanStatus(plan));
        PlannerExecutionResult executionResult = executePlan(plan, planningContext, recentHistory, listener);

        Map<String, Object> replyMetadata = new HashMap<>();
        var runtimeStatus = liveChatReplyService.getRuntimeStatus();
        replyMetadata.put("stage", resolveReplyStage(plan, planningResult));
        replyMetadata.put("intent", executionResult.plan().intent().name());
        replyMetadata.put("stepCount", executionResult.plan().steps().size());
        replyMetadata.put("planSummary", executionResult.plan().summary());
        replyMetadata.put("planningSource", planningResult.source());
        replyMetadata.put("planningFallbackUsed", planningResult.fallbackUsed());
        if (planningResult.fallbackReason() != null) {
            replyMetadata.put("planningFallbackReason", planningResult.fallbackReason());
        }
        replyMetadata.put("liveLlmEnabledConfigured", runtimeStatus.liveLlmEnabledConfigured());
        replyMetadata.put("openAiKeyPresent", runtimeStatus.openAiKeyPresent());
        replyMetadata.put("openAiModelId", runtimeStatus.openAiModelId());
        replyMetadata.put("liveLlmAvailable", runtimeStatus.liveLlmAvailable());

        ChatMessageDto reply = chatMemoryService.appendMessage(
                request.userId(),
                ChatRole.AGENT,
                executionResult.replyMessage(),
                replyMetadata
        );

        return new AgentChatResponse(
                reply,
                backendRuntimeFacade.getActiveSession(request.userId()).orElse(null),
                backendRuntimeFacade.getRecentPlaylists(request.userId())
        );
    }

    @Override
    public List<ChatMessageDto> getRecentHistory(String userId, int limit) {
        return chatMemoryService.getRecentMessages(userId, limit);
    }

    private PlannerExecutionResult executePlan(
            AgentPlan plan,
            PlanningContext planningContext,
            List<ChatMessageDto> recentHistory,
            AgentChatStreamListener streamListener
    ) {
        if (plan.intent() != AgentIntent.CHAT_ONLY && plan.intent() != AgentIntent.UNKNOWN) {
            streamListener.onStatus("正在检索音乐内容并执行推荐任务...");
            PlannerExecutionResult executionResult = taskExecutor.execute(plan, planningContext);
            streamListener.onStatus("正在让 Kimi 组织最终回复...");
            return liveChatReplyService.generateStreamingNarration(
                            executionResult.plan(),
                            planningContext,
                            executionResult.replyMessage(),
                            streamListener::onReplyDelta
                    )
                    .map(reply -> new PlannerExecutionResult(executionResult.plan(), reply))
                    .orElse(executionResult);
        }

        streamListener.onStatus("正在连接 Kimi 生成回复...");
        return liveChatReplyService.generateStreamingReply(
                        recentHistory,
                        planningContext.request().message(),
                        streamListener::onReplyDelta
                )
                .map(reply -> new PlannerExecutionResult(plan, reply))
                .orElseGet(() -> {
                    streamListener.onStatus("Kimi 流式回复不可用，正在使用本地兜底回复...");
                    return taskExecutor.execute(plan, planningContext);
                });
    }

    private String resolvePlanStatus(AgentPlan plan) {
        return switch (plan.intent()) {
            case CHAT_ONLY, UNKNOWN -> "已识别为对话请求，准备流式回复...";
            case RECOMMEND_PLAYLIST, PLAY_RECOMMENDATION -> "已识别为推荐请求，正在准备候选歌曲...";
            case PLAYBACK_CONTROL -> "已识别为播放控制请求，正在确认设备和播放状态...";
            case TRACK_LOOKUP, ARTIST_LOOKUP -> "已识别为音乐查询请求，正在检索 Spotify...";
            case PLAYLIST_HISTORY_ACCESS -> "已识别为歌单历史请求，正在读取最近推荐...";
            case COMPOSITE_REQUEST -> "已识别为复合请求，正在拆解执行步骤...";
        };
    }

    private String resolveReplyStage(AgentPlan plan, TaskPlanningResult planningResult) {
        if (LlmBackedTaskPlanner.LLM_SOURCE.equals(planningResult.source())) {
            return "llm-planner";
        }
        if (planningResult.fallbackUsed()) {
            return "planner-fallback";
        }
        if ((plan.intent() == AgentIntent.CHAT_ONLY || plan.intent() == AgentIntent.UNKNOWN)
                && liveChatReplyService.isEnabled()) {
            return "live-llm-or-fallback";
        }
        return "planner-skeleton";
    }

    private String summarizePlaylist(PlaylistDto playlist) {
        if (playlist == null || !StringUtils.hasText(playlist.name())) {
            return "";
        }

        String topTracks = playlist.tracks() == null
                ? ""
                : playlist.tracks().stream()
                .map(playlistTrack -> playlistTrack.track())
                .filter(Objects::nonNull)
                .map(track -> track.title())
                .filter(StringUtils::hasText)
                .limit(3)
                .reduce((left, right) -> left + " / " + right)
                .orElse("");

        if (!StringUtils.hasText(topTracks)) {
            return playlist.name().trim();
        }
        return playlist.name().trim() + " -> " + topTracks;
    }
}
