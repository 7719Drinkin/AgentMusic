# AgentMusic 无 Premium 可验证版本开发链路说明

版本：V1.0  
日期：2026-04-02

## 1. 目标

在不依赖 Spotify Premium、也不依赖本地 Spotify 客户端可用设备的前提下，先完成并验证以下主路径：

`聊天推荐 -> 歌单刷新 -> 本地播放状态同步 -> 底部播放器展示与本地控制`

该链路用于验证 AgentMusic 的核心产品闭环，而不是验证 Spotify 真实播放控制。

## 2. 当前原则

### 2.1 当前权威状态

当前阶段将后端 `PlaybackSession` 视为播放器唯一权威状态源：

- 前端播放器展示依赖 `GET /api/playback/{userId}/session`
- 聊天推荐完成后优先更新本地 `session`
- 播放器按钮优先更新本地 `session`
- Spotify bridge 控制属于增强能力，不作为当前阶段的唯一成功条件

### 2.2 默认用户行为

- 默认推荐请求：等同于“推荐并准备播放”
- 明确 `不要播放`：只生成歌单，不改当前播放会话

## 3. 主路径流程

### 3.1 聊天推荐

1. 前端聊天页发送 `POST /api/agent/chat`
2. 后端 planner 识别推荐意图
3. 后端生成推荐歌单并保存历史版本

### 3.2 歌单刷新

1. 聊天接口返回推荐歌单
2. 前端刷新左侧侧栏歌单列表
3. 用户无需刷新页面即可看到新歌单

### 3.3 本地播放状态同步

1. 若推荐路径为默认推荐播放路径，则后端自动选择歌单首曲
2. 后端创建或更新 `PlaybackSession`
3. `PlaybackSession` 至少包含：
   - `currentTrackId`
   - `currentPositionMs`
   - `isPlaying`
   - `playbackMode`
   - `currentPlaylistId`
   - `currentTrackIndex`

### 3.4 底部播放器展示与控制

1. 前端读取 `PlaybackSession`
2. 根据 `currentTrackId` 查询曲目详情
3. 根据曲目的 `artistId` 查询歌手详情
4. 底部播放器显示真实曲名、歌手名、封面、时长、进度、模式
5. 底部播放器按钮优先更新本地 `PlaybackSession`

## 4. 分阶段任务清单

### Task 1：统一 PlaybackSession 为播放器权威状态

目标：

- 推荐完成后，默认路径必须写入 `PlaybackSession`
- recommend-only 不得修改当前 `PlaybackSession`

验收：

- 推荐后访问 `GET /api/playback/{userId}/session` 可读到首曲

当前状态：已完成

### Task 2：聊天返回后自动联动歌单和播放器

目标：

- 聊天返回后，前端自动刷新左侧歌单与底部播放器

验收：

- 发送推荐请求后，不刷新页面也能看到歌单与播放器状态变化

当前状态：部分实现

- 左侧歌单与底部播放器都具备刷新入口
- 但聊天推荐链路当前尚未完成稳定联调验证

### Task 3：补齐当前播放歌单上下文

目标：

- 在 `PlaybackSession` 中保存：
  - `currentPlaylistId`
  - `currentTrackIndex`

原因：

- 上一首/下一首的本地 fallback 需要知道当前播放上下文

验收：

- 后端能根据当前会话推导出上下曲

当前状态：已完成

### Task 4：本地播放器控制闭环

目标：

- 在没有 Spotify 真实设备时，先完成本地闭环：
  - 播放/暂停
  - 上一首/下一首
  - 进度拖动
  - 模式切换

验收：

- 底部播放器每次操作后，`PlaybackSession` 状态变化并回显到前端

当前状态：部分实现

- 本地 fallback 已完成
- 底部播放器状态边界已补齐
- 仍需继续验证聊天推荐后自动切曲与本地控制回显

### Task 5：补充异常与边界处理

目标：

- 无推荐歌单
- 单曲歌单
- 当前会话为空
- Spotify 调用失败

验收：

- 不崩溃，不出现空白状态，前端有兜底显示

当前状态：部分实现

- 已处理播放器空状态、无音源、重复点击和单曲歌单边界
- 聊天推荐失败、推荐结果为空、联动失败提示仍需继续补齐

## 5. 当前与 Spotify 的关系

### 5.1 当前阶段不强依赖 Premium 的部分

- 聊天推荐
- 推荐歌单生成与保存
- 左侧历史歌单刷新
- 曲目与歌手元数据展示
- 本地 `PlaybackSession` 同步
- 底部播放器本地交互闭环

### 5.2 当前阶段后置到真实 Spotify 验证的部分

- 真实播放/暂停
- 真实切歌
- 真实 seek
- 真实播放模式同步
- 设备列表展示
- 设备切换

## 6. 当前开发顺序

1. 推荐完成后稳定写入本地 `PlaybackSession`
2. 前端联动刷新歌单与播放器
3. 补齐 `currentPlaylistId/currentTrackIndex`
4. 实现本地播放器控制 fallback
5. 补异常与边界处理

## 7. 后续修改规则

若该链路发生调整，需同步更新：

- 本文档
- `AgentMusic需求分析说明书.md`
- `AgentMusic开发笔记.md`
