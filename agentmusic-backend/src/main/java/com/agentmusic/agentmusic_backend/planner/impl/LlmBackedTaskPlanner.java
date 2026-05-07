package com.agentmusic.agentmusic_backend.planner.impl;

import com.agentmusic.agentmusic_backend.config.AgentChatProperties;
import com.agentmusic.agentmusic_backend.planner.PlanningContext;
import com.agentmusic.agentmusic_backend.planner.TaskPlanner;
import com.agentmusic.agentmusic_backend.planner.TaskPlanningResult;
import com.agentmusic.agentmusic_backend.planner.llm.OpenAiCompatiblePlanningClient;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Component
@Primary
public class LlmBackedTaskPlanner implements TaskPlanner {

    public static final String LLM_SOURCE = "llm-harness";
    public static final String FALLBACK_SOURCE = "simple-task-planner-fallback";
    private static final Logger log = LoggerFactory.getLogger(LlmBackedTaskPlanner.class);

    private final AgentChatProperties agentChatProperties;
    private final OpenAiCompatiblePlanningClient planningClient;
    private final SimpleTaskPlanner fallbackPlanner;

    public LlmBackedTaskPlanner(
            AgentChatProperties agentChatProperties,
            OpenAiCompatiblePlanningClient planningClient,
            SimpleTaskPlanner fallbackPlanner
    ) {
        this.agentChatProperties = agentChatProperties;
        this.planningClient = planningClient;
        this.fallbackPlanner = fallbackPlanner;
    }

    @Override
    public TaskPlanningResult createPlan(PlanningContext planningContext) {
        if (!isLivePlanningEnabled()) {
            return fallbackPlanner.createPlan(planningContext);
        }

        try {
            var result = planningClient.generateValidatedPlan(planningContext);
            return new TaskPlanningResult(result.plan(), LLM_SOURCE, false);
        } catch (RuntimeException exception) {
            String fallbackReason = classifyFailure(exception);
            log.warn(
                    "LLM planner fallback triggered. reason={}, userId={}, message={}",
                    fallbackReason,
                    planningContext.request().userId(),
                    abbreviate(planningContext.request().message()),
                    exception
            );
            TaskPlanningResult fallback = fallbackPlanner.createPlan(planningContext);
            return new TaskPlanningResult(fallback.plan(), FALLBACK_SOURCE, true, fallbackReason);
        }
    }

    private boolean isLivePlanningEnabled() {
        return agentChatProperties.liveLlmEnabled() && planningClient.isEnabled();
    }

    private String classifyFailure(Throwable throwable) {
        if (findCause(throwable, WebClientResponseException.TooManyRequests.class) != null) {
            return "provider-rate-limit";
        }

        WebClientResponseException responseException = findCause(throwable, WebClientResponseException.class);
        if (responseException != null) {
            if (responseException.getStatusCode().is5xxServerError()) {
                return "provider-server-error";
            }
            if (responseException instanceof WebClientResponseException.BadRequest) {
                return "provider-bad-request";
            }
            return "provider-http-" + responseException.getStatusCode().value();
        }

        if (findCause(throwable, TimeoutException.class) != null || messageContains(throwable, "timeout")) {
            return "provider-timeout";
        }

        if (findCause(throwable, WebClientRequestException.class) != null) {
            return "provider-network";
        }

        IllegalArgumentException validationException = findCause(throwable, IllegalArgumentException.class);
        if (validationException != null && messageContains(throwable, "failed harness validation")) {
            return "harness-validation";
        }

        if (messageContains(throwable, "response content is empty")) {
            return "provider-empty-response";
        }

        return "unknown";
    }

    private boolean messageContains(Throwable throwable, String fragment) {
        if (throwable == null || fragment == null || fragment.isBlank()) {
            return false;
        }

        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.toLowerCase().contains(fragment.toLowerCase())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private <T extends Throwable> T findCause(Throwable throwable, Class<T> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    private String abbreviate(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 120) {
            return normalized;
        }
        return normalized.substring(0, 120) + "...";
    }
}
