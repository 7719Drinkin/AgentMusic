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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);
    private static final long STREAM_HEARTBEAT_INTERVAL_MS = 10_000L;

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
            Object writeLock = new Object();
            AtomicBoolean streamClosed = new AtomicBoolean(false);
            Thread heartbeatThread = startHeartbeat(writer, writeLock, streamClosed);
            AgentChatStreamListener listener = new AgentChatStreamListener() {
                @Override
                public void onStatus(String message) {
                    writeStreamEventUnchecked(writer, writeLock, "status", Map.of("message", message));
                }

                @Override
                public void onReplyDelta(String delta) {
                    writeStreamEventUnchecked(writer, writeLock, "reply-delta", Map.of("delta", delta));
                }
            };

            try {
                AgentChatResponse response = agentApplicationService.chat(request, listener);
                writeStreamEvent(writer, writeLock, "complete", Map.of("response", response));
            } catch (UncheckedIOException exception) {
                throw exception.getCause();
            } catch (RuntimeException exception) {
                writeStreamEvent(writer, writeLock, "error", Map.of("message", exception.getMessage() == null ? "Agent request failed." : exception.getMessage()));
            } finally {
                streamClosed.set(true);
                heartbeatThread.interrupt();
            }
        };
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/x-ndjson"))
                .header("Cache-Control", "no-cache")
                .header("X-Accel-Buffering", "no")
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

    private Thread startHeartbeat(BufferedWriter writer, Object writeLock, AtomicBoolean streamClosed) {
        Thread heartbeatThread = new Thread(() -> {
            while (!streamClosed.get()) {
                try {
                    Thread.sleep(STREAM_HEARTBEAT_INTERVAL_MS);
                    if (!streamClosed.get()) {
                        writeStreamEvent(writer, writeLock, "heartbeat", Map.of("timestamp", Instant.now().toString()));
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (IOException | UncheckedIOException exception) {
                    streamClosed.set(true);
                    log.debug("Agent chat stream heartbeat stopped because the stream is no longer writable.", exception);
                    return;
                }
            }
        }, "agent-chat-stream-heartbeat");
        heartbeatThread.setDaemon(true);
        heartbeatThread.start();
        return heartbeatThread;
    }

    private void writeStreamEventUnchecked(BufferedWriter writer, Object writeLock, String type, Object payload) {
        try {
            writeStreamEvent(writer, writeLock, type, payload);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private void writeStreamEvent(BufferedWriter writer, Object writeLock, String type, Object payload) throws IOException {
        synchronized (writeLock) {
            writer.write(objectMapper.writeValueAsString(Map.of(
                    "type", type,
                    "payload", payload
            )));
            writer.newLine();
            writer.flush();
        }
    }
}
