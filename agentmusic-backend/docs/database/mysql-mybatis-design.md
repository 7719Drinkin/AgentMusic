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
- upsert session by `user_id`

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

## Remaining implementation work

- add mapper XML or annotations for real queries
- add MyBatis-backed repository implementations
- migrate service wiring from memory implementations to MyBatis implementations
- add Redis-backed session cache path
