package com.agentmusic.agentmusic_backend.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.agentmusic.agentmusic_backend.web.dto.AgentChatRequest;
import com.agentmusic.agentmusic_backend.web.dto.AgentChatResponse;
import com.agentmusic.agentmusic_backend.web.dto.ChatMessageDto;
import com.agentmusic.agentmusic_backend.web.dto.AgentRuntimeStatusDto;
import com.agentmusic.agentmusic_backend.service.LiveChatReplyService;
import com.agentmusic.agentmusic_backend.service.application.AgentApplicationService;
import com.agentmusic.agentmusic_backend.service.application.AgentChatStreamListener;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentApplicationService agentApplicationService;
    private final LiveChatReplyService liveChatReplyService;
    private final ObjectMapper objectMapper;

    public AgentController(
            AgentApplicationService agentApplicationService,
            LiveChatReplyService liveChatReplyService,
            ObjectMapper objectMapper
    ) {
        this.agentApplicationService = agentApplicationService;
        this.liveChatReplyService = liveChatReplyService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/chat")
    public AgentChatResponse chat(@RequestBody AgentChatRequest request) {
        return agentApplicationService.chat(request);
    }

    @PostMapping(value = "/chat/stream", produces = "application/x-ndjson")
    public ResponseEntity<StreamingResponseBody> chatStream(@RequestBody AgentChatRequest request) {
        StreamingResponseBody stream = outputStream -> {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
            AgentChatStreamListener listener = new AgentChatStreamListener() {
                @Override
                public void onStatus(String message) {
                    writeStreamEventUnchecked(writer, "status", Map.of("message", message));
                }

                @Override
                public void onReplyDelta(String delta) {
                    writeStreamEventUnchecked(writer, "reply-delta", Map.of("delta", delta));
                }
            };

            try {
                AgentChatResponse response = agentApplicationService.chat(request, listener);
                writeStreamEvent(writer, "complete", Map.of("response", response));
            } catch (UncheckedIOException exception) {
                throw exception.getCause();
            } catch (RuntimeException exception) {
                writeStreamEvent(writer, "error", Map.of("message", exception.getMessage() == null ? "Agent request failed." : exception.getMessage()));
            }
        };
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/x-ndjson"))
                .body(stream);
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

    private void writeStreamEventUnchecked(BufferedWriter writer, String type, Object payload) {
        try {
            writeStreamEvent(writer, type, payload);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private void writeStreamEvent(BufferedWriter writer, String type, Object payload) throws IOException {
        writer.write(objectMapper.writeValueAsString(Map.of(
                "type", type,
                "payload", payload
        )));
        writer.newLine();
        writer.flush();
    }
}
