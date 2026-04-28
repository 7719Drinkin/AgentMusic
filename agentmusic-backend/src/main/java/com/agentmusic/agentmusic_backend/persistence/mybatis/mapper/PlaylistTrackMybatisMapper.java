package com.agentmusic.agentmusic_backend.persistence.mybatis.mapper;

import com.agentmusic.agentmusic_backend.persistence.mybatis.model.PlaylistTrackRecord;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PlaylistTrackMybatisMapper {

    @Select("""
            SELECT id AS playlist_track_id, playlist_id, track_id, position, added_at
            FROM playlist_tracks
            WHERE playlist_id = #{playlistId}
            ORDER BY position ASC
            """)
    List<PlaylistTrackRecord> selectByPlaylistId(@Param("playlistId") String playlistId);

    @Delete("""
            DELETE FROM playlist_tracks
            WHERE playlist_id = #{playlistId}
            """)
    int deleteByPlaylistId(@Param("playlistId") String playlistId);

    @Delete("""
            <script>
            DELETE FROM playlist_tracks
            WHERE playlist_id IN
            <foreach collection="playlistIds" item="playlistId" open="(" separator="," close=")">
                #{playlistId}
            </foreach>
            </script>
            """)
    int deleteByPlaylistIds(@Param("playlistIds") List<String> playlistIds);

    @Insert("""
            <script>
            INSERT INTO playlist_tracks (
                id,
                playlist_id,
                track_id,
                position,
                added_at
            ) VALUES
            <foreach collection="records" item="record" separator=",">
                (
                    #{record.playlistTrackId},
                    #{record.playlistId},
                    #{record.trackId},
                    #{record.position},
                    #{record.addedAt}
                )
            </foreach>
            </script>
            """)
    int batchInsert(@Param("records") List<PlaylistTrackRecord> records);
}
