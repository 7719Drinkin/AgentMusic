package com.agentmusic.agentmusic_backend.persistence.mybatis.mapper;

import com.agentmusic.agentmusic_backend.persistence.mybatis.model.UserRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMybatisMapper {

    @Insert("""
            INSERT INTO users (
                id,
                username,
                email,
                password_hash,
                preferences,
                created_at,
                updated_at
            ) VALUES (
                #{id},
                #{username},
                #{email},
                #{passwordHash},
                CAST(#{preferences} AS JSON),
                #{createdAt},
                #{updatedAt}
            )
            ON DUPLICATE KEY UPDATE
                username = VALUES(username),
                email = VALUES(email),
                password_hash = VALUES(password_hash),
                preferences = VALUES(preferences),
                updated_at = VALUES(updated_at)
            """)
    int upsert(UserRecord record);

    @Select("""
            SELECT id,
                   username,
                   email,
                   password_hash,
                   CAST(preferences AS CHAR) AS preferences,
                   created_at,
                   updated_at
            FROM users
            WHERE id = #{userId}
            LIMIT 1
            """)
    UserRecord selectById(@Param("userId") String userId);
}
