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
import com.agentmusic.agentmusic_backend.service.application.AgentApplicationService;
import com.agentmusic.agentmusic_backend.service.BackendRuntimeFacade;
import com.agentmusic.agentmusic_backend.service.ChatMemoryService;
import com.agentmusic.agentmusic_backend.service.LiveChatReplyService;
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

        PlanningContext planningContext = new PlanningContext(
                request,
                recentHistory,
                recentRecommendationSummaries
        );
        TaskPlanningResult planningResult = taskPlanner.createPlan(planningContext);
        AgentPlan plan = planningResult.plan();
        PlannerExecutionResult executionResult = executePlan(plan, planningContext, recentHistory);

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
            List<ChatMessageDto> recentHistory
    ) {
        if (plan.intent() != AgentIntent.CHAT_ONLY && plan.intent() != AgentIntent.UNKNOWN) {
            return taskExecutor.execute(plan, planningContext);
        }

        return liveChatReplyService.generateReply(
                        recentHistory,
                        planningContext.request().message()
                )
                .map(reply -> new PlannerExecutionResult(plan, reply))
                .orElseGet(() -> taskExecutor.execute(plan, planningContext));
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
