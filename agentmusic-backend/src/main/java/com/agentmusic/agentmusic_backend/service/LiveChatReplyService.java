package com.agentmusic.agentmusic_backend.service;

import java.util.List;
import java.util.Optional;

public interface LiveChatReplyService {

    Optional<String> generateReply(List<String> recentMessages, String userMessage);

    boolean isEnabled();
}
