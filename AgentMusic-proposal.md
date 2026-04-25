# AgentMusic 项目启动 Proposal

版本：P2.0
日期：2026-04-25

## 1. 项目概述

AgentMusic 是一个以自然语言音乐推荐和真实 Spotify 播放控制为核心的 Web 应用。

当前版本的目标不是完整复刻 Spotify，而是完成一个可演示、可维护的最小闭环：

1. 用户在聊天页提出音乐需求。
2. 系统生成推荐歌单。
3. 左侧栏展示推荐歌单历史。
4. 用户进入真实歌单页查看曲目。
5. 系统通过 Spotify bridge 账号驱动真实设备播放。
6. 底部播放器与当前播放栏同步真实状态。

## 2. 当前技术路线

### 前端

- React + Vite
- 主要页面：聊天页、首页、Library、Playlist 详情页
- 主要 UI：左侧推荐歌单区、底部播放器、右侧当前播放栏

### 后端

- Java 21 + Spring Boot 3.5
- Semantic Kernel 作为 Agent 骨架能力
- Spotify Web API 作为音乐元数据与播放控制来源
- MyBatis 作为 MySQL 持久化接入方案
- Redis 作为热状态与缓存层

## 3. 当前后端结构

当前后端已按职责进行重构：

- `config`：全局配置与属性绑定
- `domain`：领域模型
- `planner`：意图识别与执行骨架
- `service`：领域服务与应用服务
- `web.controller`：REST Controller
- `web.dto`：Web DTO
- `web.mapper`：领域到 DTO 映射
- `web.exception`：API 错误处理
- `integration.spotify`：Spotify 集成代码
- `persistence.repository`：仓储抽象
- `persistence.repository.memory`：内存仓储实现
- `persistence.repository.file`：文件持久化实现
- `persistence.redis`：Redis 配置与 key 规范
- `persistence.mybatis`：MyBatis 配置、Mapper 与记录模型

## 4. 当前优先级

### Priority 1

1. 文档编码统一与结构同步
2. 包重构后的全量验证
3. 使用 MyBatis 推进 MySQL 持久化落地
4. 使用 Redis 承接播放会话热状态与缓存

### Priority 2

1. 设备列表 UI
2. 设备切换 UI
3. 更完整的异常提示和状态反馈

### Priority 3

1. 歌词功能
2. 流式聊天输出
3. 真实 LLM 完整主链接管
4. 多用户独立 Spotify 授权

## 5. 关键工程判断

### 5.1 当前播放控制模型

当前真实播放控制采用：

- 本地歌单上下文决定目标曲目
- 真实 Spotify 设备负责实际播放

因此：

- `next / previous / shuffle / loop` 由本地歌单上下文决定目标曲目
- `play / pause / seek / transfer playback` 由 Spotify Web API 驱动真实设备

### 5.2 持久化策略

- MySQL：长期业务数据
- Redis：热状态和缓存
- 通过 Repository 抽象替换当前内存实现，不直接改前端业务逻辑

## 6. 当前重大限制

### 6.1 Bridge 账号共享限制

当前版本使用单一 Spotify bridge 账号进行真实播放控制。

限制如下：

- 多个用户不能同时各自独立播放不同歌曲
- 所有真实播放上下文共享同一套 Spotify 设备和播放状态

该限制在课程设计阶段接受，但必须在文档和演示中明确说明。

## 7. 下一阶段目标

下一阶段主任务不是继续堆 UI，而是：

1. 用 MyBatis 落地 MySQL 持久化
2. 用 Redis 落地会话与缓存
3. 在持久化稳定后补设备列表与设备切换 UI
