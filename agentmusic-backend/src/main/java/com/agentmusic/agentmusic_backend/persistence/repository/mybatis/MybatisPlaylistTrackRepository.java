package com.agentmusic.agentmusic_backend.persistence.repository.mybatis;

import com.agentmusic.agentmusic_backend.domain.PlaylistTrack;
import com.agentmusic.agentmusic_backend.persistence.mybatis.mapper.PlaylistTrackMybatisMapper;
import com.agentmusic.agentmusic_backend.persistence.mybatis.model.PlaylistTrackRecord;
import com.agentmusic.agentmusic_backend.persistence.repository.PlaylistTrackRepository;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "agentmusic.persistence.mode", havingValue = "mybatis")
public class MybatisPlaylistTrackRepository implements PlaylistTrackRepository {

    private final PlaylistTrackMybatisMapper playlistTrackMybatisMapper;

    public MybatisPlaylistTrackRepository(PlaylistTrackMybatisMapper playlistTrackMybatisMapper) {
        this.playlistTrackMybatisMapper = playlistTrackMybatisMapper;
    }

    @Override
    public void replaceTracks(String playlistId, List<PlaylistTrack> playlistTracks) {
        playlistTrackMybatisMapper.deleteByPlaylistId(playlistId);
        if (playlistTracks.isEmpty()) {
            return;
        }
        playlistTrackMybatisMapper.batchInsert(playlistTracks.stream()
                .map(this::toRecord)
                .toList());
    }

    @Override
    public List<PlaylistTrack> findByPlaylistId(String playlistId) {
        return playlistTrackMybatisMapper.selectByPlaylistId(playlistId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void deleteByPlaylistId(String playlistId) {
        playlistTrackMybatisMapper.deleteByPlaylistId(playlistId);
    }

    private PlaylistTrackRecord toRecord(PlaylistTrack playlistTrack) {
        return new PlaylistTrackRecord(
                playlistTrack.id(),
                playlistTrack.playlistId(),
                playlistTrack.trackId(),
                playlistTrack.position(),
                playlistTrack.addedAt()
        );
    }

    private PlaylistTrack toDomain(PlaylistTrackRecord record) {
        return new PlaylistTrack(
                record.playlistTrackId(),
                record.playlistId(),
                record.trackId(),
                record.position(),
                record.addedAt()
        );
    }
}
