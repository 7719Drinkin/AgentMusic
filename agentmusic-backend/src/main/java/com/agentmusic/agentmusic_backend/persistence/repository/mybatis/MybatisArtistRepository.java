package com.agentmusic.agentmusic_backend.persistence.repository.mybatis;

import com.agentmusic.agentmusic_backend.domain.Artist;
import com.agentmusic.agentmusic_backend.persistence.mybatis.mapper.ArtistMybatisMapper;
import com.agentmusic.agentmusic_backend.persistence.mybatis.model.ArtistRecord;
import com.agentmusic.agentmusic_backend.persistence.repository.ArtistRepository;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "agentmusic.persistence.mode", havingValue = "mybatis")
public class MybatisArtistRepository implements ArtistRepository {

    private final ArtistMybatisMapper artistMybatisMapper;

    public MybatisArtistRepository(ArtistMybatisMapper artistMybatisMapper) {
        this.artistMybatisMapper = artistMybatisMapper;
    }

    @Override
    public Artist save(Artist artist) {
        artistMybatisMapper.upsert(toRecord(artist));
        return artist;
    }

    @Override
    public Optional<Artist> findById(String artistId) {
        return Optional.ofNullable(artistMybatisMapper.selectById(artistId))
                .map(this::toDomain);
    }

    private ArtistRecord toRecord(Artist artist) {
        return new ArtistRecord(
                artist.artistId(),
                artist.name(),
                artist.bio(),
                artist.imageUrl(),
                artist.followers(),
                artist.updatedAt()
        );
    }

    private Artist toDomain(ArtistRecord record) {
        return new Artist(
                record.artistId(),
                record.name(),
                record.bio(),
                record.imageUrl(),
                record.followers(),
                record.updatedAt()
        );
    }
}
