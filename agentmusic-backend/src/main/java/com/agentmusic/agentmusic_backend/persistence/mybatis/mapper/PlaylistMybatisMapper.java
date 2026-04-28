package com.agentmusic.agentmusic_backend.persistence.mybatis.mapper;

import com.agentmusic.agentmusic_backend.persistence.mybatis.model.PlaylistRecord;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PlaylistMybatisMapper {

    @Insert("""
            INSERT INTO playlists (
                id,
                user_id,
                name,
                version,
                created_at
            ) VALUES (
                #{playlistId},
                #{userId},
                #{name},
                #{version},
                #{createdAt}
            )
            ON DUPLICATE KEY UPDATE
                user_id = VALUES(user_id),
                name = VALUES(name),
                version = VALUES(version),
                created_at = VALUES(created_at)
            """)
    int upsert(PlaylistRecord record);

    @Select("""
            SELECT id AS playlist_id, user_id, name, version, created_at
            FROM playlists
            WHERE id = #{playlistId}
            LIMIT 1
            """)
    PlaylistRecord selectById(@Param("playlistId") String playlistId);

    @Select("""
            SELECT id AS playlist_id, user_id, name, version, created_at
            FROM playlists
            WHERE user_id = #{userId}
            ORDER BY version DESC, created_at DESC
            LIMIT #{limit}
            """)
    List<PlaylistRecord> selectRecentByUserId(@Param("userId") String userId, @Param("limit") int limit);

    @Select("""
            SELECT id AS playlist_id, user_id, name, version, created_at
            FROM playlists
            WHERE user_id = #{userId}
            ORDER BY version DESC, created_at DESC
            """)
    List<PlaylistRecord> selectByUserIdOrdered(@Param("userId") String userId);

    @Select("""
            SELECT MAX(version)
            FROM playlists
            WHERE user_id = #{userId}
            """)
    Integer selectMaxVersionByUserId(@Param("userId") String userId);

    @Delete("""
            <script>
            DELETE FROM playlists
            WHERE id IN
            <foreach collection="playlistIds" item="playlistId" open="(" separator="," close=")">
                #{playlistId}
            </foreach>
            </script>
            """)
    int deleteByIds(@Param("playlistIds") List<String> playlistIds);
}
