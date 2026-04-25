# AgentMusic 软件需求分析说明书（SRS）

版本：S2.0
日期：2026-04-25
项目名称：AgentMusic

## 1. 文档目的

本文档用于明确 AgentMusic 当前阶段的功能边界、约束条件、优先级和后续实施方向。

当前版本的核心不是构建一个完整的音乐平台，而是验证以下闭环：

1. 用户用自然语言提出音乐需求。
2. 系统生成推荐歌单并保存。
3. 系统将推荐结果同步到播放会话。
4. 前端通过聊天区、左侧歌单区、歌单页、底部播放器和当前播放栏呈现结果。
5. 系统通过 Spotify bridge 账号驱动真实 Spotify 设备播放。

## 2. 产品范围

### 2.1 当前版本要做的内容

- 聊天驱动的音乐推荐。
- 推荐歌单生成与历史歌单展示。
- 歌单详情页。
- 底部播放器与右侧当前播放栏。
- 真实 Spotify 播放控制。
- MySQL / Redis 持久化方案设计与逐步落地。

### 2.2 当前版本不做的内容

- 多用户独立 Spotify 授权体系。
- 社区功能。
- 商业化账户体系。
- 原生移动端。
- 歌词完整服务。

## 3. 当前总体架构

### 3.1 前端

- React + Vite。
- 页面主体：
  - 聊天页
  - 首页
  - Library
  - Playlist 详情页
- 关键 UI：
  - 左侧推荐歌单区
  - 底部播放器
  - 右侧当前播放栏

### 3.2 后端

当前后端采用以下结构：

- `config`
- `domain`
- `planner`
- `service`
- `web.controller`
- `web.dto`
- `web.mapper`
- `web.exception`
- `integration.spotify`
- `persistence.repository`
- `persistence.repository.memory`
- `persistence.repository.file`
- `persistence.redis`
- `persistence.mybatis`

### 3.3 第三方依赖

- Spotify Web API
- OpenAI 兼容模型接口（当前仍非主闭环依赖）
- MySQL
- Redis
- MyBatis

## 4. 功能需求

### 4.1 聊天推荐

系统应支持用户发送中文自然语言推荐请求，并返回推荐结果。

验收要求：

- 请求进入推荐意图分支，而不是通用 fallback。
- 系统生成推荐歌单。
- 系统更新播放会话。

### 4.2 左侧推荐歌单

系统应支持：

- 显示推荐歌单历史。
- 点击推荐歌单进入真实歌单页。
- 当前激活歌单在侧栏具备激活态。

### 4.3 歌单页

歌单页应展示：

- 序号
- 标题（封面 / 歌名 / 艺人）
- 专辑
- 添加时间
- 时长

歌单页应支持页内点歌，并触发真实播放。

### 4.4 底部播放器

底部播放器应支持：

- 播放 / 暂停
- 上一首 / 下一首
- 进度拖动
- 播放模式切换
- 真实播放状态同步

### 4.5 当前播放栏与队列

系统应支持：

- 显示当前歌单名
- 显示当前曲目和艺人
- 显示“队列中的下一首歌”
- 打开完整队列抽屉
- 点击队列中的非当前歌曲切歌

### 4.6 真实 Spotify 控制

系统应支持：

- 设备发现
- transfer playback
- pause / play / sync
- next / previous
- seek
- playback mode

## 5. 非功能需求

### 5.1 结构约束

- Web 层、第三方集成层、持久化层必须分离。
- 不允许继续将 demo / fallback 数据混入运行时主路径。
- 持久化替换必须通过 Repository 抽象进行，不允许页面层直接依赖具体存储实现。

### 5.2 持久化约束

- MySQL 持久化使用 MyBatis。
- Redis 用于热状态与缓存，不替代 MySQL 的长期数据责任。
- 当前内存仓储仅作为过渡实现，不作为最终方案。

### 5.3 可维护性约束

- 包结构要能直接反映职责：Web、Integration、Persistence。
- 文档必须保持 UTF-8，可持续维护。

## 6. 当前持久化设计方向

### 6.1 MySQL

推荐首先落地的表：

- `playlists`
- `playlist_tracks`
- `tracks`
- `artists`
- `chat_messages`
- `sessions`

这些表由 MyBatis Mapper 与对应 Repository 实现承接。

### 6.2 Redis

Redis 负责：

- 当前播放会话热状态
- 最近歌单缓存
- 曲目 / 艺人元数据缓存

### 6.3 实施策略

1. 保持现有 Repository 接口。
2. 在 `persistence.mybatis` 下新增 Mapper 与持久化实现。
3. 在 `persistence.redis` 下新增缓存支持。
4. 逐个模块替换内存仓储。

## 7. 当前优先级

### Priority 1

- 文档编码清理与结构同步。
- 后端结构重构后验证。
- MyBatis / MySQL / Redis 基础设施进入项目。
- 持久化开始替换内存仓储。

### Priority 2

- 设备列表 UI。
- 设备切换 UI。
- 更完整的前端异常提示与状态反馈。

### Priority 3

- 歌词。
- 流式聊天输出。
- 多用户独立 Spotify 授权。
- 真实 LLM 完整主链。

## 8. 重大限制

### 8.1 Bridge 账号共享限制

当前系统使用单一 Spotify bridge 账号控制真实播放。

限制如下：

- 多个用户不能同时各自独立播放不同歌曲。
- 所有真实播放状态共享同一套 Spotify 播放上下文。

这是当前版本的重大架构缺陷，但在课程设计阶段接受。

### 8.2 当前 Agent 仍以规则驱动为主

- 当前推荐主闭环已跑通。
- 但完整在线 LLM 主链还未替换当前 planner 驱动逻辑。

## 9. 当前验收标准

当前阶段最小可验收版本应满足：

1. 聊天请求能生成推荐歌单。
2. 左侧推荐歌单能显示并进入真实歌单页。
3. 歌单页展示真实详情结构。
4. 底部播放器与当前播放栏能显示真实会话。
5. 真实 Spotify 播放控制可用。
6. 持久化方案已明确切换为 MyBatis + MySQL + Redis。
