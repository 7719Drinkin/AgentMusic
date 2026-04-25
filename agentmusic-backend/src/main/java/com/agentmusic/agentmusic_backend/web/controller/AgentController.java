package com.agentmusic.agentmusic_backend.web.controller;

import com.agentmusic.agentmusic_backend.web.dto.AgentChatRequest;
import com.agentmusic.agentmusic_backend.web.dto.AgentChatResponse;
import com.agentmusic.agentmusic_backend.web.dto.ChatMessageDto;
import com.agentmusic.agentmusic_backend.web.dto.AgentRuntimeStatusDto;
import com.agentmusic.agentmusic_backend.service.LiveChatReplyService;
import com.agentmusic.agentmusic_backend.service.application.AgentApplicationService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentApplicationService agentApplicationService;
    private final LiveChatReplyService liveChatReplyService;

    public AgentController(
            AgentApplicationService agentApplicationService,
            LiveChatReplyService liveChatReplyService
    ) {
        this.agentApplicationService = agentApplicationService;
        this.liveChatReplyService = liveChatReplyService;
    }

    @PostMapping("/chat")
    public AgentChatResponse chat(@RequestBody AgentChatRequest request) {
        return agentApplicationService.chat(request);
    }

    @GetMapping("/history/{userId}")
    public List<ChatMessageDto> getRecentHistory(
            @PathVariable String userId,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return agentApplicationService.getRecentHistory(userId, limit);
    }

    @GetMapping("/runtime-status")
    public AgentRuntimeStatusDto getRuntimeStatus() {
        return liveChatReplyService.getRuntimeStatus();
    }
}
