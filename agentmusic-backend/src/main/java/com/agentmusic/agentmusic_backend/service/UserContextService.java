package com.agentmusic.agentmusic_backend.service;

import com.agentmusic.agentmusic_backend.domain.User;

public interface UserContextService {

    User save(User user);

    User getRequired(String userId);
}

