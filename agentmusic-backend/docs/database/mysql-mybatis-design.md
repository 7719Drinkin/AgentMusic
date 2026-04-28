# MySQL + MyBatis Persistence Design

## Goal

Introduce durable persistence without breaking the current application boundary.

Implementation constraints:

- keep existing `Repository` interfaces stable
- replace memory-backed implementations incrementally
- use MyBatis for MySQL access
- use Redis only for hot state and cache

## Current package layout

- `com.agentmusic.agentmusic_backend.persistence.repository`
- `com.agentmusic.agentmusic_backend.persistence.repository.memory`
- `com.agentmusic.agentmusic_backend.persistence.repository.file`
- `com.agentmusic.agentmusic_backend.persistence.mybatis.config`
- `com.agentmusic.agentmusic_backend.persistence.mybatis.mapper`
- `com.agentmusic.agentmusic_backend.persistence.mybatis.model`
- `com.agentmusic.agentmusic_backend.persistence.redis`

This layout is intentional:

- `repository` remains the domain-facing abstraction
- `mybatis` contains SQL-facing records and mappers
- `redis` contains cache and hot-state infrastructure

## Recommended replacement order

### Phase 1

- `PlaylistRepository`
- `PlaylistTrackRepository`

Reason:

- recommendation history and playlist detail pages already depend on these paths
- replacing them first removes the highest-value in-memory bottleneck

### Phase 2

- `TrackRepository`
- `ArtistRepository`

Reason:

- track and artist metadata are shared by playlist detail, current playing, and queue UI

### Phase 3

- `SessionRepository`

Reason:

- playback session is hot state and should become a Redis + MySQL hybrid path

### Phase 4

- `ChatMessageRepository`
- `UserRepository`

Reason:

- these are important, but they do not currently block the playback and recommendation closed loop

## MyBatis responsibilities

MyBatis should own:

- SQL mapping
- row-to-record mapping
- query pagination and filtering
- write-side batch operations where playlist and track relations are updated together

MyBatis should not own:

- playback decision logic
- planner logic
- DTO mapping

## Proposed table-to-mapper alignment

### `playlists`

- mapper: `PlaylistMybatisMapper`
- model: `PlaylistRecord`

Operations to add first:

- find latest playlists by `user_id`
- find playlist by `id`
- insert playlist

### `playlist_tracks`

- mapper: new `PlaylistTrackMybatisMapper`
- model: new `PlaylistTrackRecord`

Operations to add first:

- find ordered tracks by `playlist_id`
- insert track rows in batch
- delete all by `playlist_id`

### `sessions`

- mapper: `PlaybackSessionMybatisMapper`
- model: `PlaybackSessionRecord`

Operations to add first:

- find active session by `user_id`
- upsert session snapshot by `id`

## Redis integration boundary

Redis should be introduced around `SessionRepository` first.

Suggested pattern:

1. read Redis hot state
2. fallback to MySQL
3. write-through or write-behind to MySQL

This keeps playback UI responsive while preserving recovery after restart.

## SQL / mapping style guidance

- prefer explicit column lists over `select *`
- keep mapper XML grouped by aggregate, not by endpoint
- use `mapUnderscoreToCamelCase=true`
- keep record models persistence-only; do not reuse web DTOs in MyBatis

## Done in this refactor

- MyBatis starter added
- MySQL driver added
- Redis starter added
- `MybatisPersistenceConfig` added
- initial MyBatis mapper/model placeholders added
- application properties now expose MySQL and Redis configuration

## Done in phase 1 implementation

- `PlaylistMybatisMapper` now owns playlist read/write SQL
- `PlaylistTrackMybatisMapper` now owns ordered track lookup, batch insert, and delete-by-playlist SQL
- `MybatisPlaylistRepository` added
- `MybatisPlaylistTrackRepository` added
- in-memory playlist repositories are now conditional and remain the default fallback
- switch property added:
  - `agentmusic.persistence.mode=mybatis`

## Done in phase 2 implementation

- `TrackMybatisMapper` added
- `ArtistMybatisMapper` added
- `TrackRecord` and `ArtistRecord` added
- `MybatisTrackRepository` added
- `MybatisArtistRepository` added
- in-memory track and artist repositories are now conditional and remain the default fallback

## Done in phase 3 implementation

- `PlaybackSessionMybatisMapper` now owns active-session lookup and session upsert SQL
- `RedisMybatisSessionRepository` added as the `SessionRepository` implementation for `agentmusic.persistence.mode=mybatis`
- read path:
  - Redis first
  - fallback to MySQL
  - rehydrate Redis on fallback hit
- write path:
  - MySQL first
  - Redis hot cache second
- `sessions` table now requires:
  - `current_playlist_id`
  - `current_track_index`
- migration added:
  - `src/main/resources/db/mysql/migrations/20260425_add_session_context_columns.sql`

## Done in phase 4 implementation

- `ChatMessageMybatisMapper` added
- `ChatMessageRecord` added
- `MybatisChatMessageRepository` added
- in-memory chat repository is now conditional and remains the default fallback
- current read/write behavior:
  - write new chat messages directly to MySQL
  - load recent messages ordered by `created_at DESC`
  - trim older messages by user after append

## Done in phase 5 implementation

- `UserMybatisMapper` added
- `UserRecord` added
- `MybatisUserRepository` added
- in-memory user repository is now conditional and remains the default fallback
- user preferences are persisted as JSON in the `users.preferences` column

## Startup bootstrap

When `agentmusic.persistence.mode=mybatis` is enabled:

1. `db/mysql/schema.sql` is executed at startup
2. `schema_migrations` is created if missing
3. `db/mysql/migrations/*.sql` is scanned and applied in filename order
4. required tables and columns are validated before the app continues

Current required validation includes:

- `users`
- `playlists`
- `playlist_tracks`
- `tracks`
- `artists`
- `chat_messages`
- `sessions`
- `schema_migrations`
- `sessions.current_playlist_id`
- `sessions.current_track_index`
- `users.preferences`

## E2E validation status

Current browser-level validation is available through:

- `agentmusic-frontend/scripts/e2e-persistence.js`

Validated path:

1. send a recommendation request from the chat page
2. verify a new recommendation playlist is returned by the backend
3. verify the left sidebar refreshes
4. open the newest playlist page
5. click a track from the playlist page
6. verify playback session reflects the opened playlist and selected track
7. verify MySQL row growth in:
   - `playlists`
   - `playlist_tracks`
   - `tracks`
   - `artists`
   - `chat_messages`
   - `sessions`

Operational prerequisite:

- the bridge account must have an active Spotify device, otherwise playback APIs correctly return `409`

## Remaining implementation work

- add more explicit user-facing diagnostics when startup migration fails
- add restart-recovery regression coverage in mybatis mode
