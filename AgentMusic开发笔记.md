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
