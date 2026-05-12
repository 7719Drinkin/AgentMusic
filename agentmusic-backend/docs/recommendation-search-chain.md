# Recommendation Search Chain

版本：R1.0  
更新日期：2026-05-12

## 1. 目的

本文档记录当前推荐链路从用户请求到最终歌单曲目的实际执行方式，用于：

1. 说明 LLM 在推荐链中的职责边界。
2. 说明 Spotify 检索入口、候选召回、重排和硬约束的分工。
3. 为后续优化实体型请求命中精度、主题型请求和语义归一化提供参考。

## 2. 当前总体架构

当前推荐链采用“LLM 主导语义与偏好，确定性代码负责检索与校验”的混合架构。

### 2.1 责任分配

- `LLM` 负责：
  - 规划意图识别：`RECOMMEND_PLAYLIST` / `PLAY_RECOMMENDATION`
  - 推荐规格提炼：`RecommendationSpec`
  - 候选集重排：`RecommendationRerank`
- `确定性代码` 负责：
  - 结构化实体抽取兜底
  - Spotify 候选召回
  - 同艺人 / 同专辑硬过滤
  - 显式标题优先插入
  - 可播放条目去重与最终截断

### 2.2 核心文件

- 规划 Harness：
  - `src/main/java/com/agentmusic/agentmusic_backend/planner/llm/AgentLlmPlanningHarness.java`
- 推荐规格与重排主入口：
  - `src/main/java/com/agentmusic/agentmusic_backend/service/impl/LlmBackedRecommendationSelectionService.java`
- 本地 query 提炼：
  - `src/main/java/com/agentmusic/agentmusic_backend/service/impl/SearchQueryRefiner.java`
- Spotify 元数据查询：
  - `src/main/java/com/agentmusic/agentmusic_backend/service/impl/DefaultMusicMetadataService.java`
- Spotify Web API 客户端：
  - `src/main/java/com/agentmusic/agentmusic_backend/integration/spotify/SpotifyWebApiCatalogClient.java`

## 3. 当前请求分类

### 3.1 Artist-only

例子：

- `推荐20首张雨生的歌`
- `来点谭咏麟的歌`

当前链路：

1. 提炼 `artist` 和 `desiredTrackCount`
2. 先 `searchArtists(artistName)` 获取主 `artistId`
3. 基于 `artistId` 展开 artist catalog：
   - artist albums
   - album tracks
4. 只保留同一 `artistId` 的候选
5. 交给 LLM rerank
6. 最终由代码做 strict artist filter 和数量截断

说明：

- 当前不再把 plain-text `track search("张雨生")` 作为主召回入口。
- 当前 `artist-only` 请求已能稳定返回深候选池，不再只拿到第一页约 6 首的结果。

### 3.2 Artist + Track (+ Album)

例子：

- `推荐张雨生专辑《两伊战争红色热情》里的《我最深爱的人伤我最深》以及张雨生的其他歌曲`
- `推荐张雨生《发晕》以及他的其他歌曲`

当前链路：

1. 提炼 `artist` / `track` / `album`
2. 先 `searchArtists(artistName)` 获取主 `artistId`
3. 优先尝试结构化 Spotify query：
   - `track:<title> artist:<artist> album:<album>`
   - `track:<title> artist:<artist>`
   - `artist:<artist> album:<album>`
4. 只保留同一 `artistId` 的候选
5. 如果显式标题未充分命中，再补同艺人 catalog 候选
6. 用 LLM rerank 候选
7. 代码最终确保：
   - 显式标题尽量放第 1 位
   - 同专辑歌曲优先
   - 同艺人其他歌曲后补

说明：

- 当前这条链已经修复“同名异歌手被排到前面”的问题。
- 当前标题匹配带有简繁折叠和模糊匹配，不只依赖完全相等。

### 3.3 Album-only

例子：

- `推荐谭咏麟《世外桃源》专辑里的歌曲`
- `推荐张雨生专辑《两伊战争白色才情》里的歌`
- `推荐张雨生《两伊战争白色才情》专辑里的歌曲`

当前链路：

1. 先提炼 `album`
2. 如果请求语义是“专辑里的歌/歌曲”，进入 `album-only hard scope`
3. 候选只允许来自目标专辑
4. 如果有艺人约束，则优先同艺人；若艺人名字符串不稳定，则按该专辑候选中的主导 `artistId` 过滤
5. 若用户没有明确要求“其他歌曲”，则不允许向专辑外扩展

说明：

- 当前已补“语义归一化”：
  - `张雨生专辑《两伊战争白色才情》里的歌`
  - `张雨生《两伊战争白色才情》专辑里的歌曲`
  会被收敛成同一个 `album-only` 结果。

### 3.4 Theme-aware（待实现）

例子：

- `给我来点90年代的粤语歌`
- `来点适合雨天通勤的中文歌`

当前状态：

- 尚未切出独立主题型召回流。
- 当前仍主要依赖推荐规格 + 多 query 搜索 + rerank。

后续建议：

1. 提炼主题型 `RecommendationSpec`
2. 生成多组主题召回 query
3. 做年份 / 语种 / 风格标签过滤
4. 再进入 LLM rerank

## 4. 当前实际调用 Spotify 的方式

当前主要使用 Spotify Web API：

- `GET /v1/search`
- `GET /v1/artists/{id}`
- `GET /v1/artists/{id}/albums`
- `GET /v1/albums/{id}`

### 4.1 Search API

当前支持字段过滤：

- `track:<title>`
- `artist:<name>`
- `album:<name>`

组合查询示例：

- `track:发晕 artist:张雨生 album:两伊战争白色才情`

### 4.2 Artist catalog expansion

对 `artist-only` 和部分实体约束型请求，当前已不依赖 search 第一页结果，而是：

1. 先查 `artistId`
2. 再查该艺人的 albums
3. 再展开 album tracks

说明：

- `GET /artists/{id}/top-tracks` 在当前 token 下返回过 `403 Forbidden`
- 因此当前 artist catalog 扩展主要依赖 `albums + album tracks`

## 5. 当前已知约束

### 5.1 LLM 不是最终曲库真相来源

LLM 负责推荐语义和重排，不负责直接保证 Spotify catalog 中一定存在某首歌。

因此：

- 候选召回仍必须依赖 Spotify API
- 最终结果仍必须经过代码校验

### 5.2 语义归一化仍有一部分在本地代码中

当前已对以下语义做了本地 guardrail：

- 明确数量优先于默认数量
- `专辑里的歌` 归一为 `album-only`
- 未显式要求 `其他歌曲` 时，不允许 album-only 自动外扩

后续方向：

- 可以把更多语义归一化交给 LLM
- 但应保留代码侧 contract validation 和 repair

## 6. 当前剩余优化点

### Priority 1

1. 主题型推荐流拆分  
2. 推荐规格语义归一化单独建 Harness  
3. 将 LLM semantic normalization 与 deterministic guardrail 解耦  

### Priority 2

1. 歌单命名从原始请求截断改成结构化命名  
2. 继续优化 album/title 变体命中  
3. 对 rerank 结果增加可观测性 metadata  

### Priority 3

1. 流式聊天输出  
2. 多用户独立 Spotify 授权  
