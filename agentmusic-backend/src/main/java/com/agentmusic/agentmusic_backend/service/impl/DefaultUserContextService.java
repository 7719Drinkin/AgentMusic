package com.agentmusic.agentmusic_backend.service.impl;

import com.agentmusic.agentmusic_backend.domain.User;
import com.agentmusic.agentmusic_backend.exception.NotFoundException;
import com.agentmusic.agentmusic_backend.repository.UserRepository;
import com.agentmusic.agentmusic_backend.service.UserContextService;
import org.springframework.stereotype.Service;

@Service
public class DefaultUserContextService implements UserContextService {

    private final UserRepository userRepository;

    public DefaultUserContextService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public User save(User user) {
        return userRepository.save(user);
    }

    @Override
    public User getRequired(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));
    }
}

