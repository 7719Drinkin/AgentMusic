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

## 2026-04-02：底部播放器歌手名与 SRS 追踪表补充

### 本轮目标

1. 将底部播放器展示的 `artistId` 替换为真实歌手名
2. 在 SRS 说明书中补充“已实现 / 未实现 / 待验证”的需求追踪表
3. 明确将“设备列表展示”和“设备切换”调整为 Priority 2

### 前端改动

- 更新底部播放器会话同步逻辑：
  - 在读取播放会话中的 `currentTrackId` 后，先查询曲目详情
  - 若曲目包含 `artistId`，继续查询歌手详情接口
  - 以真实歌手名填充播放器左侧显示区域
- 修改文件：
  - `agentmusic-frontend/src/api/music.js`
  - `agentmusic-frontend/src/component/footer/footer.jsx`

### 文档改动

- 重写并规范化《AgentMusic需求分析说明书》编码与结构
- 在说明书中新增需求追踪表，标记当前需求状态：
  - 已实现
  - 部分实现
  - 未实现
  - 待验证
- 在需求优先级中明确：
  - 设备列表展示：Priority 2
  - 设备切换：Priority 2

### 验证

前端执行：

```bash
npm run build
```

预期结果：

```text
build success
```

## 2026-04-02：底部播放器状态边界补稳

### 本轮目标

1. 在无当前曲目时禁用不应可用的控制按钮
2. 在请求进行中阻止重复点击，避免状态抖动
3. 在无音源、无封面、无可拖动进度时提供稳定兜底行为

### 前端改动

- 底部播放器增加本地忙碌状态 `isPlaybackBusy`
- 以下按钮按状态禁用：
  - 无曲目时禁用播放/暂停
  - 无歌单上下文时禁用上一首/下一首
  - 无曲目时禁用模式切换
  - 无可用时长时禁用进度条拖动
- 请求发起期间统一禁用重复点击，等待接口返回后恢复
- `<audio>` 在无可播放音源时主动暂停
- 封面图在缺失时回退到默认占位图
- 进度条组件与控制按钮组件增加 `disabled` 状态样式

### 修改文件

- `agentmusic-frontend/src/component/footer/footer-left.jsx`
- `agentmusic-frontend/src/component/footer/footer.jsx`
- `agentmusic-frontend/src/component/footer/player/music-control-box.jsx`
- `agentmusic-frontend/src/component/footer/player/music-control-box.module.css`
- `agentmusic-frontend/src/component/footer/player/music-progress-bar.jsx`
- `agentmusic-frontend/src/component/footer/range-slider.jsx`
- `agentmusic-frontend/src/component/footer/range-slider.module.css`

### 验证

前端执行：

```bash
npm run build
```

结果：

```text
build success
```

## 2026-04-02：无 Premium 播放闭环前端联动补齐

### 本轮目标

1. 让左侧推荐歌单具备播放入口
2. 避免播放器在无 preview 音频时错误沿用旧音源
3. 让后端 `session` 中的进度更准确回显到前端音频组件

### 前端改动

- 左侧推荐歌单列表改为可点击：
  - 点击某个推荐歌单后，取首曲并调用 `/api/playback/{userId}/play`
  - 同时带上：
    - `playlistId`
    - `trackIndex`
  - 触发底部播放器刷新事件
- 播放器会话同步逻辑调整：
  - 若后端曲目无 `previewUrl`，不再回退到旧音源
  - 避免页面显示新曲目、实际仍播放旧测试音源
- 增加会话进度对 `<audio>` 的反向同步：
  - 当前端收到新的 `currentPositionMs` 时，主动更新音频组件播放位置

### 修改文件

- `agentmusic-frontend/src/component/sidebar/playlist.jsx`
- `agentmusic-frontend/src/component/sidebar/playlist.module.css`
- `agentmusic-frontend/src/component/footer/footer.jsx`
- `agentmusic-frontend/src/component/footer/audio.jsx`

### 验证

前端执行：

```bash
npm run build
```

预期结果：

```text
build success
```

## 2026-04-02：Spotify 授权 scope 编码修复

### 问题现象

- 访问 `/api/auth/spotify/login` 时后端返回 500
- 错误原因为 Spotify 授权 URL 中的 `scope` 参数包含空格，但未经过合法编码
- 导致授权流程未跳转到 Spotify，`/api/auth/spotify/status` 一直为 `authorized=false`

### 修复内容

- 修改 `SpotifyWebApiAuthClient` 的授权 URL 构造方式
- 由 `UriComponentsBuilder` 负责对 query 参数进行标准编码
- 避免 `scope` 中的空格以非法字符形式进入 URL
- 新增最小单元测试，验证授权地址中的 `scope` 参数已被编码

### 修改文件

- `agentmusic-backend/src/main/java/com/agentmusic/agentmusic_backend/client/spotify/SpotifyWebApiAuthClient.java`
- `agentmusic-backend/src/test/java/com/agentmusic/agentmusic_backend/SpotifyWebApiAuthClientTests.java`

### 验证

后端执行：

```bash
mvn test
```

预期结果：

```text
BUILD SUCCESS
```

## 2026-04-02：无 Premium 可验证播放闭环第一阶段

### 本轮目标

1. 将“无 Premium 可验证版本”的开发链条固化为项目文档
2. 让推荐结果默认写入本地 `PlaybackSession`
3. 为本地上一首/下一首补齐歌单上下文
4. 在 Spotify 调用失败时，播放器仍可退回本地 `session` 闭环

### 文档新增

- 新增《AgentMusic无Premium开发链路说明》：
  - 明确当前阶段的权威状态源是 `PlaybackSession`
  - 明确当前主路径是：
    - 聊天推荐
    - 歌单刷新
    - 本地播放状态同步
    - 底部播放器展示与本地控制

### 后端改动

- 扩展 `PlaybackSession` / `PlaybackSessionDto`：
  - `currentPlaylistId`
  - `currentTrackIndex`
- 推荐播放路径现在会将：
  - 推荐歌单 ID
  - 首曲索引
  一并写入本地播放会话
- `PlaybackApplicationService` 新增本地 fallback 逻辑：
  - 当 Spotify bridge 调用异常时
  - 播放、暂停、seek、模式切换仍更新本地 `session`
- 为上一首/下一首新增本地 fallback：
  - 基于当前 `currentPlaylistId` 和 `currentTrackIndex`
  - 从歌单上下文内切换当前曲目

### 前端改动

- 底部播放器 Redux 状态新增：
  - `currentPlaylistId`
  - `currentTrackIndex`
- 播放器发起播放请求时，会把当前歌单上下文一起传给后端

### 接口文档同步

- 更新 `frontend-controller-api.md`
- 明确 `PlaybackSessionDto` 与 `POST /api/playback/{userId}/play` 的新增字段

### 验证

后端执行：

```bash
mvn test
```

结果：

```text
BUILD SUCCESS
```

前端执行：

```bash
npm run build
```

结果：

```text
build success
```

## 2026-03-25 Chat page bottom boundary adjustment

### 调整内容

针对聊天页与全局底部播放器栏重叠的问题，补充了一条新的前端布局约束：

- Agent chat 页面底部边界必须以下方播放器栏的上边界为准
- 聊天消息滚动区域和底部输入框都不能落到播放器栏后面
- 该约束同时适用于空聊天状态和已有消息状态

### 实现方式

- 在聊天页样式中引入播放器栏占位高度变量
- 聊天页整体增加底部占位
- 对话态主布局高度改为 `视口高度 - 顶部导航高度 - 播放器栏占位高度`

### 结果

- 有消息时，底部输入框不会再被播放器栏遮挡
- 聊天消息列表的可滚动终点位于播放器栏上方

## 2026-03-26 Agent chat frontend-backend integration

### 本轮目标

先打通第一条真实联通链路：

- 前端聊天页
- `/api/agent/history/{userId}`
- `/api/agent/chat`

### 后端调整

- 新增开发期 CORS 配置，允许前端开发服务器访问 `/api/**`
- 新增配置项 `app.cors.allowed-origins`

### 前端调整

- 新增前端 API 请求封装
- 聊天页启动时读取聊天历史
- 发送消息时调用后端 `POST /api/agent/chat`
- 保留现有聊天输入交互：回车发送、Shift+回车换行、输入框自动增高
- 发送失败时在聊天流中直接显示错误反馈

### 当前结果

- Agent 聊天页已不再依赖纯前端 mock 消息
- 前后端第一条真实业务链路已具备联通条件
- 下一步应继续接：
  - 左侧歌单区 -> `/api/playlists/{userId}`
  - 底部播放器状态 -> `/api/playback/{userId}/session`

## 2026-03-26 Minimal player dataset and optional live LLM branch

### 前端静态数据收缩

- 将旧前端静态歌单数据缩减为最小测试集
- 删除大部分第三方 mp3 直链
- 保留一个 Spotify preview 链接作为底部播放器测试音源

这样做的目的：

- 避免页面启动时继续请求大量不稳定的旧音频地址
- 在未接入 Spotify bridge 播放控制前，保留最小播放器联调能力

### CHAT_ONLY 的真实 LLM 分支

- 为 `CHAT_ONLY / UNKNOWN` 增加了一个可开关的真实 LLM 回复分支
- 默认关闭，不会在日常开发中消耗 token
- 配置项：
  - `agent.chat.live-llm-enabled`
  - 默认值：`false`

### 当前行为

- 默认情况下：
  - 仍然走本地硬编码 / planner skeleton 逻辑
- 手动开启 `agent.chat.live-llm-enabled=true` 时：
  - `CHAT_ONLY / UNKNOWN` 会尝试请求 OpenAI Chat Completions
  - 失败时回退到本地提示文案

### 验证情况

- 前端 `npm run build` 通过
- 后端 `mvn test` 通过
- 由于当前本机到 `api.openai.com:443` 的直连请求被网络层阻断，未能在本轮内完成实时在线验证
- 代码路径已接好，后续可在本地重启 Spring Boot 后，临时开启配置打一条聊天请求进行验证

## 2026-03-26 Sidebar playlist integration

### 已实现内容

- 左侧推荐歌单区开始通过前端 API 调用后端 `GET /api/playlists/{userId}`
- 默认用户先固定为 `demo-user`
- 聊天页在收到后端推荐歌单结果后，会触发一次侧栏歌单刷新

### 当前范围

- 本轮只先实现“后端歌单历史 -> 前端侧栏列表”
- 暂未改造歌单详情页去读取后端 playlist DTO
- 暂未将播放控制和后端会话状态接入底部播放器

### 结果

- 基本功能开发已开始从“聊天页联通”进入“推荐歌单联通”
- 下一步优先继续接底部播放器的后端会话状态

## 2026-03-26 Footer playback session integration

### 本轮范围

- 底部播放器开始读取后端 `GET /api/playback/{userId}/session`
- 若 session 中存在 `currentTrackId`，再请求 `GET /api/music/tracks/{trackId}`
- 将返回的曲目预览地址、曲名、封面、播放状态同步到前端播放器状态

### 当前策略

- 保留现有播放器 UI 和本地控制壳
- 后端 session 数据优先
- 静态测试音源继续作为兜底数据
- 聊天页若收到 `session`，会触发底部播放器刷新

### 结果

- 底部播放器已开始从“纯静态前端数据”向“后端真实播放会话”过渡
- 下一步继续接：
  - 播放/暂停/模式切换 -> 后端 playback API
  - 进度拖动 -> 后端 playback API

## 2026-04-02 Footer playback control wiring

### 后端新增控制接口

为底部播放器补齐了以下 playback controller 接口：

- `POST /api/playback/{userId}/play`
- `POST /api/playback/{userId}/pause`
- `POST /api/playback/{userId}/next`
- `POST /api/playback/{userId}/previous`
- `POST /api/playback/{userId}/seek`
- `POST /api/playback/{userId}/mode`
- `POST /api/playback/{userId}/sync`

同时将 application service / bridge playback service / Spotify playback client 一并接通。

### 前端接入范围

底部播放器当前已将以下操作接到后端 playback API：

- 播放 / 暂停
- 上一首 / 下一首
- 进度拖动 seek
- Shuffle 切换
- 循环模式切换

### 当前策略

- 若当前播放器状态来自后端 session，则优先走后端控制链路
- 若当前仍是本地静态测试音源，则保留前端本地交互作为兜底
- 这样可以在 Spotify 设备可用时直接验证真实播放器控制，也不会阻塞静态联调

### 验证

- `mvn test` 通过
- `npm run build` 通过

## 2026-03-26 Local config loading fix for live LLM switch

### 问题

在 `application-local.properties` 中设置：

- `agent.chat.live-llm-enabled=true`

但运行中的聊天接口仍返回 `planner-skeleton`。

### 处理

- 将 `spring.config.import` 扩展为同时支持：
  - 从 `agentmusic-backend` 目录启动
  - 从项目根目录启动
- 将 `agent.chat.live-llm-enabled` 改为基础默认值 `false`
- 允许 `application-local.properties` 直接覆盖该值

### 结果

- 本地私有配置中的 `agent.chat.live-llm-enabled=true` 现在能更稳定地被 Spring Boot 读取
- 后续验证 live LLM 时，只需修改本地配置并完全重启后端即可

### 额外诊断支持

- 新增 `GET /api/agent/runtime-status`
- 同时在聊天回复 `metadata` 中增加：
  - `liveLlmEnabledConfigured`
  - `openAiKeyPresent`
  - `openAiModelId`
  - `liveLlmAvailable`

这样可以直接判断当前运行实例是否真正读取到了 live LLM 配置和 OpenAI key。

### OpenAI key 显式环境变量通道

根据运行时诊断结果：

- `liveLlmEnabledConfigured=true`
- `openAiKeyPresent=false`

说明运行实例已经读到 live LLM 开关，但没有成功读到 `openai.api.key`。

为避免继续依赖本地文件导入路径，本轮补充：

- `openai.api.key=${OPENAI_API_KEY:}`

这样可以直接通过环境变量 `OPENAI_API_KEY` 为后端提供 key。

## 2026-04-02 SRS document baseline

### 本轮新增

- 在项目根目录新增正式需求分析文档：
  - `AgentMusic需求分析说明书.md`

### 文档目标

- 严格按 SRS 思路整理项目需求
- 明确目标、边界、角色、场景、异常流程、约束、验收标准与变更机制
- 为后续开发与课程验收提供统一基线

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

## 2026-03-25 Frontend migration strategy adjustment

- Adjusted the frontend migration strategy from "redesign while migrating" to "high-fidelity migration first".
- Moved the reference frontend `src` and `public` into `agentmusic-frontend` to preserve its original structure and style direction.
- Kept the current step focused on runtime adaptation for Vite + React 18 instead of immediate product-level UI modification.
- Product-specific redesign will be done only after the reference-style frontend is fully migrated and buildable.

## 2026-03-25 Frontend high-fidelity migration completed

- Completed the high-fidelity migration of the reference frontend into `agentmusic-frontend`.
- Retained the original page structure, Redux state shape, route layout, CSS modules, and static assets.
- Adapted the runtime for the new workspace by:
  - switching the entry point to a Vite-compatible `main.jsx`
  - adding the required React Router / Redux dependencies
  - normalizing JSX-bearing source files from `.js` to `.jsx`
- Verified that the migrated frontend now passes:
  - `npm run build`
- The next frontend step should start from this migrated baseline and then apply AgentMusic-specific redesign work.

## 2026-03-25 Frontend React 18 effect compatibility fix

- Fixed the migrated reference frontend crash under `npm run dev`.
- Root cause:
  - `Sidebar` returned `false` from `useEffect` when drag-resize was inactive
  - `useWindowSize` returned `false` from `useEffect` in the non-client guard path
- Adjusted both effects so they return either a cleanup function or `undefined`, which is required by React.
- Also added explicit dependencies to the `Sidebar` resize effect to avoid unstable repeated registrations.

## 2026-03-25 Frontend API doc and dual-home layout

- Added a controller API integration document for frontend usage:
  - `agentmusic-backend/docs/frontend-controller-api.md`
- Established a maintenance rule that every controller change must update the API doc in the same change set.
- Changed the frontend primary route to a new chat-first page:
  - `/` -> Agent chat main page
  - `/music` -> migrated music main page
- Added the first batch of AgentMusic-specific UI elements on the chat page:
  - chat message area
  - message composer
  - voice-input button placeholder
  - recommendation preview card
  - playlist history card
  - current playback information card
  - feature coverage card

## 2026-03-25 Chat page simplification pass

- Removed the right-side information panel from the Agent chat page.
- Reworked the chat page into a single-column layout closer to a standard chat product interaction model.
- Empty state behavior:
  - composer is centered in the page
  - starter prompt cards are shown under the centered composer
- Active conversation behavior:
  - messages render in a vertical stream
  - composer moves to the bottom area
  - voice-input button remains available next to the send action
- The music main page remains available through the separate `/music` route and navigation entry.

## 2026-03-25 Chat interaction polish and visible text normalization

- Improved the chat page interaction model:
  - message list now lives in its own vertical scroll container
  - new messages auto-scroll to the bottom
  - mouse wheel scroll now drives the chat stream when content exceeds the viewport
  - `Enter` sends the message
  - `Shift + Enter` inserts a newline
  - the textarea starts as a single line and auto-expands with content up to a max height
- Replaced the plain text send and voice controls with compact icon-style buttons:
  - voice button uses the sound icon
  - send button uses the upward arrow
  - hover tooltips were added for both controls
- Normalized currently visible UI copy into simplified Chinese for:
  - sidebar playlist area
  - top navigation profile text
  - search placeholder
  - music home page section titles
  - library page tab titles

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
