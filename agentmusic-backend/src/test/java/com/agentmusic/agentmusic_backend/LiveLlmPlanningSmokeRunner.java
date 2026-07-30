package com.agentmusic.agentmusic_backend;

import com.agentmusic.agentmusic_backend.config.AgentChatProperties;
import com.agentmusic.agentmusic_backend.config.OpenAiProperties;
import com.agentmusic.agentmusic_backend.domain.ChatRole;
import com.agentmusic.agentmusic_backend.planner.PlanningContext;
import com.agentmusic.agentmusic_backend.planner.llm.AgentLlmPlanningHarness;
import com.agentmusic.agentmusic_backend.planner.llm.OpenAiCompatiblePlanningClient;
import com.agentmusic.agentmusic_backend.web.dto.AgentChatRequest;
import com.agentmusic.agentmusic_backend.web.dto.ChatMessageDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.springframework.util.StringUtils;

public final class LiveLlmPlanningSmokeRunner {

    private LiveLlmPlanningSmokeRunner() {
    }

    public static void main(String[] args) throws IOException {
        Properties properties = loadEffectiveProperties();
        OpenAiProperties openAiProperties = buildOpenAiProperties(properties);
        AgentChatProperties agentChatProperties = buildAgentChatProperties(properties);

        AgentLlmPlanningHarness harness = new AgentLlmPlanningHarness(agentChatProperties, new ObjectMapper());
        OpenAiCompatiblePlanningClient planningClient = new OpenAiCompatiblePlanningClient(
                openAiProperties,
                agentChatProperties,
                harness
        );

        PlanningContext context = new PlanningContext(
                new AgentChatRequest(
                        "demo-user",
                        "Recommend atmospheric Mandarin songs for a late-night train ride and start playback.",
                        false
                ),
                List.of(
                        new ChatMessageDto(
                                "history-1",
                                ChatRole.AGENT,
                                "Previous playlist included River and other city-pop tracks.",
                                Map.of(),
                                LocalDateTime.of(2026, 5, 1, 20, 1)
                        )
                ),
                List.of("Night Ride -> River / Everyday / Missing You")
        );

        var result = planningClient.generateValidatedPlan(context);
        System.out.println("intent=" + result.plan().intent());
        System.out.println("summary=" + result.plan().summary());
        System.out.println("steps=" + result.plan().steps().stream().map(step -> step.type().name()).toList());
    }

    private static Properties loadEffectiveProperties() throws IOException {
        Properties properties = new Properties();
        loadFromPath(properties, Path.of("src", "main", "resources", "application.properties"));
        loadFromPath(properties, Path.of("application-local.properties"));
        return properties;
    }

    private static void loadFromPath(Properties properties, Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }

        try (InputStream inputStream = Files.newInputStream(path)) {
            Properties override = new Properties();
            override.load(inputStream);
            properties.putAll(override);
        }
    }

    private static OpenAiProperties buildOpenAiProperties(Properties properties) {
        return new OpenAiProperties(
                resolveValue(properties.getProperty("openai.api-key")),
                resolveValue(properties.getProperty("openai.base-url")),
                new OpenAiProperties.Chat(resolveValue(properties.getProperty("openai.chat.model-id"))),
                new OpenAiProperties.Api(resolveValue(properties.getProperty("openai.api.key")))
        );
    }

    private static AgentChatProperties buildAgentChatProperties(Properties properties) {
        return new AgentChatProperties(
                Boolean.parseBoolean(resolveValue(properties.getProperty("agent.chat.live-llm-enabled"))),
                resolveValue(properties.getProperty("agent.chat.system-prompt")),
                resolveValue(properties.getProperty("agent.chat.planning-harness-version")),
                Integer.parseInt(resolveValue(properties.getProperty("agent.chat.llm-recent-message-limit"))),
                Double.parseDouble(resolveValue(properties.getProperty("agent.chat.llm-temperature"))),
                Integer.parseInt(resolveValue(properties.getProperty("agent.chat.llm-planning-max-repair-attempts"))),
                Integer.parseInt(resolveValue(properties.getProperty("agent.chat.llm-http-max-retries"))),
                Long.parseLong(resolveValue(properties.getProperty("agent.chat.llm-http-retry-backoff-ms")))
        );
    }

    private static String resolveValue(String rawValue) {
        if (rawValue == null) {
            return "";
        }

        String trimmed = rawValue.trim();
        if (!trimmed.startsWith("${") || !trimmed.endsWith("}")) {
            return trimmed;
        }

        String body = trimmed.substring(2, trimmed.length() - 1);
        int separatorIndex = body.indexOf(':');
        String environmentKey = separatorIndex >= 0 ? body.substring(0, separatorIndex) : body;
        String fallback = separatorIndex >= 0 ? body.substring(separatorIndex + 1) : "";
        String environmentValue = System.getenv(environmentKey);
        return StringUtils.hasText(environmentValue) ? environmentValue.trim() : fallback.trim();
    }
}
