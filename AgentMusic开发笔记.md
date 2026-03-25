# AgentMusic开发笔记

## 环境准备与初始化

### 后端

#### Maven环境准备
##### 1. 检查 Maven 是否已安装**
![mvn -version](image-1.png)

**如果没有 Maven，如何安装并配置环境**
推荐方式（最简单稳定）：
- Win
去官网下载最新版：https://maven.apache.org/download.cgi
下载 apache-maven-3.9.13-bin.zip（2026年当前最新版）。
解压到任意目录，例如：C:\apache-maven-3.9.13
配置环境变量（重要！）：
  - 右键`“此电脑” → 属性 → 高级系统设置 → 环境变量`
  系统变量 中`新建`：
  变量名：`MAVEN_HOME`
  变量值：`C:\apache-maven-3.9.13`
  编辑 Path 变量，新增：`%MAVEN_HOME%\bin`

重启 CMD/PowerShell，输入 mvn -version 验证。

##### 2. 用 Maven 创建 Spring Boot 项目
打开浏览器访问官方 Spring Initializr：
https://start.spring.io
  1. 填写以下参数
  ![spring-boot项目创建图例](image.png)

      |你勾选的依赖|实际作用|为什么重要|
      |---|---|---|
      |**Spring Web**|提供 Web 服务、RestController、JSON 处理等|后端项目必备|
      |**Spring Boot DevTools**|热重载（改代码后自动重启）|开发效率大幅提升|
      |**Lombok**|自动生成 getter/setter、constructor 等|减少大量重复代码|
      |**Spring Reactive Web**|支持 WebFlux（响应式编程）|后面做高并发 Agent 时有用|

  2. 点击 GENERATE 下载 zip 文件。
  3. 解压后，用你喜欢的 IDE（IntelliJ / VS Code）打开项目文件夹。
  4. 在项目根目录 运行以下命令验证（第一次会自动下载依赖）：
      ```bash
      mvn clean install
      ```
  5. 启动项目测试：
     ```Bash
     mvn spring-boot:run
     ```
     看到 `Started AgentMusicApplication` 即成功。

##### 3. 给项目添加 Semantic Kernel Java 依赖
1. 修改 pom.xml
   ```xml
    <!-- ==================== Semantic Kernel Java  ==================== -->
    <!-- 先添加dependencyManagement，即添加semantickernel-bom，后添加dependencies -->
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>com.microsoft.semantic-kernel</groupId>
                <artifactId>semantickernel-bom</artifactId>
                <version>1.4.4-RC2</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <!-- Semantic Kernel 模块（不再写 version，由 BOM 统一管理） -->
        <dependency>
            <groupId>com.microsoft.semantic-kernel</groupId>
            <artifactId>semantickernel-api</artifactId>
        </dependency>

        <dependency>
            <groupId>com.microsoft.semantic-kernel</groupId>
            <artifactId>semantickernel-agents-core</artifactId>
        </dependency>

        <!-- OpenAI 连接器（正确名称！） -->
        <dependency>
            <groupId>com.microsoft.semantic-kernel</groupId>
            <artifactId>semantickernel-aiservices-openai</artifactId>
        </dependency>
    </dependencies>
   ```

2. 在终端中运行
    ```bash
    mvn clean install -U
    ```

    输出Build Success即成功。
    ![Build Success](image-2.png)

3. 创建 Kernel 配置类
   路径：`src/main/java/com/agentmusic/config/SemanticKernelConfig.java`
   内容：
    ```java
    package com.agentmusic.config;

    import com.azure.ai.openai.OpenAIAsyncClient;
    import com.azure.ai.openai.OpenAIClientBuilder;
    import com.azure.core.credential.KeyCredential;
    import com.microsoft.semantickernel.Kernel;
    import com.microsoft.semantickernel.aiservices.openai.chatcompletion.OpenAIChatCompletion;
    import org.springframework.beans.factory.annotation.Value;
    import org.springframework.context.annotation.Bean;
    import org.springframework.context.annotation.Configuration;

    @Configuration
    public class SemanticKernelConfig {

        @Value("${openai.api.key}")
        private String openAiApiKey;

        @Bean
        public Kernel kernel() {

            OpenAIAsyncClient client = new OpenAIClientBuilder()
                    .credential(new KeyCredential(openAiApiKey))
                    .endpoint("https://api.openai.com")
                    .buildAsyncClient();

            OpenAIChatCompletion chatCompletion =
                    OpenAIChatCompletion.builder()
                            .withModelId("gpt-4o")
                            .withOpenAIAsyncClient(client)
                            .build();

            return Kernel.builder()
                    .withAIService(OpenAIChatCompletion.class, chatCompletion)
                    .build();
        }
    }
    ```

    同时在 `src/main/resources/application.properties` 中添加 openai.api.key 配置。
    ```properties
    openai.api.key=your-api-key-here
    ```

## 当前后端整理进度

### 已完成的基础骨架整理

本轮已将后端从“只有启动类和 Kernel 配置”的状态，整理成适合后续继续开发的基础结构。

当前 `src/main/java/com/agentmusic/agentmusic_backend` 下已预留以下包：

- `config`：配置类与配置属性
- `controller`：后续 HTTP 接口入口
- `service`：业务服务接口
- `service.impl`：业务服务实现
- `client`：外部服务客户端，例如 Spotify API
- `dto`：请求/响应数据结构
- `plugin`：Semantic Kernel 插件适配层
- `domain`：核心领域模型
- `exception`：异常定义

### 已修正的结构问题

原先 `SemanticKernelConfig` 位于 `com.agentmusic.config`，默认不在 `AgentMusicApplication` 的组件扫描范围内。现已迁移到：

```text
src/main/java/com/agentmusic/agentmusic_backend/config
```

这样 Spring Boot 启动时可以正常扫描到相关配置类。

### API Key 安全配置调整

原先项目将 `openai.api.key` 直接写在：

```text
src/main/resources/application.properties
```

这会导致密钥进入代码仓库，存在明显安全风险。现已调整为：

1. `application.properties` 只保留安全占位配置
2. 支持通过环境变量读取：
   - `OPENAI_API_KEY`
   - `OPENAI_MODEL`
3. 支持通过项目根目录的本地文件读取：
   - `application-local.properties`
4. 新增示例文件：
   - `application-local.example.properties`
5. `.gitignore` 已忽略：
   - `application-local.properties`
   - `.env`

当前推荐的本地配置方式：

```properties
openai.api.key=your-openai-api-key
```

将其写入项目根目录的 `application-local.properties`，不要提交到仓库。

如果使用环境变量，则可直接设置：

```bash
OPENAI_API_KEY=your-openai-api-key
OPENAI_MODEL=gpt-4o
```

当前 `Kernel` Bean 仅会在检测到 `openai.api.key` 已实际提供时创建，避免因为空配置导致应用启动时报错。

### Maven 本地构建隔离

为尽量绕开 Windows 下的目录权限问题，当前项目已固定为“项目内独立仓库 + 干净构建目录”模式：

1. Maven 本地依赖仓库固定到：

```text
agentmusic-backend/.m2/repository
```

对应配置文件：

```text
agentmusic-backend/.mvn/maven.config
```

其内容为：

```text
-Dmaven.repo.local=.m2/repository
```

2. Maven 构建产物目录固定到：

```text
agentmusic-backend/build
```

对应 `pom.xml` 中已设置：

```xml
<build>
    <directory>${project.basedir}/build</directory>
</build>
```

3. `.gitignore` 已忽略以下本地产物：
   - `.m2/`
   - `build/`

### 后续推荐使用方式

优先使用以下命令：

```bash
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

如果系统里的 `mvnw` 仍受 PowerShell 环境影响，则退回：

```bash
mvn test
mvn spring-boot:run
```

此时 Maven 也会自动读取 `.mvn/maven.config`，继续使用项目内仓库。

### 当前状态说明

当前后端仍处于“工程初始化 + Kernel 接入 + 目录骨架整理”阶段，尚未开始实现以下业务能力：

- Agent 对话接口
- Spotify API 客户端
- 播放控制能力
- 推荐歌单生成
- 历史歌单管理
- 聊天记录与用户偏好存储

## 数据库与缓存基线

### 已落地的数据库基线文件

已将数据库设计整理为正式的 MySQL 建表 SQL：

```text
agentmusic-backend/src/main/resources/db/mysql/schema.sql
```

当前基线包括以下表：

- `users`
- `playlists`
- `tracks`
- `playlist_tracks`
- `artists`
- `chat_messages`
- `sessions`

### 本轮设计收敛

为避免后续实现时语义不清，本轮对文档设计做了两点收敛：

1. `tracks` 新增 `last_accessed_at`
   - 原文档里清理策略是“保留最近 3 天使用过的轨道”
   - 因此仅使用 `updated_at` 不够准确，需要单独的访问时间字段

2. `sessions` 明确为“Redis 实时主存 + MySQL 持久快照”
   - Redis 负责实时读写
   - MySQL 负责恢复、审计和多设备状态兜底

### 已落地的 Redis 设计说明

已新增 Redis 设计基线文档：

```text
agentmusic-backend/docs/database/redis-design.md
```

核心约定包括：

- `user:session:{userId}`
- `user:playlists:{userId}`
- `playlist:tracks:{playlistId}`
- `track:info:{trackId}`
- `artist:bio:{artistId}`
- `chat:history:{userId}`

并明确了每类 key 的：

- 数据结构
- TTL
- 使用场景
- 读写顺序
- 清理策略

### 已落地的后端实体 / DTO / Redis key 约定

本轮已先把数据库设计转成一组后端代码基线，便于后续继续接 Repository、Service 和 Controller：

1. 领域模型（`domain`）
   - `User`
   - `UserPreferences`
   - `Playlist`
   - `PlaylistTrack`
   - `Track`
   - `Artist`
   - `ChatMessage`
   - `PlaybackSession`
   - `ChatRole`
   - `PlaybackMode`

2. DTO（`dto`）
   - `TrackDto`
   - `PlaylistTrackDto`
   - `PlaylistDto`
   - `ArtistDto`
   - `ChatMessageDto`
   - `PlaybackSessionDto`
   - `UserPreferencesDto`
   - `AgentChatRequest`
   - `AgentChatResponse`

3. Redis key 约定（`cache`）
   - `RedisKeys`
   - 内含 key 命名方法、TTL 常量、列表长度上限常量

### 当前阶段边界

当前这些类是“结构基线”，还没有接入：

- ORM / Repository
- Redis 客户端
- Controller 路由
- MySQL migration 工具
- 真实的 Spotify / Agent 业务流程

## 可运行后端层接线进度

### 本轮目标

本轮先不讨论 Controller 和 Spotify API 调用，而是先把“数据库/缓存/领域模型基线”接成当前可以运行的后端层。

当前策略是：

- 暂不直接接 MySQL / Redis 客户端
- 先通过 `Repository 接口 + 内存版实现 + Service 层 + Mapper` 搭起可运行结构
- 保证后续替换成真实 MySQL / Redis 时不需要推倒业务层

### 已新增的仓储层

已新增 Repository 抽象：

- `UserRepository`
- `PlaylistRepository`
- `PlaylistTrackRepository`
- `TrackRepository`
- `ArtistRepository`
- `ChatMessageRepository`
- `SessionRepository`

并新增当前阶段可运行的内存实现（`repository.memory`）：

- `InMemoryUserRepository`
- `InMemoryPlaylistRepository`
- `InMemoryPlaylistTrackRepository`
- `InMemoryTrackRepository`
- `InMemoryArtistRepository`
- `InMemoryChatMessageRepository`
- `InMemorySessionRepository`

这些实现的作用是：

- 先让服务层真实可用
- 后续再把接口实现替换成 MySQL / Redis 版本

### 已新增的服务层

已新增服务接口：

- `UserContextService`
- `MusicMetadataService`
- `PlaylistService`
- `ChatMemoryService`
- `PlaybackSessionService`
- `BackendRuntimeFacade`

并新增默认实现（`service.impl`）：

- `DefaultUserContextService`
- `DefaultMusicMetadataService`
- `DefaultPlaylistService`
- `DefaultChatMemoryService`
- `DefaultPlaybackSessionService`
- `DefaultBackendRuntimeFacade`

当前这些服务已可以完成：

- 用户上下文读写
- 歌曲/歌手元数据缓存写入与读取
- 历史推荐歌单创建与查询
- 聊天记录写入与最近消息读取
- 播放会话写入与活动会话查询
- 面向上层的统一运行时聚合读取

### 已新增的映射与基础配置

1. 映射层
   - `DomainDtoMapper`
   - 负责 `domain -> dto` 的统一转换

2. 基础配置
   - `TimeConfig`
   - 统一注入 `Clock`，避免时间逻辑散落在各处，方便后续测试和替换

3. 异常类型
   - `NotFoundException`

### 当前可运行含义

这里的“可运行”指：

- Spring Boot 能正常启动
- Service Bean 已经能被真实装配
- 后端内部已经存在一条完整的数据流：
  - 用户
  - 歌单
  - 轨道元数据
  - 聊天记录
  - 播放会话
  - 聚合读取

### 验证方式

本轮新增集成测试：

- `BackendRuntimeFacadeIntegrationTests`

测试覆盖了：

- 创建用户
- 创建推荐歌单
- 写入聊天记录
- 写入播放会话
- 通过 `BackendRuntimeFacade` 聚合读取运行时数据

本轮执行结果：

```bash
mvn test
```

结果为：

```text
BUILD SUCCESS
```

## 2026-03-25 Planner intent relationship refactor

- Updated planner docs so `PLAY_RECOMMENDATION` is the default recommendation path.
- Kept `RECOMMEND_PLAYLIST` as a reusable recommend-only subflow.
- Refactored planner code so recommendation playback reuses recommendation playlist generation first.
- Added `/api/agent/chat` API tests for:
  - recommend-only requests
  - default recommend-and-play requests

## 2026-03-25 Frontend reference split and modern scaffold

- Renamed the cloned reference repository directory to `agentmusic-frontend-reference`.
- Preserved the original reference project as a migration source.
- Created a new `agentmusic-frontend` workspace on a modern Vite + React + TypeScript baseline.
- Added a first-round migration plan for moving selected UI modules into the new frontend.

## 2026-03-25 Frontend round 1 shell migration

- Replaced the placeholder scaffold screen in `agentmusic-frontend` with a chat-first AgentMusic main screen.
- Adjusted the main information architecture away from a Spotify clone landing page and toward the actual product goal:
  - LLM chat as the main workspace
  - recommendation playlist history as the secondary panel
  - playback bar as a persistent bottom shell
- Kept the reference frontend only as a migration source and did not carry over its legacy Redux or routing setup.
- Confirmed the new frontend can still build after the first shell migration.

## Planner 设计说明

### 本轮目标

本轮不继续直接优化现有 planner 代码，而是先回到产品设计文档，基于 Agent 真正需要支持的能力，整理正式的 planner 设计说明。

### 已新增文档

1. Planner 正式设计说明：

```text
agentmusic-backend/docs/planner-design-spec.md
```

2. 产品能力到 planner intent 的映射表：

```text
agentmusic-backend/docs/planner-capability-mapping.md
```

### 核心设计结论

本轮明确了一个关键原则：

- planner 的设计中心不是“搜索后播放”
- 而是“根据用户需求生成推荐歌单，并可选择立即播放”

也就是说，推荐歌单生成应该作为独立核心能力设计，而不是附着在普通搜索逻辑上的一个分支。

### 从产品功能提炼出的 Agent 核心能力

根据现有 proposal / 选题分析，Agent 的高优先能力主要包括：

- 播放控制
- 歌曲 / 歌手查询
- 推荐歌单生成
- 推荐歌单历史版本访问
- 本地状态同步
- 对话上下文利用

### 推荐歌单 Planner 的关键设计

文档中已明确推荐歌单规划应按以下阶段设计：

1. 识别推荐目标
2. 提取显式约束
3. 读取近期聊天上下文
4. 读取长期用户偏好
5. 读取历史推荐歌单
6. 生成候选曲目集合
7. 对候选曲目进行排序与去重
8. 生成并保存推荐歌单
9. 如果用户要求，则立即开始播放
10. 生成最终回复

### 当前实现与目标的差距

本轮文档中也明确指出：

- 当前 `COMPOSITE_REQUEST` 里“搜索后取第一首再播放”的做法
- 只适合早期 playback demo
- 不适合作为推荐歌单 planner 的正式策略

因此，后续 planner 优化时应优先做：

1. 将推荐歌单相关 intent 从普通搜索 intent 中拆开
2. 为推荐歌单生成建立单独的 plan 分支
3. 在 plan 中显式接入：
   - `users.preferences`
   - `chat_messages`
   - `playlists / playlist_tracks`
   - 本地 session

### 当前作用

这两份文档的作用是：

- 先把 planner 的产品边界固定下来
- 避免后续直接围绕现有简化版实现做错误优化
- 为下一步真正重构 planner 提供基线

## Spotify Catalog Client 接入

### 本轮目标

在 bridge token 能工作的前提下，优先接入 Spotify 目录查询能力，不碰播放控制接口。

### 已完成内容

1. 新增 `SpotifyWebApiCatalogClient`
   - 已实现：
     - 根据 `trackId` 获取曲目信息
     - 根据 `artistId` 获取歌手信息
     - 根据关键字搜索歌曲

2. 调整 `MusicMetadataService`
   - 新增：
     - `findTrackOrFetch`
     - `findArtistOrFetch`
     - `searchTracks`
   - 行为改为：
     - 先查本地缓存（内存仓储 / 后续可替换为 MySQL + Redis）
     - miss 后再通过 bridge token 调 Spotify
     - 命中 Spotify 后回写本地缓存

3. 调整 `MusicQueryApplicationService`
   - 查询歌曲 / 歌手时，已优先走“本地缓存 + Spotify fallback”
   - 新增搜索歌曲能力

4. 调整 `MusicQueryController`
   - 新增接口：
     - `GET /api/music/search/tracks?q=...&limit=...`

### 当前边界

当前只实现了 catalog 方向：

- `get track`
- `get artist`
- `search tracks`

当前尚未实现：

- album 查询
- recommendations 查询
- top tracks / top artists
- playback 相关 Spotify HTTP 接口

### 架构收益

本轮完成后，目录查询已经形成完整链路：

```text
Controller
  -> Application Service
    -> MusicMetadataService
      -> SpotifyBridgeAuthService
      -> SpotifyCatalogClient
```

这条链路已经满足后续 Agent / Planner 查询歌曲和艺人信息的基础能力。

## Spotify Playback Client 与 Planner 执行接线

### 本轮目标

打通 bridge 模式下的播放控制链路，并让 Planner 的以下 intent 不再停留在占位层：

- `PLAYBACK_CONTROL`
- `COMPOSITE_REQUEST`

### 已完成内容

1. 新增 `SpotifyWebApiPlaybackClient`
   - 已实现：
     - 读取当前播放状态
     - 读取可用设备列表
     - 播放指定歌曲
     - 暂停播放
     - seek
     - 切换播放模式（shuffle / repeat）

2. 新增 bridge 播放内部模型
   - `SpotifyPlaybackState`
   - `SpotifyBridgeDevice`

3. 新增 `BridgePlaybackControlService`
   - 负责：
     - 用 bridge token 调用 Spotify playback API
     - 将结果同步写回本地 `sessions`

4. 扩展 `PlaybackApplicationService`
   - 新增：
     - `playTrack`
     - `pause`
     - `syncBridgeState`

### Planner 执行层变化

`DefaultTaskExecutor` 已不再只是占位回复。

当前行为：

1. `PLAYBACK_CONTROL`
   - 如果识别到暂停语义，则调用播放服务暂停
   - 否则尝试读取当前本地 / bridge 状态，并按解析出的播放模式继续播放

2. `COMPOSITE_REQUEST`
   - 先走 `MusicQueryApplicationService.searchTracks`
   - 选择第一首命中曲目
   - 再调用 `PlaybackApplicationService.playTrack`

### 当前效果

这意味着 Agent 已经具备最小闭环：

```text
自然语言请求
  -> Planner
    -> 搜索歌曲
    -> 调 bridge playback API
    -> 同步本地 session
    -> 返回执行结果
```

### 当前边界

本轮仍然没有做：

- next / previous
- transfer playback
- volume control
- 更细的设备选择策略
- 更强的多步 planner 参数抽取
- 失败重试和异常翻译

### 风险说明

当前 `COMPOSITE_REQUEST` 选择的是搜索结果的第一首曲目，这只是第一版最小可运行策略。

后续需要补：

- 更强的参数抽取
- 结果排序 / 置信度
- 结合用户偏好做候选选择

## Spotify Bridge Mode 与 Planner 骨架

### 已新增正式设计文档

已新增桥接账号模式的正式设计文档：

```text
agentmusic-backend/docs/spotify-bridge-mode-design.md
```

该文档明确了：

- 当前阶段采用“开发者 Spotify Premium 账号作为桥接账号”
- AgentMusic 用户不直接绑定自己的 Spotify 账号
- Spotify 侧的桥接账号资源不直接作为用户自己的资源暴露
- 用户可见的播放状态、历史歌单、偏好、聊天等仍以 AgentMusic 自己的 MySQL / Redis 为主
- Controller / Application Service / Domain Service / Spotify Client 的调用边界
- 后续从桥接模式迁移到多用户 Spotify 绑定模式的路径

### 对当前代码结构的检查结论

当前代码结构支持桥接模式，原因如下：

1. `controller` 已经与业务逻辑分离
2. `service.application` 已经存在，适合承接 Spotify 用例编排
3. `service` 层已经具备本地播放会话、歌单、聊天、元数据能力
4. `client` 层已经有 Spotify client 接口占位
5. 数据库设计本身就支持“本地用户状态”和“外部 Spotify 元数据缓存”分离

当前仍缺少但已明确边界的部分：

- `SpotifyAuthClient` 真实实现
- `SpotifyCatalogClient` 真实实现
- `SpotifyPlaybackClient` 真实实现
- bridge token 持久化与刷新
- 开发者桥接账号授权入口

### 已新增桥接模式配置基线

本轮新增了 `SpotifyBridgeProperties`，并在配置中补齐了桥接模式需要的参数：

- `spotify.bridge.enabled`
- `spotify.bridge.client-id`
- `spotify.bridge.client-secret`
- `spotify.bridge.redirect-uri`
- `spotify.bridge.system-user-id`
- `spotify.bridge.default-device-id`

同时已更新：

- `application.properties`
- `application-local.example.properties`

### 已新增 Planner 第一版骨架

本轮已新增 `planner` 包，先把 Agent 的规划层边界固定下来，包含：

1. 规划模型
   - `AgentIntent`
   - `PlanStepType`
   - `PlanStep`
   - `AgentPlan`
   - `PlanningContext`
   - `PlannerExecutionResult`

2. 规划接口
   - `TaskPlanner`
   - `TaskExecutor`

3. 默认实现
   - `SimpleTaskPlanner`
   - `DefaultTaskExecutor`

### 当前 Planner 的职责

当前版本还不是最终智能 Planner，而是“结构骨架”：

- 先做轻量 intent 分类
- 生成多步计划对象
- 生成占位执行结果

已经覆盖的 intent 类型包括：

- `CHAT_ONLY`
- `SEARCH_TRACK`
- `GET_ARTIST_INFO`
- `CREATE_PLAYLIST`
- `PLAYBACK_CONTROL`
- `COMPOSITE_REQUEST`
- `UNKNOWN`

### Agent 侧接线变更

`DefaultAgentApplicationService` 已改为：

1. 先写入用户消息
2. 构造 `PlanningContext`
3. 调用 `TaskPlanner`
4. 调用 `TaskExecutor`
5. 将计划结果写回聊天记录

这意味着 Agent 现在已经不再是单纯写死回复，而是开始走“规划 -> 执行 -> 回写”链路，只是执行逻辑目前仍是占位实现。

### 验证结果

本轮完成后再次执行：

```bash
mvn test
```

结果为：

```text
BUILD SUCCESS
```

## Spotify Bridge 授权与 Token 刷新

### 本轮目标

在桥接账号模式下，先打通 Spotify 授权和 token 生命周期的基础能力，不直接实现目录查询或播放控制 HTTP 接口。

### 已完成内容

1. 重构 `SpotifyAuthClient`
   - 不再是简单返回 access token 的占位接口
   - 现在明确包含：
     - 构建授权链接
     - 用授权码换 token
     - 用 refresh token 刷新 access token

2. 新增 `SpotifyWebApiAuthClient`
   - 基于 Spotify 官方 Authorization Code Flow
   - 使用 `POST https://accounts.spotify.com/api/token`
   - 使用后端保存的 `client_id` / `client_secret`

3. 新增 `SpotifyToken`
   - 统一表示 bridge account 的 token 结构

4. 新增 bridge token 存储
   - `SpotifyBridgeTokenRepository`
   - `InMemorySpotifyBridgeTokenRepository`

5. 新增 `SpotifyBridgeAuthService`
   - 负责生成授权链接
   - 校验 `state`
   - 处理 callback
   - 自动刷新将要过期的 access token
   - 对外提供“当前 bridge 授权状态”

6. 新增 `SpotifyBridgeAuthController`
   - `GET /api/auth/spotify/login`
   - `GET /api/auth/spotify/callback`
   - `GET /api/auth/spotify/status`

### 当前安全边界

当前 controller 不返回 bridge account 的原始 access token / refresh token。

对外只返回：

- 是否启用 bridge 模式
- 是否已授权
- system user id
- redirect uri
- 已授权 scopes
- token 到期时间

这样可以先满足后端调试和接线需要，同时避免把敏感 token 直接暴露给前端。

### 新增配置项

本轮新增 bridge 模式配置项：

- `spotify.bridge.enabled`
- `spotify.bridge.client-id`
- `spotify.bridge.client-secret`
- `spotify.bridge.redirect-uri`
- `spotify.bridge.system-user-id`
- `spotify.bridge.default-device-id`

并同步更新：

- `application.properties`
- `application-local.example.properties`

### 当前实现边界

当前已完成的是：

- bridge account 授权入口
- callback 处理
- token 刷新逻辑
- bridge 授权状态查询

当前尚未完成的是：

- 持久化保存 refresh token 到 MySQL
- 真正的 Spotify catalog API 调用
- 真正的 Spotify playback API 调用
- 将 Spotify playback 状态同步写入本地 `sessions`

### 验证结果

本轮完成后执行：

```bash
mvn test
```

结果为：

```text
BUILD SUCCESS
```

### 当前边界说明

当前后端层仍然刻意没有接入：

- HTTP Controller
- Spotify API Client
- MySQL 真正持久化实现
- Redis 真正缓存实现
- WebSocket
- Semantic Kernel Agent 编排链路

这样做的目的是先把后端结构和边界稳定下来，再讨论上层接口和外部依赖接法。

## Controller / Application Service / Spotify Client 骨架

### 本轮目标

在可运行后端层之上，继续补齐第一版 Web 分层骨架，但仍然不接真实 Spotify HTTP 调用。

本轮新增内容包括：

- 4 个 Controller
- 4 个 Application Service 接口及默认实现
- Spotify client 接口占位
- 控制器层需要的请求 DTO

### 已新增的 Controller

1. `AgentController`
   - 路径前缀：`/api/agent`
   - 当前接口：
     - `POST /api/agent/chat`
     - `GET /api/agent/history/{userId}`

2. `PlaylistController`
   - 路径前缀：`/api/playlists`
   - 当前接口：
     - `GET /api/playlists/{userId}`
     - `POST /api/playlists/{userId}`

3. `PlaybackController`
   - 路径前缀：`/api/playback`
   - 当前接口：
     - `GET /api/playback/{userId}/session`
     - `PUT /api/playback/{userId}/session`

4. `MusicQueryController`
   - 路径前缀：`/api/music`
   - 当前接口：
     - `GET /api/music/tracks/{trackId}`
     - `GET /api/music/artists/{artistId}`

### 已新增的 Application Service

1. `AgentApplicationService`
   - 负责 Agent 对话入口编排
   - 当前默认实现会：
     - 写入用户消息
     - 生成一条占位 Agent 回复
     - 回填最近播放状态和最近歌单

2. `PlaylistApplicationService`
   - 负责歌单相关用例编排
   - 当前接到底层 `PlaylistService`

3. `PlaybackApplicationService`
   - 负责播放会话相关用例编排
   - 当前接到底层 `PlaybackSessionService`

4. `MusicQueryApplicationService`
   - 负责音乐信息查询用例编排
   - 当前接到底层 `MusicMetadataService`

### 已新增的 Spotify client 接口占位

当前只定义边界，不做真实实现：

1. `SpotifyAuthClient`
   - 用于获取访问令牌

2. `SpotifyCatalogClient`
   - 用于歌曲/歌手查询与搜索

3. `SpotifyPlaybackClient`
   - 用于播放控制、暂停、seek、切换播放模式

这样做的目的是先把依赖方向固定下来：

```text
Controller
  -> Application Service
    -> Domain Service
      -> Spotify Client
```

避免后续把 Spotify HTTP 调用直接写进 Controller。

### 已新增的请求 DTO

新增了控制器层需要的请求对象：

- `CreatePlaylistRequest`
- `UpdatePlaybackSessionRequest`

### 当前状态说明

到这一轮为止，后端已经具备以下结构：

1. Domain / DTO / Redis key 基线
2. Repository 抽象 + 内存版实现
3. Service 层
4. Application Service 层
5. Controller 层
6. Spotify client 接口边界

但仍然没有接入：

- 真实 Spotify OAuth
- 真实 Spotify Web API HTTP 调用
- MySQL Repository 实现
- Redis Repository / Cache 实现
- Agent Planner 与 Plugin 的真正联动

### 验证结果

本轮完成后再次执行：

```bash
mvn test
```

结果为：

```text
BUILD SUCCESS
```
