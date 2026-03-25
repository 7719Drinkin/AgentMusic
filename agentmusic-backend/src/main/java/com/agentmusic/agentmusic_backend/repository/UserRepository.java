package com.agentmusic.agentmusic_backend.repository;

import com.agentmusic.agentmusic_backend.domain.User;
import java.util.Optional;

public interface UserRepository {

    User save(User user);

    Optional<User> findById(String userId);
}

