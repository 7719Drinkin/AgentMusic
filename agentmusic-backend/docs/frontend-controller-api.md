# Frontend Controller API

This document is the current frontend integration contract for backend controllers in `agentmusic-backend`.

Update rule:

- every controller endpoint change must update this document in the same change set

Base URL during local development:

```text
http://localhost:8080
```

## Agent API

### `POST /api/agent/chat`

Request body:

```json
{
  "userId": "demo-user",
  "message": "给我来点轻松的粤语歌，然后直接播放",
  "voiceInput": false
}
```

Response shape:

```json
{
  "reply": {
    "id": "message-id",
    "role": "AGENT",
    "message": "string",
    "metadata": {},
    "createdAt": "2026-03-25T20:00:00"
  },
  "session": {
    "sessionId": "session-id",
    "currentTrackId": "track-id",
    "currentPositionMs": 0,
    "isPlaying": true,
    "playbackMode": "SHUFFLE",
    "deviceId": "device-id",
    "lastUpdated": "2026-03-25T20:00:00"
  },
  "recommendedPlaylists": [
    {
      "id": "playlist-id",
      "name": "playlist name",
      "version": 1,
      "createdAt": "2026-03-25T20:00:00",
      "tracks": []
    }
  ]
}
```

### `GET /api/agent/history/{userId}?limit=20`

- returns `ChatMessageDto[]`

### `GET /api/agent/runtime-status`

Response shape:

```json
{
  "liveLlmEnabledConfigured": true,
  "openAiKeyPresent": true,
  "openAiModelId": "gpt-4o",
  "liveLlmAvailable": true
}
```

## Playlist API

### `GET /api/playlists/{userId}?limit=10`

- returns `PlaylistDto[]`

### `POST /api/playlists/{userId}`

Request body:

```json
{
  "name": "Late Night Mix",
  "tracks": [
    {
      "trackId": "track-id",
      "title": "Song A",
      "artistId": "artist-id",
      "albumName": "Album A",
      "albumId": "album-id",
      "durationMs": 180000,
      "previewUrl": null,
      "albumImageUrl": null
    }
  ]
}
```

- returns `PlaylistDto`

## Playback API

### `GET /api/playback/{userId}/session`

- returns `Optional<PlaybackSessionDto>`

### `PUT /api/playback/{userId}/session`

Request body:

```json
{
  "sessionId": "session-id",
  "currentTrackId": "track-id",
  "currentPositionMs": 12500,
  "isPlaying": true,
  "playbackMode": "SEQUENTIAL",
  "deviceId": "device-id"
}
```

- returns `PlaybackSessionDto`

### `POST /api/playback/{userId}/play`

Request body:

```json
{
  "trackId": "track-id",
  "deviceId": "device-id",
  "playbackMode": "SEQUENTIAL"
}
```

- returns `PlaybackSessionDto`

### `POST /api/playback/{userId}/pause`

Request body:

```json
{
  "deviceId": "device-id"
}
```

- returns `PlaybackSessionDto`

### `POST /api/playback/{userId}/next`

Request body:

```json
{
  "deviceId": "device-id"
}
```

- returns `PlaybackSessionDto`

### `POST /api/playback/{userId}/previous`

Request body:

```json
{
  "deviceId": "device-id"
}
```

- returns `PlaybackSessionDto`

### `POST /api/playback/{userId}/seek`

Request body:

```json
{
  "positionMs": 12500,
  "deviceId": "device-id"
}
```

- returns `PlaybackSessionDto`

### `POST /api/playback/{userId}/mode`

Request body:

```json
{
  "playbackMode": "SHUFFLE",
  "deviceId": "device-id"
}
```

- returns `PlaybackSessionDto`

### `POST /api/playback/{userId}/sync`

- returns `Optional<PlaybackSessionDto>`

## Music Query API

### `GET /api/music/tracks/{trackId}`

- returns `Optional<TrackDto>`

### `GET /api/music/artists/{artistId}`

- returns `Optional<ArtistDto>`

### `GET /api/music/search/tracks?q={query}&limit=10`

- returns `TrackDto[]`

## Spotify Bridge Auth API

### `GET /api/auth/spotify/login`

- redirects browser to Spotify authorization URL

### `GET /api/auth/spotify/callback?code=...&state=...`

- returns `SpotifyBridgeAuthStatusDto`

### `GET /api/auth/spotify/status`

Response shape:

```json
{
  "enabled": true,
  "authorized": true,
  "systemUserId": "bridge-user",
  "redirectUri": "http://127.0.0.1:8080/api/auth/spotify/callback",
  "scopes": [
    "user-read-playback-state",
    "user-modify-playback-state"
  ],
  "expiresAt": "2026-03-25T20:00:00Z"
}
```

## DTO Notes

### `TrackDto`

- `trackId`
- `title`
- `artistId`
- `albumName`
- `albumId`
- `durationMs`
- `previewUrl`
- `albumImageUrl`

### `ArtistDto`

- `artistId`
- `name`
- `bio`
- `imageUrl`
- `followers`

### `PlaybackSessionDto`

- `sessionId`
- `currentTrackId`
- `currentPositionMs`
- `isPlaying`
- `playbackMode`
- `deviceId`
- `lastUpdated`

### `ChatMessageDto`

- `id`
- `role`
- `message`
- `metadata`
- `createdAt`

### `PlaylistDto`

- `id`
- `name`
- `version`
- `createdAt`
- `tracks`
