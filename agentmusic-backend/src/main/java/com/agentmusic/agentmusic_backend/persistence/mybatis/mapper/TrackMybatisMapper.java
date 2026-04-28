package com.agentmusic.agentmusic_backend.persistence.mybatis.mapper;

import com.agentmusic.agentmusic_backend.persistence.mybatis.model.TrackRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TrackMybatisMapper {

    @Insert("""
            INSERT INTO tracks (
                track_id,
                title,
                artist_id,
                album_name,
                album_id,
                duration_ms,
                preview_url,
                album_image_url,
                updated_at,
                last_accessed_at
            ) VALUES (
                #{trackId},
                #{title},
                #{artistId},
                #{albumName},
                #{albumId},
                #{durationMs},
                #{previewUrl},
                #{albumImageUrl},
                #{updatedAt},
                #{lastAccessedAt}
            )
            ON DUPLICATE KEY UPDATE
                title = VALUES(title),
                artist_id = VALUES(artist_id),
                album_name = VALUES(album_name),
                album_id = VALUES(album_id),
                duration_ms = VALUES(duration_ms),
                preview_url = VALUES(preview_url),
                album_image_url = VALUES(album_image_url),
                updated_at = VALUES(updated_at),
                last_accessed_at = VALUES(last_accessed_at)
            """)
    int upsert(TrackRecord record);

    @Select("""
            SELECT track_id, title, artist_id, album_name, album_id, duration_ms,
                   preview_url, album_image_url, updated_at, last_accessed_at
            FROM tracks
            WHERE track_id = #{trackId}
            LIMIT 1
            """)
    TrackRecord selectById(@Param("trackId") String trackId);
}
