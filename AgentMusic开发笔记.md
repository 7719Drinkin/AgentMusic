# AgentMusic 开发笔记

版本：M2.6
更新日期：2026-04-28

## 1. 当前基线

当前版本的主目标已经从“把前后端接起来”转为“把真实播放闭环做稳定，并为持久化落地准备结构”。

当前主闭环已经具备：

1. 聊天页发送推荐请求。
2. 后端生成推荐歌单并写入播放会话。
3. 左侧栏展示推荐歌单历史。
4. 点击推荐歌单进入真实歌单页。
5. 底部播放器与右侧当前播放栏显示真实播放上下文。
6. 通过 Spotify bridge 账号控制真实 Spotify 设备播放。

## 2. 当前后端结构

本轮已完成一次受控包重构，目标是把 Web 层、Spotify 集成层、持久化层拆开，减少横向技术包混放。

### 2.1 结构说明

后端当前主要包结构如下：

- `config`
  - 全局配置与属性绑定。
- `domain`
  - 领域模型，如 `Playlist`、`Track`、`PlaybackSession`。
- `planner`
  - Agent 意图识别、规划与执行骨架。
- `service`
  - 领域服务接口与应用服务接口。
- `web.controller`
  - REST Controller。
- `web.dto`
  - Web 层 DTO。
- `web.mapper`
  - 领域对象到 DTO 的映射。
- `web.exception`
  - API 错误响应与异常处理。
- `integration.spotify`
  - Spotify Web API 客户端、token、设备、播放状态等集成代码。
- `persistence.repository`
  - Repository 抽象。
- `persistence.repository.memory`
  - 内存仓储实现。
- `persistence.repository.file`
  - 文件型持久化实现（当前主要用于 Spotify bridge token）。
- `persistence.redis`
  - Redis key 与 Redis 配置入口。
- `persistence.mybatis`
  - MyBatis 持久化入口、Mapper 占位与记录模型。

### 2.2 重构原则

本次重构没有继续把所有业务再细分成更多目录，而是先解决最混乱的三层：

- Web 入口层。
- 第三方集成层。
- 持久化层。

这样做的原因是：

- 改动量可控。
- 现有业务接口不会被一次性打散。
- 为 MyBatis / Redis 替换当前内存仓储留出清晰入口。

## 3. 已完成的关键能力

### 3.1 Agent 与推荐闭环

- 聊天页已接后端真实接口。
- 中文推荐意图识别已修复。
- 推荐请求可进入 `PLAY_RECOMMENDATION`。
- 后端会生成真实推荐歌单并写入 `currentPlaylistId/currentTrackIndex`。
- 左侧栏可展示推荐歌单历史。

### 3.2 歌单页

- 左侧推荐歌单点击后进入真实歌单页。
- 歌单页已绑定 `GET /api/playlists/{playlistId}/detail`。
- 当前歌单页行结构为：
  - 序号
  - 标题（封面 / 歌名 / 艺人）
  - 专辑
  - 添加时间
  - 时长
- `addedAt` 已从后端 DTO 向前端透传。

### 3.3 播放器与当前播放栏

- 底部播放器已接真实播放会话。
- 右侧当前播放栏已显示真实歌单名、当前曲目、艺人、下一首。
- 底部设备面板已可打开、刷新并显示当前可用设备。
- 底部设备摘要已显示当前设备名称，设备离线时可显示无活动设备提示。
- 前端播放异常提示已完成首轮统一：
  - 设备不可用。
  - bridge 授权过期 / 无效。
  - Spotify 网络 / DNS 异常。
  - 通用播放控制失败。
- 队列抽屉已支持：
  - 展示完整歌单上下文。
  - 点击非当前曲目切歌。
  - “队列中的下一首歌”随当前曲目同步。

### 3.4 真实 Spotify 播放控制

已验证通过：

- 设备发现。
- `transfer playback`。
- `pause / play / sync`。
- `next / previous`。
- `seek`。
- `mode` 中的 `SEQUENTIAL / SHUFFLE / LIST_LOOP`。

其中：

- `SHUFFLE` 已明确收敛为“本地歌单上下文模式”，不再依赖 Spotify 原生队列上下文。
- `next / previous` 的目标曲目由本地歌单上下文决策，再通过真实 Spotify `playTrack` 落到设备上。

### 3.5 Spotify 集成稳定性修复

已修复两个关键问题：

1. 播放控制请求误发到 `localhost:80`。
2. Reactor Netty 默认 DNS resolver 无法稳定解析 `accounts.spotify.com` 与 Spotify API 域名。

当前 Spotify 相关 WebClient 已改为使用系统 / JDK resolver。

## 4. 持久化方向

### 4.1 当前状态

当前系统处于“默认内存、可切换真实持久化”的过渡态：

- 默认运行模式仍为内存仓储，便于本地快速启动。
- 当配置 `agentmusic.persistence.mode=mybatis` 时：
  - `PlaylistRepository`
  - `PlaylistTrackRepository`
  - `TrackRepository`
  - `ArtistRepository`
  - `SessionRepository`
  - `ChatMessageRepository`
  会切换到 MyBatis / Redis 路径。
- Spotify bridge token 仍为文件持久化。

### 4.2 本轮新增的持久化入口

本轮已补：

- MyBatis Starter 依赖。
- MySQL 驱动依赖。
- Redis Starter 依赖。
- `MybatisPersistenceConfig`。
- `persistence.mybatis.mapper` Mapper 占位。
- `persistence.mybatis.model` 记录模型占位。
- `RedisPersistenceConfig`。
- `application.properties` 中的 MySQL / Redis / MyBatis 配置入口。

### 4.3 已完成的第一阶段实现

当前已落地的 MyBatis / Redis 仓储实现：

- `PlaylistRepository`
- `PlaylistTrackRepository`
- `TrackRepository`
- `ArtistRepository`
- `SessionRepository`（Redis + MyBatis 混合实现）
- `ChatMessageRepository`
- `UserRepository`

切换方式：

- 默认仍为内存实现。
- 当配置 `agentmusic.persistence.mode=mybatis` 时：
  - `MybatisPlaylistRepository`
  - `MybatisPlaylistTrackRepository`
  - `MybatisTrackRepository`
  - `MybatisArtistRepository`
  - `RedisMybatisSessionRepository`
  - `MybatisChatMessageRepository`
  - `MybatisUserRepository`
  会替换当前内存仓储实现。

### 4.4 后续实现原则

后续持久化替换按下面顺序推进：

1. 保持 Repository 抽象不变。
2. 逐个替换内存实现，而不是一次性推翻。
3. MyBatis 负责 MySQL 持久化实现。
4. Redis 负责热状态、缓存和会话加速。

### 4.5 当前 SessionRepository 形态

当前 `SessionRepository` 已切到混合实现：

1. 写入时：
   - 先落 MySQL `sessions`
   - 再写 Redis 热缓存
2. 读取时：
   - 先查 Redis
   - Redis 未命中再回源 MySQL
   - 回源后再回填 Redis

当前为支撑该路径，已对 `sessions` 表新增：

- `current_playlist_id`
- `current_track_index`

如果本地 MySQL 是旧表结构，需要先执行 migration：

- `agentmusic-backend/src/main/resources/db/mysql/migrations/20260425_add_session_context_columns.sql`

### 4.6 当前 ChatMessageRepository 形态

当前聊天历史已切到 MyBatis：

1. 新消息直接写入 MySQL `chat_messages`
2. 最近消息按 `created_at DESC` 读取
3. 超出保留上限时，按用户维度裁剪旧消息

这意味着聊天页历史现在也进入了持久化路径，不再只依赖内存仓储。

### 4.7 当前端到端验证结论

当前已经具备可重复运行的真实 E2E 路径：

1. 聊天页发送推荐请求。
2. 后端生成推荐歌单并开始真实播放。
3. 左侧栏刷新并显示最新推荐歌单。
4. 点击最新歌单进入真实歌单页。
5. 页内点歌触发真实播放接口。
6. MySQL 中可观测到：
   - `playlists`
   - `playlist_tracks`
   - `tracks`
   - `artists`
   - `chat_messages`
   - `sessions`
   持久化数据增长。
7. 底部设备面板可打开并显示当前设备。
7. 底部设备面板可打开，并显示当前可用设备。

当前 E2E 入口脚本为：

- `agentmusic-frontend/scripts/e2e-persistence.js`

该脚本的稳定运行前提是：

- 前端运行于 `localhost:5173`
- 后端运行于 `localhost:8080`
- bridge 账号下存在活跃 Spotify 设备
- 优先通过 Edge CDP `http://127.0.0.1:9222` 接入浏览器

### 4.8 当前启动自检与 schema bootstrap

当前在 `agentmusic.persistence.mode=mybatis` 下，后端启动时会执行：

1. 使用 `db/mysql/schema.sql` 确保基础表存在。
2. 创建 `schema_migrations` 表。
3. 扫描并应用 `db/mysql/migrations/*.sql` 中尚未执行的 migration。
4. 校验关键表与关键列是否存在。

当前已覆盖的关键校验包括：

- `users`
- `playlists`
- `playlist_tracks`
- `tracks`
- `artists`
- `chat_messages`
- `sessions`
- `schema_migrations`

以及：

- `sessions.current_playlist_id`
- `sessions.current_track_index`
- `users.preferences`

同时，MySQL 连接串已加入：

- `createDatabaseIfNotExist=true`

用于减少本地首次启动时因为数据库尚未创建而直接失败的情况。

### 4.9 当前开发约束

当前本地开发流程下，后端代码更新后由 Spring Boot 自动重启。

原因是：

- 当前联调与 E2E 依赖最新后端代码生效后再继续。
- “重启恢复验证”不应进入每轮开发的常规验收流程。

因此当前阶段的联调方式固定为：

1. 完成一轮代码修改。
2. 等待 Spring Boot 自动重启完成，并确认 `localhost:8080` 恢复响应。
3. 直接继续页面联调与 E2E。

“重启恢复验证”不再作为当前阶段的常规验收项。

## 5. 重大限制

### 5.1 Bridge 模式的多用户冲突

当前版本仍采用单个 Spotify bridge 账号承载真实播放控制。

这意味着：

- 多个用户不能同时各听各的歌。
- 所有真实播放上下文共享同一个 Spotify 账号和设备集合。
- 这是当前版本的重大架构缺陷，但不阻塞课程设计阶段的单实例演示。

### 5.2 当前 Agent 仍不是完整在线 LLM Agent

- 当前推荐主链可运行。
- 但整体仍以 planner + executor 为主。
- 真实在线 LLM 还未成为主闭环必需部分。

## 6. 当前待完成任务

### Priority 1

1. 为 migration 执行结果增加更明确的日志与失败提示。
2. 前端增加设备异常、授权异常、网络异常的完整用户提示。
3. 完善设备切换后的回显、刷新与边界状态。

### Priority 2

1. 多设备场景下的设备切换联调与验证。
2. 设备面板的视觉细节与状态提示。
3. 更完整的当前播放与歌单页视觉细节。

### Priority 3

1. 歌词。
2. 流式聊天输出。
3. 真实 LLM 完整接管聊天主链。
4. 多用户独立 Spotify 账号绑定。

## 7. 本轮结论

本轮的实质性成果不是新增功能，而是把项目从“能跑”推进到“结构更清楚，后续可持续替换持久化层”。

当前已经具备继续推进 MyBatis / MySQL / Redis 落地的前提：

- Web 层边界已清楚。
- Spotify 集成层已清楚。
- 持久化层入口已单独划出。
- 现有仓储抽象仍可作为替换边界。
- `Playlist / PlaylistTrack / Track / Artist / Session` 已有可切换的持久化实现。
