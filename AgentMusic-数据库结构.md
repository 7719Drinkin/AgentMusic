AgentMusic 数据库设计文档（最终版）
数据库类型

主数据库：MySQL 8.0+（关系型持久化存储）
辅助缓存：Redis（高并发、实时状态、短期缓存）

设计原则

结构清晰、查询高效、支持高并发
实时数据走 Redis，持久化数据走 MySQL
JSON 字段用于灵活扩展，同时保持核心字段固定化
支持歌单管理（排序、添加、删除）和历史歌单切换


1. Users（用户表）
作用：存储用户基本信息和长期偏好设置，是所有表的核心关联表。
表结构：
```sql
SQLCREATE TABLE users (
    id CHAR(36) PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255),
    preferences JSON NOT NULL DEFAULT '{}',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```
preferences JSON 示例（字段固定化）：
```JSON
JSON{
  "favorite_genres": ["香港流行", "Cantopop"],
  "favorite_artists": ["陈奕迅", "Taylor Swift"],     // 已补充喜爱歌手
  "excluded_genres": ["说唱"],
  "preferred_language": "zh-HK",
  "mood_preference": "chill"
}
```
存取方式：登录时读取，Agent 生成推荐时作为上下文传入 Planner。
维护时间：长期保存。
数量限制：无限制。
与 Redis 对接：用户会话信息缓存在 Redis（key: user:session:{id}）。

2. Playlists（历史推荐歌单表）
作用：存储 Agent 生成的历史歌单，支持用户切换回以前版本。
表结构：
```sql
SQLCREATE TABLE playlists (
    id CHAR(36) PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    name VARCHAR(100) NOT NULL,
    version INT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);
```
存取方式：Agent 生成新歌单时插入，左侧栏读取时按 version 降序 LIMIT 10。
维护时间：长期保存。
数量限制：每个用户最多保留 10 个历史歌单（超过自动删除最旧的）。
与 Redis 对接：缓存最近 10 个歌单（key: user:playlists:{user_id}，List 类型，使用 LTRIM 保持上限）。

3. Playlist_Tracks（歌单-歌曲关联表） ★ 新增核心表
作用：实现歌单与歌曲的多对多关系，支持歌曲排序、添加、删除等管理操作。
表结构：
```sql
SQLCREATE TABLE playlist_tracks (
    id CHAR(36) PRIMARY KEY,
    playlist_id CHAR(36) NOT NULL,
    track_id VARCHAR(50) NOT NULL,
    position INT NOT NULL,                    -- 用于歌曲排序和拖拽
    added_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (playlist_id) REFERENCES playlists(id),
    FOREIGN KEY (track_id) REFERENCES tracks(track_id),
    
    UNIQUE KEY uk_playlist_track (playlist_id, track_id)
);
```
存取方式：查询歌单歌曲时通过 JOIN 获取，添加/删除/排序时直接操作此表。
维护时间：随歌单长期保存。
数量限制：无限制（由歌单容量间接控制）。
与 Redis 对接：热门歌单的歌曲列表可缓存（key: playlist:tracks:{playlist_id}，TTL 1 小时）。

4. Tracks（轨道缓存表）
作用：缓存歌曲元数据，减少重复调用 Spotify API。
表结构：
```sql
SQLCREATE TABLE tracks (
    track_id VARCHAR(50) PRIMARY KEY,
    title VARCHAR(200),
    artist_id VARCHAR(50),
    album_name VARCHAR(200),
    album_id VARCHAR(50),
    duration_ms INT,
    preview_url TEXT,
    album_image_url TEXT,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
```
存取方式：播放或显示时先查此表，没有再调用 Spotify API 并缓存。
维护时间：每 3 天自动清理一次（只保留最近 3 天使用过的轨道）。
数量限制：按需增长（通过定时任务控制）。
与 Redis 对接：

热门轨道缓存（key: track:info:{track_id}，Hash 类型）
TTL = 24 小时
逻辑：先查 Redis → 未命中再查 MySQL → 命中后写回 Redis 并设置 TTL


5. Artists（艺术家表）
作用：缓存歌手信息，特别是 bio 和热门歌曲。
表结构：
```sql
SQLCREATE TABLE artists (
    artist_id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(100),
    bio TEXT,
    image_url TEXT,
    followers INT,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
```
存取方式：右侧栏显示歌手信息时使用。
维护时间：每 30 天清理一次。
数量限制：不限制。
与 Redis 对接：缓存歌手 bio（key: artist:bio:{artist_id}，TTL 7 天）。

6. ChatMessages（Agent 聊天记录表）
作用：存储用户与 Agent 的完整对话历史，为 Memory 和长期学习提供数据。
表结构：
```sql
SQLCREATE TABLE chat_messages (
    id CHAR(36) PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    message TEXT NOT NULL,
    is_user BOOLEAN NOT NULL,
    metadata JSON,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);
```
存取方式：Agent Planner 生成回复前读取最近 20 条作为上下文。
维护时间：每个用户保留最近 200 条。
数量限制：每个用户最多 200 条。
与 Redis 对接：短期记忆缓存（key: chat:history:{user_id}，List 类型，保留最近 50 条）。

7. Sessions（会话表）
作用：存储用户实时播放状态，支持多设备同步和 WebSocket 推送。
表结构：
```sql
SQLCREATE TABLE sessions (
    id CHAR(36) PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    current_track_id VARCHAR(50),
    current_position_ms INT,
    is_playing BOOLEAN DEFAULT FALSE,
    playback_mode VARCHAR(20),
    device_id VARCHAR(100),
    last_updated DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);
```
存取方式：WebSocket 连接时读取/更新，Agent 操作前必须先读当前状态。
维护时间：会话过期自动清理（24 小时）。
数量限制：每个用户最多 5 个活跃设备。
与 Redis 对接：实时数据主存 Redis（key: session:user:{user_id}，Hash 类型，TTL 1 小时）。

文档说明：

Playlists 表已移除大 JSON 字段，改用 playlist_tracks 关联表（性能更好，支持管理）
tracks JSON 中已包含 artist_id 和 album_id，满足前端跳转需求
用户偏好 JSON 已补充 favorite_artists
Tracks 表明确 3 天清理 + Redis TTL 24 小时辅助机制