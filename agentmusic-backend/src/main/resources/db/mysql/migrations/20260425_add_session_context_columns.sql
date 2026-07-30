ALTER TABLE sessions
    ADD COLUMN current_playlist_id CHAR(36) NULL AFTER current_track_id,
    ADD COLUMN current_track_index INT NULL AFTER current_playlist_id;
