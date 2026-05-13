package com.agentmusic.agentmusic_backend.service.application;

public interface AgentChatStreamListener {

    AgentChatStreamListener NOOP = new AgentChatStreamListener() {
    };

    default void onStatus(String message) {
    }

    default void onReplyDelta(String delta) {
    }

    static AgentChatStreamListener noop() {
        return NOOP;
    }
}
