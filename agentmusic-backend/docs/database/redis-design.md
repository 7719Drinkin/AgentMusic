# Redis Design Baseline

## Role of Redis

Redis is the real-time and hot-cache layer for AgentMusic.

- Real-time playback state is stored in Redis first.
- Short-term chat memory is stored in Redis first.
- Hot metadata and recent playlist history are cached in Redis.
- MySQL remains the durable source for historical data and recovery.

## Key Naming Convention

All keys use lowercase prefixes and colon-separated segments:

```text
{namespace}:{resource}:{identifier}
```

Examples:

```text
user:session:{userId}
user:playlists:{userId}
playlist:tracks:{playlistId}
track:info:{trackId}
artist:bio:{artistId}
chat:history:{userId}
```

## Key Catalog

### `user:session:{userId}`

- Type: `HASH`
- Purpose: current playback state for one user
- TTL: `1 hour`
- Fields:
  - `sessionId`
  - `currentTrackId`
  - `currentPositionMs`
  - `isPlaying`
  - `playbackMode`
  - `deviceId`
  - `lastUpdated`

This is the primary real-time state read by WebSocket handlers and agent actions.
MySQL `sessions` is treated as the durable fallback snapshot.

### `user:playlists:{userId}`

- Type: `LIST`
- Purpose: recent recommendation playlist ids for the left sidebar
- TTL: no fixed TTL, maintained by application logic
- Size limit: keep latest `10` items with `LTRIM`

### `playlist:tracks:{playlistId}`

- Type: `LIST`
- Purpose: hot cache of ordered track ids for a playlist
- TTL: `1 hour`
- Value shape: ordered `trackId` list

### `track:info:{trackId}`

- Type: `HASH`
- Purpose: hot cache of track metadata
- TTL: `24 hours`
- Fields:
  - `trackId`
  - `title`
  - `artistId`
  - `albumId`
  - `albumName`
  - `durationMs`
  - `previewUrl`
  - `albumImageUrl`
  - `updatedAt`

Lookup order:

1. Redis
2. MySQL `tracks`
3. Spotify API

### `artist:bio:{artistId}`

- Type: `HASH`
- Purpose: hot cache of artist summary data
- TTL: `7 days`
- Fields:
  - `artistId`
  - `name`
  - `bio`
  - `imageUrl`
  - `followers`
  - `updatedAt`

### `chat:history:{userId}`

- Type: `LIST`
- Purpose: short-term conversation memory for planner context
- TTL: no fixed TTL
- Size limit: latest `50` serialized messages

MySQL still stores the durable history with a per-user retention cap of `200`.

## Write Strategy

### Playback session

1. Write Redis `user:session:{userId}`
2. Optionally publish WebSocket event
3. Persist or refresh MySQL `sessions` asynchronously

### Chat message

1. Push message to `chat:history:{userId}`
2. Trim to `50`
3. Persist to MySQL `chat_messages`

### Track metadata

1. Read Redis
2. Fallback to MySQL
3. Fallback to Spotify API
4. Write back to MySQL and Redis

### Playlist history

1. Insert MySQL `playlists` and `playlist_tracks`
2. Push playlist id to Redis `user:playlists:{userId}`
3. Trim to `10`

## Expiration and Cleanup

- Redis TTL handles hot-key expiration for session, track, artist, and playlist-track caches.
- MySQL cleanup jobs should remove:
  - expired `sessions`
  - cold `tracks` where `last_accessed_at` is older than `3 days`
  - old `chat_messages` beyond the latest `200` per user
  - old `playlists` beyond the latest `10` per user
