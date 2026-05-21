# AgentMusic 任务清单

版本：T2.7
更新日期：2026-05-21

本文档用于跟踪当前版本的任务优先级、已完成工作和下一步实施顺序。

## 1. 已完成任务

### Priority 1

#### 工程与结构

- 完成后端核心分层与基础骨架。
- 完成后端包重构：
  - `web`
  - `integration.spotify`
  - `persistence`
- 完成 `planner.llm` 规划 Harness：
  - 输入 Envelope
  - 严格 JSON 输出契约
  - 固定 step template
  - 服务端验证
  - 单元测试
- 引入 MyBatis / MySQL / Redis 的基础依赖与配置入口。
- E2E 持久化验证脚本已建立并稳定可复跑。

#### 推荐闭环

- 聊天页已接后端真实接口。
- 中文推荐请求可进入推荐分支。
- 推荐歌单可生成并写入播放会话。
- 左侧栏可展示推荐歌单。
- 推荐歌单已通过 E2E 验证写入 MySQL 并可从页面重新打开。
- 推荐链已完成职责重分配：
  - `LLM` 负责 `RecommendationSpec` 提炼与候选 rerank
  - `代码` 负责 Spotify 候选召回、硬过滤与最终入歌单校验
- 推荐语义归一化已切到 LLM 主导：
  - `RecommendationSpec.requestMode`
  - 独立请求默认忽略旧上下文
  - 代码侧仅保留窄范围 repair / guardrail
- `artist-only` 请求已切到：
  - `artist name -> artistId -> artist albums -> album tracks`
- `artist + track (+ album)` 请求已支持：
  - 结构化 Spotify query
  - 同艺人强过滤
  - 显式标题优先
- `album-only` 请求已支持：
  - 专辑硬边界
  - 等价语义归一化

#### 歌单页

- 左侧推荐歌单点击进入真实歌单页。
- 歌单页已接 `GET /api/playlists/{playlistId}/detail`。
- 歌单页当前字段结构已稳定为：
  - 序号
  - 标题（封面 / 歌名 / 艺人）
  - 专辑
  - 添加时间
  - 时长

#### Music Home

- `Music Home` 已保留并改成推荐内容聚合页。
- 当前 `Music Home` 已改成圆角卡片化布局，并支持分区展开 / 收起。
- `Music Home` 页面滚动问题已修复，页面底部内容可滚动进入视口并正常交互。
- 当前首页已展示：
  - 最近推荐歌单
  - 最近推荐中出现的歌曲
  - 最近推荐中出现的艺人
  - 最近推荐中出现的专辑
- `Search` 与 `Library` 不再作为独立功能页保留：
  - `/search` 现重定向到 `/music`
  - `/library` 现重定向到 `/`

#### 播放器与当前播放栏

- 底部播放器已接真实会话。
- 右侧当前播放栏已接真实歌单与曲目上下文。
- 队列抽屉已支持点击切歌。
- “队列中的下一首歌”与当前曲目同步。

#### 真实 Spotify 控制

- 设备发现已打通。
- `transfer playback` 已打通。
- 设备面板已接到底部播放器，可显示当前可用设备。
- 设备摘要已显示当前设备名称，并已进入 E2E 验证路径。
- 左侧推荐歌单区已去除与主导航重复的 `Agent Chat` / `Music Home` 固定入口。
- 左上角标识已改为 `AgentMusic` 文字标识，并与侧栏外壳统一为圆角卡片化风格。
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
  - 设备面板打开与当前设备显示
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
  - bootstrap 分阶段失败诊断（schema / migration / validation）
- Redis 用于：
  - 播放会话热状态
  - 元数据缓存
  - 最近歌单缓存

#### C. 前端异常提示补齐

- 已完成首轮归一：
  - 设备不可用提示。
  - 已选设备离线提示。
  - 授权过期提示。
  - 网络 / DNS 异常提示。
  - 播放控制失败提示。
- 已完成：
  - 后端结构化错误码首轮落地。
  - 前端优先消费后端错误码，再回退到状态码与消息文本。
- 已补：
  - `spotify-device-offline` 错误码。
  - 显式设备切换离线分支。
- 已继续补：
  - bridge 未连接 / access token 缺失 -> `spotify-authorization-missing`
  - 播放 `403` -> `spotify-device-restricted`
  - 播放 `404` -> `spotify-device-unavailable`
  - bridge disabled -> `spotify-bridge-disabled`
  - OAuth state 失效 -> `spotify-authorization-state`
- 已修正：
  - bridge 账号未连接时，播放控制与设备列表接口不再回退成“本地假成功”。
- 已扩展覆盖：
  - 聊天历史加载失败。
  - Agent 请求失败。
  - 左侧推荐歌单加载失败。
- 已修复：
  - 左侧推荐歌单超过页面高度后，列表区可滚动，底部歌单不再被遮挡。
- 下一步：
  - 继续细化设备切换中的成功 / 失败 / 离线提示。
  - 继续补充更多后端错误码，继续压缩前端按消息文本分类的兜底分支。
  - 当前前端已继续收一轮：播放 / 授权路径 `401/403/404/503` 优先按路径+状态归类，仅对传输层异常保留文本兜底。
  - 收设备摘要与设备面板的剩余视觉细节。

#### D. 真实 LLM 接入前置工作

- 已完成：
  - `openai.base-url` 可配置，支持 OpenAI 兼容接口。
  - `agent.chat.planning-harness-version`
  - `agent.chat.llm-recent-message-limit`
  - `agent.chat.llm-temperature`
  - `agent.chat.llm-planning-max-repair-attempts`
  - `agent.chat.llm-http-max-retries`
  - `agent.chat.llm-http-retry-backoff-ms`
  - `planner.llm` Harness 设计与单测
  - 影子模式真实调用器：
    - `OpenAiCompatiblePlanningClient`
    - `LiveLlmPlanningSmokeRunner`
  - LLM 输出验收流程文档化：
    - 原始输出必须先经过 Harness 解析与校验
    - 只有成功转换为 `AgentPlan` 才算可接受
  - Kimi 兼容接口在推荐主路径上已通过真实 shadow smoke，且重复运行可稳定通过 Harness
  - 已完成 `LlmBackedTaskPlanner` 主链接入：
    - 优先走真实 LLM + Harness
    - 失败时回退到 `SimpleTaskPlanner`
    - 回复 metadata 中已补 `planningSource` / `planningFallbackUsed`
- 下一步：
  - 验证真实运行中的 `/api/agent/chat` 已稳定返回 `planningSource=llm-harness`
  - 评估 `429` 限流下的回退体验和重试策略

#### E. 推荐链文档化与主题型检索

- 已新增推荐检索链独立文档：
  - `agentmusic-backend/docs/recommendation-search-chain.md`
- 当前已记录：
  - `artist-only`
  - `artist + track (+ album)`
  - `album-only`
  的真实运行链路和职责边界
- 已补首版 `theme-aware retrieval flow`：
  - `给我来点90年代的粤语歌`
  - `来点适合雨天通勤的中文歌`
  当前可生成主题型歌单，并已加入 `language / era / genre / mood / scene / seedArtists` 语义字段
- 主题型召回已补 seed artist catalog 扩展：
  - LLM 或兜底规则给出代表性艺人
  - 后端先解析 `artistId`
  - 再展开每个 seed artist 的小规模 catalog
  - Spotify theme search 作为补充召回
- 当前结论：
  - 实体型推荐链路已明显稳定
  - 主题型推荐已进入专用链路，但仍缺 release-year / language metadata，质量还需要继续调优
  - 主题型结果已补轻量清洗：同名曲优先去重，标题语种置信不足的候选延后，Live / 演唱会版本和 `Intro` 类非歌曲段落在有替代候选时降权

### Priority 2

- 多设备场景下的设备切换联调与验证。
- 设备面板的视觉细节与状态提示。
- 歌单页与当前播放栏的进一步视觉细化。
  - 本轮已完成歌单页 hero 状态 pill、说明文案清理、当前播放栏上下文条与 `Up next` 说明补强。
  - 本轮已继续完成歌单页乱码文案清理、设备面板 `current session tracked` 头部信息和无 hover 场景下的当前播放栏头部按钮可见性修复。
- recommendation spec / rerank 运行态可观测性：
  - 当前回包只稳定暴露 `planningSource`
  - 后续可继续补：
    - semantic normalization source
    - rerank source
    - repair / guardrail 命中情况

### Priority 3

- 歌词功能。
- 流式聊天输出。
- 主题型推荐的风格 / 年代 / 语种标签强化。
- 多用户独立 Spotify 授权。

## 3. 当前建议执行顺序

1. 继续调优 `theme-aware retrieval flow` 的候选证据、召回质量与本地过滤。
2. 继续处理 LLM provider 稳定性与 `planningFallbackReason` 可观测性。
3. 补 recommendation spec / rerank 的运行态可观测性。
4. 再继续播放器和歌单页的细节增强。

当前说明：

- 后端代码更新后由 Spring Boot 自动重启，恢复响应后直接继续联调与 E2E。
- 不再把“重启恢复验证”作为当前阶段常规开发验收项。

## 4. 重大缺陷记录

### Spotify Search 400 Bad Request

当前观察到后端偶发：

- `WebClientResponseException$BadRequest: 400 Bad Request from GET https://api.spotify.com/v1/search`

初步判断：

- 问题发生在后端调用 Spotify Web API `/v1/search`，不是前端直接请求失败。
- 当前推荐链会生成大量结构化 / 主题型 query，部分 query 可能因字段组合、特殊字符、长度、空白内容或 Spotify search 语法不兼容触发 400。
- 现有日志只打印了异常栈，缺少 Spotify 返回 body、最终 query、limit、market 等上下文，导致无法精准定位是哪条 query 触发。

后续待处理：

- 在 `SpotifyWebApiCatalogClient.searchTracks/searchArtists` 中补充 400 响应 body、query、limit、market 的结构化日志。
- 对进入 Spotify `/v1/search` 的 query 做输入校验与降级：
  - 空 query 直接跳过。
  - 过长 query 截断或拆分。
  - 结构化字段值中的特殊字符做安全清洗。
  - 单条 query 400 时只丢弃该候选，不中断整轮推荐。
- 增加回归用例覆盖 malformed query 不应导致整轮 Agent 请求失败。

### Bridge 模式播放上下文共享

当前版本使用单个 Spotify bridge 账号控制真实播放。

这意味着：

- 多个用户不能同时各自独立播放不同歌曲。
- 所有真实播放上下文共享同一套 Spotify 设备和播放状态。

该缺陷已确认，当前版本接受此限制，但必须在后续答辩、文档和演示中明确说明。
