package com.agentmusic.agentmusic_backend.persistence.repository.mybatis;

import com.agentmusic.agentmusic_backend.domain.Playlist;
import com.agentmusic.agentmusic_backend.persistence.mybatis.mapper.PlaylistMybatisMapper;
import com.agentmusic.agentmusic_backend.persistence.mybatis.mapper.PlaylistTrackMybatisMapper;
import com.agentmusic.agentmusic_backend.persistence.mybatis.model.PlaylistRecord;
import com.agentmusic.agentmusic_backend.persistence.repository.PlaylistRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "agentmusic.persistence.mode", havingValue = "mybatis")
public class MybatisPlaylistRepository implements PlaylistRepository {

    private final PlaylistMybatisMapper playlistMybatisMapper;
    private final PlaylistTrackMybatisMapper playlistTrackMybatisMapper;

    public MybatisPlaylistRepository(
            PlaylistMybatisMapper playlistMybatisMapper,
            PlaylistTrackMybatisMapper playlistTrackMybatisMapper
    ) {
        this.playlistMybatisMapper = playlistMybatisMapper;
        this.playlistTrackMybatisMapper = playlistTrackMybatisMapper;
    }

    @Override
    public Playlist save(Playlist playlist) {
        playlistMybatisMapper.upsert(toRecord(playlist));
        return playlist;
    }

    @Override
    public Optional<Playlist> findById(String playlistId) {
        return Optional.ofNullable(playlistMybatisMapper.selectById(playlistId))
                .map(this::toDomain);
    }

    @Override
    public List<Playlist> findRecentByUserId(String userId, int limit) {
        return playlistMybatisMapper.selectRecentByUserId(userId, limit).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public int nextVersionForUser(String userId) {
        return Optional.ofNullable(playlistMybatisMapper.selectMaxVersionByUserId(userId))
                .orElse(0) + 1;
    }

    @Override
    public void deleteOldestExcess(String userId, int keepLatest) {
        List<String> playlistIdsToDelete = playlistMybatisMapper.selectByUserIdOrdered(userId).stream()
                .skip(keepLatest)
                .map(PlaylistRecord::playlistId)
                .toList();
        if (playlistIdsToDelete.isEmpty()) {
            return;
        }
        playlistTrackMybatisMapper.deleteByPlaylistIds(playlistIdsToDelete);
        playlistMybatisMapper.deleteByIds(playlistIdsToDelete);
    }

    private PlaylistRecord toRecord(Playlist playlist) {
        return new PlaylistRecord(
                playlist.id(),
                playlist.userId(),
                playlist.name(),
                playlist.version(),
                playlist.createdAt()
        );
    }

    private Playlist toDomain(PlaylistRecord record) {
        return new Playlist(
                record.playlistId(),
                record.userId(),
                record.name(),
                record.version(),
                record.createdAt()
        );
    }
}
