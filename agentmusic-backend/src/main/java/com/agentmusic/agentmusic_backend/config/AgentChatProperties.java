package com.agentmusic.agentmusic_backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent.chat")
public record AgentChatProperties(
        boolean liveLlmEnabled,
        String systemPrompt
) {
}
