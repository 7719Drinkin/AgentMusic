package com.agentmusic.agentmusic_backend.persistence.repository.mybatis;

import com.agentmusic.agentmusic_backend.domain.Track;
import com.agentmusic.agentmusic_backend.persistence.mybatis.mapper.TrackMybatisMapper;
import com.agentmusic.agentmusic_backend.persistence.mybatis.model.TrackRecord;
import com.agentmusic.agentmusic_backend.persistence.repository.TrackRepository;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "agentmusic.persistence.mode", havingValue = "mybatis")
public class MybatisTrackRepository implements TrackRepository {

    private final TrackMybatisMapper trackMybatisMapper;

    public MybatisTrackRepository(TrackMybatisMapper trackMybatisMapper) {
        this.trackMybatisMapper = trackMybatisMapper;
    }

    @Override
    public Track save(Track track) {
        trackMybatisMapper.upsert(toRecord(track));
        return track;
    }

    @Override
    public Optional<Track> findById(String trackId) {
        return Optional.ofNullable(trackMybatisMapper.selectById(trackId))
                .map(this::toDomain);
    }

    private TrackRecord toRecord(Track track) {
        return new TrackRecord(
                track.trackId(),
                track.title(),
                track.artistId(),
                track.albumName(),
                track.albumId(),
                track.durationMs(),
                track.previewUrl(),
                track.albumImageUrl(),
                track.updatedAt(),
                track.lastAccessedAt()
        );
    }

    private Track toDomain(TrackRecord record) {
        return new Track(
                record.trackId(),
                record.title(),
                record.artistId(),
                record.albumName(),
                record.albumId(),
                record.durationMs(),
                record.previewUrl(),
                record.albumImageUrl(),
                record.updatedAt(),
                record.lastAccessedAt()
        );
    }
}
