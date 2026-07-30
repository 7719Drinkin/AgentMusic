package com.agentmusic.agentmusic_backend.persistence.mybatis.mapper;

import com.agentmusic.agentmusic_backend.persistence.mybatis.model.ArtistRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ArtistMybatisMapper {

    @Insert("""
            INSERT INTO artists (
                artist_id,
                name,
                bio,
                image_url,
                followers,
                updated_at
            ) VALUES (
                #{artistId},
                #{name},
                #{bio},
                #{imageUrl},
                #{followers},
                #{updatedAt}
            )
            ON DUPLICATE KEY UPDATE
                name = VALUES(name),
                bio = VALUES(bio),
                image_url = VALUES(image_url),
                followers = VALUES(followers),
                updated_at = VALUES(updated_at)
            """)
    int upsert(ArtistRecord record);

    @Select("""
            SELECT artist_id, name, bio, image_url, followers, updated_at
            FROM artists
            WHERE artist_id = #{artistId}
            LIMIT 1
            """)
    ArtistRecord selectById(@Param("artistId") String artistId);
}
