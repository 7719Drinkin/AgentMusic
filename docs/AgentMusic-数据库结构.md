# AgentMusic 数据库结构设计

版本：D2.0
日期：2026-04-25

## 1. 持久化总体方案

AgentMusic 当前的持久化采用双层方案：

- MySQL：长期业务数据
- Redis：热状态、缓存、短期上下文

接入方式：

- MySQL 通过 MyBatis 实现
- Redis 通过 Spring Data Redis 实现

## 2. MySQL 负责的数据

### 2.1 users

用途：用户基础信息与偏好。

关键字段：
- `id`
- `username`
- `email`
- `preferences_json`
- `created_at`
- `updated_at`

### 2.2 playlists

用途：推荐歌单主表。

关键字段：
- `id`
- `user_id`
- `name`
- `version`
- `created_at`

### 2.3 playlist_tracks

用途：歌单与曲目关系表。

关键字段：
- `id`
- `playlist_id`
- `track_id`
- `position`
- `added_at`

说明：
- `position` 决定歌单内顺序
- `added_at` 用于歌单页“添加时间”列

### 2.4 tracks

用途：曲目元数据缓存表。

关键字段：
- `track_id`
- `title`
- `artist_id`
- `album_id`
- `album_name`
- `duration_ms`
- `preview_url`
- `album_image_url`
- `updated_at`

### 2.5 artists

用途：艺人元数据缓存表。

关键字段：
- `artist_id`
- `name`
- `bio`
- `image_url`
- `followers`
- `updated_at`

### 2.6 chat_messages

用途：聊天历史。

关键字段：
- `id`
- `user_id`
- `role`
- `message`
- `metadata_json`
- `created_at`

### 2.7 sessions

用途：播放会话的持久化快照。

关键字段：
- `id`
- `user_id`
- `current_track_id`
- `current_playlist_id`
- `current_track_index`
- `current_position_ms`
- `is_playing`
- `playback_mode`
- `device_id`
- `last_updated`

## 3. Redis 负责的数据

### 3.1 播放会话热状态

Key：`user:session:{userId}`
类型：`HASH`

字段：
- `sessionId`
- `currentTrackId`
- `currentPlaylistId`
- `currentTrackIndex`
- `currentPositionMs`
- `isPlaying`
- `playbackMode`
- `deviceId`
- `lastUpdated`

### 3.2 最近歌单缓存

Key：`user:playlists:{userId}`
类型：`LIST`

用途：左侧栏最近推荐歌单。

### 3.3 歌单曲目缓存

Key：`playlist:tracks:{playlistId}`
类型：`LIST`

用途：缓存有序 `trackId` 列表。

### 3.4 曲目元数据缓存

Key：`track:info:{trackId}`
类型：`HASH`

用途：缓存热门曲目信息。

### 3.5 艺人元数据缓存

Key：`artist:bio:{artistId}`
类型：`HASH`

### 3.6 聊天短期上下文缓存

Key：`chat:history:{userId}`
类型：`LIST`

## 4. MyBatis 设计要求

当前后端已经预留：

- `persistence.mybatis.config`
- `persistence.mybatis.mapper`
- `persistence.mybatis.model`

后续实现策略：

1. 保持现有 `Repository` 接口不变
2. 在 `persistence.mybatis` 下增加对应 Mapper 和持久化实现
3. 按模块替换当前内存仓储
4. 优先替换：
   - `PlaylistRepository`
   - `PlaylistTrackRepository`
   - `TrackRepository`
   - `ArtistRepository`
   - `ChatMessageRepository`
   - `SessionRepository`

## 5. 推荐实施顺序

1. 落 `playlists` 与 `playlist_tracks`
2. 落 `tracks` 与 `artists`
3. 落 `sessions`
4. 落 `chat_messages`
5. 最后把 Redis 缓存与 MySQL 双写策略接入

## 6. 说明

当前内存仓储仍是运行时主实现，但只作为过渡层。最终目标是：

- MySQL 负责长期数据
- Redis 负责热状态
- MyBatis 负责 MySQL 持久化访问
