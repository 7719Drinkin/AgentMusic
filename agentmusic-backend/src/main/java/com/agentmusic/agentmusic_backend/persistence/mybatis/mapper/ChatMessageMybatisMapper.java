package com.agentmusic.agentmusic_backend.persistence.mybatis.mapper;

import com.agentmusic.agentmusic_backend.persistence.mybatis.model.ChatMessageRecord;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ChatMessageMybatisMapper {

    @Insert("""
            INSERT INTO chat_messages (
                id,
                user_id,
                message,
                role,
                metadata,
                created_at
            ) VALUES (
                #{id},
                #{userId},
                #{message},
                #{role},
                #{metadata},
                #{createdAt}
            )
            """)
    int insert(ChatMessageRecord record);

    @Select("""
            SELECT id, user_id, message, role, metadata, created_at
            FROM chat_messages
            WHERE user_id = #{userId}
            ORDER BY created_at DESC
            LIMIT #{limit}
            """)
    List<ChatMessageRecord> selectRecentByUserId(@Param("userId") String userId, @Param("limit") int limit);

    @Delete("""
            DELETE FROM chat_messages
            WHERE id IN (
                SELECT stale.id
                FROM (
                    SELECT id
                    FROM chat_messages
                    WHERE user_id = #{userId}
                    ORDER BY created_at DESC
                    LIMIT 18446744073709551615 OFFSET #{keepLatest}
                ) AS stale
            )
            """)
    int deleteOlderThanLatest(@Param("userId") String userId, @Param("keepLatest") int keepLatest);
}
