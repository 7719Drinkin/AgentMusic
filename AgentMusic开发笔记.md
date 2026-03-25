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
