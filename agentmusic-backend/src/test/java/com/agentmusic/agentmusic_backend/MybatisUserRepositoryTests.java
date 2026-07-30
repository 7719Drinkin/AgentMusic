package com.agentmusic.agentmusic_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentmusic.agentmusic_backend.domain.User;
import com.agentmusic.agentmusic_backend.domain.UserPreferences;
import com.agentmusic.agentmusic_backend.persistence.mybatis.mapper.UserMybatisMapper;
import com.agentmusic.agentmusic_backend.persistence.mybatis.model.UserRecord;
import com.agentmusic.agentmusic_backend.persistence.repository.mybatis.MybatisUserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MybatisUserRepositoryTests {

    @Mock
    private UserMybatisMapper userMybatisMapper;

    private MybatisUserRepository repository;

    @BeforeEach
    void setUp() {
        repository = new MybatisUserRepository(userMybatisMapper);
    }

    @Test
    void saveShouldPersistNormalizedPreferencesAndPreserveExistingCreatedAt() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 4, 28, 10, 0);
        when(userMybatisMapper.selectById("demo-user")).thenReturn(new UserRecord(
                "demo-user",
                "demo-user",
                "demo-user@local.test",
                null,
                "{\"favoriteGenres\":[],\"favoriteArtists\":[],\"excludedGenres\":[],\"preferredLanguage\":null,\"moodPreference\":null}",
                createdAt,
                createdAt
        ));

        User saved = repository.save(new User(
                "demo-user",
                "demo-user",
                "demo-user@local.test",
                null,
                new UserPreferences(
                        List.of("Cantopop"),
                        List.of("Eason Chan"),
                        null,
                        "zh-CN",
                        "rainy"
                ),
                null,
                null
        ));

        ArgumentCaptor<UserRecord> captor = ArgumentCaptor.forClass(UserRecord.class);
        verify(userMybatisMapper).upsert(captor.capture());
        UserRecord record = captor.getValue();

        assertThat(saved.createdAt()).isEqualTo(createdAt);
        assertThat(record.createdAt()).isEqualTo(createdAt);
        assertThat(record.preferences()).contains("\"favoriteGenres\":[\"Cantopop\"]");
        assertThat(record.preferences()).contains("\"favoriteArtists\":[\"Eason Chan\"]");
        assertThat(record.preferences()).contains("\"excludedGenres\":[]");
    }

    @Test
    void findByIdShouldDeserializeStoredPreferences() {
        when(userMybatisMapper.selectById("demo-user")).thenReturn(new UserRecord(
                "demo-user",
                "demo-user",
                "demo-user@local.test",
                null,
                """
                {"favoriteGenres":["Cantopop"],"favoriteArtists":["Eason Chan"],"excludedGenres":["Rap"],"preferredLanguage":"zh-HK","moodPreference":"chill"}
                """.trim(),
                LocalDateTime.of(2026, 4, 28, 10, 0),
                LocalDateTime.of(2026, 4, 28, 10, 5)
        ));

        Optional<User> result = repository.findById("demo-user");

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().preferences().favoriteGenres()).containsExactly("Cantopop");
        assertThat(result.orElseThrow().preferences().favoriteArtists()).containsExactly("Eason Chan");
        assertThat(result.orElseThrow().preferences().excludedGenres()).containsExactly("Rap");
        assertThat(result.orElseThrow().preferences().preferredLanguage()).isEqualTo("zh-HK");
        assertThat(result.orElseThrow().preferences().moodPreference()).isEqualTo("chill");
    }
}
