# AgentMusic 任务清单

版本：T2.2
更新日期：2026-04-28

本文档用于跟踪当前版本的任务优先级、已完成工作和下一步实施顺序。

## 1. 已完成任务

### Priority 1

#### 工程与结构

- 完成后端核心分层与基础骨架。
- 完成后端包重构：
  - `web`
  - `integration.spotify`
  - `persistence`
- 引入 MyBatis / MySQL / Redis 的基础依赖与配置入口。
- E2E 持久化验证脚本已建立并稳定可复跑。

#### 推荐闭环

- 聊天页已接后端真实接口。
- 中文推荐请求可进入推荐分支。
- 推荐歌单可生成并写入播放会话。
- 左侧栏可展示推荐歌单。
- 推荐歌单已通过 E2E 验证写入 MySQL 并可从页面重新打开。

#### 歌单页

- 左侧推荐歌单点击进入真实歌单页。
- 歌单页已接 `GET /api/playlists/{playlistId}/detail`。
- 歌单页当前字段结构已稳定为：
  - 序号
  - 标题（封面 / 歌名 / 艺人）
  - 专辑
  - 添加时间
  - 时长

#### 播放器与当前播放栏

- 底部播放器已接真实会话。
- 右侧当前播放栏已接真实歌单与曲目上下文。
- 队列抽屉已支持点击切歌。
- “队列中的下一首歌”与当前曲目同步。

#### 真实 Spotify 控制

- 设备发现已打通。
- `transfer playback` 已打通。
- 已验证：
  - `pause / play / sync`
  - `next / previous`
  - `seek`
  - `SEQUENTIAL / SHUFFLE / LIST_LOOP`
- Spotify DNS resolver 问题已修复。

#### 持久化

- 已完成：
  - `PlaylistRepository`
  - `PlaylistTrackRepository`
  - `TrackRepository`
  - `ArtistRepository`
  - `SessionRepository`
  - `ChatMessageRepository`
  - `UserRepository`
  的 MyBatis / Redis 持久化实现
- 已完成端到端验证：
  - 聊天推荐
  - 左侧歌单刷新
  - 真实歌单页打开
  - 页内点歌
  - MySQL 落库

### Priority 2

- Playwright + Edge CDP 页面级回归通路已建立。

## 2. 当前待完成任务

### Priority 1

#### A. 文档与工程卫生

- 统一根目录核心文档编码为 UTF-8。
- 同步文档中的最新结构、持久化方向和重大限制。
- 保持 E2E 脚本、运行日志和临时文件的工作区整洁。

#### B. 持久化落地

- 使用 MyBatis 实现 MySQL 持久化层。
- 已完成：
  - `PlaylistRepository`
  - `PlaylistTrackRepository`
  - `TrackRepository`
  - `ArtistRepository`
  - `SessionRepository`
  - `ChatMessageRepository`
  - `UserRepository`
- 已完成：
  - `schema.sql` 启动执行
  - `schema_migrations` 建表
  - `db/mysql/migrations/*.sql` 启动扫描与执行
  - 关键表 / 列启动校验
- Redis 用于：
  - 播放会话热状态
  - 元数据缓存
  - 最近歌单缓存

#### C. 前端异常提示补齐

- 设备不可用提示。
- 授权过期提示。
- 网络 / DNS 异常提示。
- 播放控制失败提示。

### Priority 2

- 设备列表 UI。
- 设备切换 UI。
- 歌单页与当前播放栏的进一步视觉细化。

### Priority 3

- 歌词功能。
- 流式聊天输出。
- 真实 LLM 主链接管。
- 多用户独立 Spotify 授权。

## 3. 当前建议执行顺序

1. 增加 migration 执行失败时更明确的启动错误说明。
2. 补持久化模式下的重启恢复回归测试。
3. 在持久化稳定后，补设备列表 / 设备切换 UI。
4. 再继续播放器和歌单页的细节增强。

## 4. 重大缺陷记录

### Bridge 模式播放上下文共享

当前版本使用单个 Spotify bridge 账号控制真实播放。

这意味着：

- 多个用户不能同时各自独立播放不同歌曲。
- 所有真实播放上下文共享同一套 Spotify 设备和播放状态。

该缺陷已确认，当前版本接受此限制，但必须在后续答辩、文档和演示中明确说明。
