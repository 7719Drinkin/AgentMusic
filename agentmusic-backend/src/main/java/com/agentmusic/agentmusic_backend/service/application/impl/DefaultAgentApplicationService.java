package com.agentmusic.agentmusic_backend.service.application.impl;

import com.agentmusic.agentmusic_backend.domain.ChatRole;
import com.agentmusic.agentmusic_backend.dto.AgentChatRequest;
import com.agentmusic.agentmusic_backend.dto.AgentChatResponse;
import com.agentmusic.agentmusic_backend.dto.ChatMessageDto;
import com.agentmusic.agentmusic_backend.planner.AgentIntent;
import com.agentmusic.agentmusic_backend.planner.PlannerExecutionResult;
import com.agentmusic.agentmusic_backend.planner.AgentPlan;
import com.agentmusic.agentmusic_backend.planner.PlanningContext;
import com.agentmusic.agentmusic_backend.planner.TaskExecutor;
import com.agentmusic.agentmusic_backend.planner.TaskPlanner;
import com.agentmusic.agentmusic_backend.service.BackendRuntimeFacade;
import com.agentmusic.agentmusic_backend.service.ChatMemoryService;
import com.agentmusic.agentmusic_backend.service.LiveChatReplyService;
import java.util.HashMap;
import com.agentmusic.agentmusic_backend.service.application.AgentApplicationService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

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
        chatMemoryService.appendMessage(
                request.userId(),
                ChatRole.USER,
                request.message(),
                Map.of("voiceInput", request.voiceInput())
        );

        PlanningContext planningContext = new PlanningContext(
                request,
                chatMemoryService.getRecentMessages(request.userId(), 20).stream()
                        .map(ChatMessageDto::message)
                        .toList()
        );
        AgentPlan plan = taskPlanner.createPlan(planningContext);
        PlannerExecutionResult executionResult = executePlan(plan, planningContext);

        Map<String, Object> replyMetadata = new HashMap<>();
        var runtimeStatus = liveChatReplyService.getRuntimeStatus();
        replyMetadata.put("stage", resolveReplyStage(plan));
        replyMetadata.put("intent", executionResult.plan().intent().name());
        replyMetadata.put("stepCount", executionResult.plan().steps().size());
        replyMetadata.put("planSummary", executionResult.plan().summary());
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

    private PlannerExecutionResult executePlan(AgentPlan plan, PlanningContext planningContext) {
        if (plan.intent() != AgentIntent.CHAT_ONLY && plan.intent() != AgentIntent.UNKNOWN) {
            return taskExecutor.execute(plan, planningContext);
        }

        return liveChatReplyService.generateReply(
                        planningContext.recentMessages(),
                        planningContext.request().message()
                )
                .map(reply -> new PlannerExecutionResult(plan, reply))
                .orElseGet(() -> taskExecutor.execute(plan, planningContext));
    }

    private String resolveReplyStage(AgentPlan plan) {
        if ((plan.intent() == AgentIntent.CHAT_ONLY || plan.intent() == AgentIntent.UNKNOWN)
                && liveChatReplyService.isEnabled()) {
            return "live-llm-or-fallback";
        }
        return "planner-skeleton";
    }
}
