package com.agentmusic.agentmusic_backend.persistence.mybatis.mapper;

import com.agentmusic.agentmusic_backend.persistence.mybatis.model.PlaybackSessionRecord;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PlaybackSessionMybatisMapper {

    @Insert("""
            INSERT INTO sessions (
                id,
                user_id,
                current_track_id,
                current_playlist_id,
                current_track_index,
                current_position_ms,
                is_playing,
                playback_mode,
                device_id,
                last_updated,
                expires_at
            ) VALUES (
                #{sessionId},
                #{userId},
                #{currentTrackId},
                #{currentPlaylistId},
                #{currentTrackIndex},
                #{currentPositionMs},
                #{isPlaying},
                #{playbackMode},
                #{deviceId},
                #{lastUpdated},
                #{expiresAt}
            )
            ON DUPLICATE KEY UPDATE
                user_id = VALUES(user_id),
                current_track_id = VALUES(current_track_id),
                current_playlist_id = VALUES(current_playlist_id),
                current_track_index = VALUES(current_track_index),
                current_position_ms = VALUES(current_position_ms),
                is_playing = VALUES(is_playing),
                playback_mode = VALUES(playback_mode),
                device_id = VALUES(device_id),
                last_updated = VALUES(last_updated),
                expires_at = VALUES(expires_at)
            """)
    int upsert(PlaybackSessionRecord record);

    @Select("""
            SELECT id AS session_id,
                   user_id,
                   current_track_id,
                   current_playlist_id,
                   current_track_index,
                   current_position_ms,
                   is_playing,
                   playback_mode,
                   device_id,
                   last_updated,
                   expires_at
            FROM sessions
            WHERE user_id = #{userId}
              AND expires_at > #{now}
            ORDER BY last_updated DESC
            LIMIT 1
            """)
    PlaybackSessionRecord selectLatestActiveByUserId(@Param("userId") String userId, @Param("now") LocalDateTime now);
}
