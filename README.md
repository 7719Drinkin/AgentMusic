# AgentMusic

AgentMusic 是一个 Web 端 AI 增强音乐 Agent。项目目标不是简单复刻 Spotify，而是构建一个可以通过自然语言理解用户音乐需求、生成推荐歌单、展示音乐内容，并直接控制真实 Spotify 播放的智能音乐应用。

当前版本已经完成从用户对话到真实播放的核心闭环：

```text
用户请求
  -> LLM Planner / Harness 约束
  -> 推荐语义提炼
  -> Spotify 候选召回
  -> LLM 候选重排
  -> AgentMusic 歌单生成与持久化
  -> 前端展示
  -> Spotify Web Playback SDK 浏览器设备播放
```

## 当前完成情况

### AI Agent 与推荐链

- 支持 Agent Chat 对话入口。
- 接入 OpenAI-compatible LLM，目前本地配置使用 Kimi / Moonshot 兼容接口。
- 设计并实现 LLM Planning Harness，对 LLM 输出做结构化约束与后端校验。
- 推荐链采用“LLM 语义理解 + 确定性候选召回 + LLM 重排”的混合模式。
- 支持主题型推荐、歌手推荐、专辑边界、同名歌曲与歌手精确匹配等推荐链优化。
- 聊天回复支持流式输出。

### Spotify 集成与真实播放

- 后端通过 Spotify Bridge Mode 使用一个开发者授权的 Spotify Premium bridge account。
- 支持 Spotify 搜索、歌曲元数据、歌手信息、播放控制、设备同步。
- 前端底部播放器已接入 Spotify Web Playback SDK。
- AgentMusic 浏览器页面可以注册为真实 Spotify Connect 设备：`AgentMusic Web Player`。
- Music Home 和 Playlist Detail 的播放按钮会自动启用或复用 SDK 播放设备。
- 刷新页面后，在同一浏览器会话内可自动重连 SDK 播放设备。
- 后端保留播放编排权，继续负责 AgentMusic 本地歌单上下文、播放模式、下一首/上一首逻辑。

### 前端页面

- Agent Chat：自然语言对话、流式回复、推荐结果入口。
- Music Home：展示最近 Agent 推荐产生的歌曲、歌手、歌单、专辑。
- Playlist Detail：展示生成歌单、播放歌单、播放单曲、当前播放状态。
- Sidebar：展示推荐歌单列表，支持长列表滚动。
- Bottom Player：真实播放控制、进度条、音量、播放模式、设备面板。

### 持久化与运行状态

- 支持 MySQL + MyBatis 持久化用户、聊天、歌单、歌曲、播放会话。
- 支持 Redis 作为播放会话缓存。
- 支持内存模式用于早期开发和快速验证。
- 后端统一结构化错误码，减少前端按文本判断错误类型。

## 技术栈

### 后端

- Java 21
- Spring Boot 3.5
- Spring MVC / WebFlux
- MyBatis
- MySQL
- Redis
- Microsoft Semantic Kernel Java
- OpenAI-compatible Chat API
- Spotify Web API

### 前端

- React 18
- Redux
- React Router
- Vite
- TypeScript typecheck for JS project
- Spotify Web Playback SDK

## 本地运行

### 1. 前置依赖

- Java 21
- Maven
- Node.js / npm
- MySQL
- Redis
- Spotify Premium account
- Spotify Developer App
- OpenAI-compatible API key, for example Kimi / Moonshot

### 2. 后端配置

在 `agentmusic-backend/application-local.properties` 中配置本地私密参数。该文件不应提交到仓库。

示例：

```properties
agent.chat.live-llm-enabled=true
openai.api-key=your-api-key
openai.base-url=https://api.moonshot.cn/v1
openai.chat.model-id=moonshot-v1-8k

spotify.bridge.enabled=true
spotify.bridge.client-id=your-spotify-client-id
spotify.bridge.client-secret=your-spotify-client-secret
spotify.bridge.redirect-uri=http://127.0.0.1:8080/api/auth/spotify/callback
spotify.bridge.system-user-id=bridge-user
spotify.search.market=TW

agentmusic.persistence.mode=mysql
spring.datasource.url=jdbc:mysql://localhost:3306/agentmusic?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8
spring.datasource.username=root
spring.datasource.password=your-password
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

### 3. 启动后端

```powershell
cd agentmusic-backend
mvn spring-boot:run
```

首次使用 Spotify bridge mode 时，需要完成 bridge account 授权：

```text
http://127.0.0.1:8080/api/auth/spotify/login
```

Web Playback SDK 需要以下 Spotify scopes：

- `streaming`
- `user-read-private`
- `user-read-email`
- `user-read-playback-state`
- `user-read-currently-playing`
- `user-modify-playback-state`

如果后续新增 scope，需要重新进入 `/api/auth/spotify/login` 完成授权。

### 4. 启动前端

```powershell
cd agentmusic-frontend
npm install
npm run dev
```

默认访问：

```text
http://localhost:5173
```

## 验证方式

### 后端测试

```powershell
cd agentmusic-backend
mvn test
```

### 前端类型检查

```powershell
cd agentmusic-frontend
npx tsc --noEmit
```

### 手动 E2E 建议流程

1. 启动 MySQL、Redis、后端、前端。
2. 完成 Spotify bridge account 授权。
3. 打开 AgentMusic 前端。
4. 在设备面板启用 `AgentMusic Web Player`，或直接在 Music Home / Playlist Detail 点击播放。
5. 在 Agent Chat 输入推荐请求，例如“推荐 10 首 90 年代粤语歌并播放”。
6. 检查聊天回复、生成歌单、歌单详情、底部播放器、Spotify 播放状态。
7. 刷新页面，确认 Web Player 可以自动重连。
8. 测试播放、暂停、下一首、上一首、seek、播放模式切换。

## 关键设计文档

- `agentmusic-backend/docs/llm/planning-harness.md`：LLM 输入输出契约与 Harness 校验规则。
- `agentmusic-backend/docs/recommendation-search-chain.md`：推荐链职责分配、候选召回与 LLM 重排逻辑。
- `agentmusic-backend/docs/spotify-bridge-mode-design.md`：Spotify bridge mode 与 Web Playback SDK 设备模型。
- `agentmusic-backend/docs/web-playback-sdk-device-plan.md`：Web Playback SDK 设备实现记录。
- `agentmusic-backend/docs/frontend-controller-api.md`：前后端控制器 API 合约。
- `agentmusic-backend/docs/database/mysql-mybatis-design.md`：MySQL / MyBatis 持久化设计。
- `agentmusic-backend/docs/database/redis-design.md`：Redis 会话缓存设计。

## 当前限制

- 当前 Spotify 集成仍是单 bridge account 模式，不是每个用户绑定自己的 Spotify 账号。
- Spotify 播放依赖 Premium account 和 Spotify 官方 Web API / Web Playback SDK。
- SDK `device_id` 是浏览器会话级瞬时 ID，不应作为长期配置保存。
- LLM Agent 当前是受控 planner + executor 模式，还不是完全自主工具调用 Agent。
- 高频 Spotify 搜索可能触发 rate limit，需要在后续继续优化缓存、退避和候选召回策略。
