## 项目启动Proposal: AgentMusic
#### 1. 项目概述
AgentMusic 是一个 Web 端 AI 增强音乐软件，核心目标是打造一个“会思考、会聊天、能直接操控音乐”的智能音乐 Agent。
软件通过 Spotify Web API 获取歌曲、专辑、歌手信息与推荐，同时利用 Microsoft Semantic Kernel 构建智能 Agent，实现自然语言（可支持语音）控制音乐播放与推荐。

#### 2. 项目目标（按当前版本优先级收敛）

**priority 1**
1. 获取歌曲、专辑、歌手等基础音乐元数据
2. 中间主界面为 AI Agent 聊天框（支持文字输入）
   1. 输入自然语言指令
   2. 获取 Agent 自动生成的推荐歌单
3. 左侧栏展示 Agent 生成的历史推荐歌单
   1. 保留多版历史
   2. 可点击切换或作为播放入口
4. 底部固定控制栏功能
   1. 播放 / 暂停 / 上一首 / 下一首
   2. 播放模式切换（顺序 / 列表循环 / 单曲循环 / 随机）
   3. 进度条拖动与实时更新
   4. 音量控制条控制音量
   5. 当前曲目、歌手、封面等信息展示
5. Agent 能够根据用户自然语言指令自动执行上述核心操作

**priority 2**
1. AI Agent 支持语音控制/输入（浏览器 Web Speech API）
2. 歌词实时查看与高亮
3. 播放队列查看与管理（选择播放 / 添加 / 删除 / 清空）
4. 点击歌手名进入歌手信息页（Bio、热门歌曲、相关艺术家）
5. 设备列表展示与设备切换
6. 左 / 右侧栏支持一键收起 / 展开

**priority 3**
1. 用户个人 Spotify 账号绑定的完整产品化流程
2. 社区化与分享功能
3. 商业化账户体系与多端同步能力

#### 3. 技术栈与实现方案
**前端（React + TypeScript）**

- 直接复用并修改 https://github.com/oguz3/spotify-web-player 项目作为 UI 基础（从GitHub克隆仓库：`git clone https://github.com/oguz3/spotify-web-player.git`）。
  - 侧栏收放动画、响应式布局、歌词高亮、聊天框实时交互全部基于该仓库进行二次开发
  - 初始化流程（**待确认**）：
    - 进入仓库目录，运行`npm install`安装依赖；
    - 使用`npm start`启动开发服务器，测试UI；
    - 二次开发包括添加Agent聊天框组件（`ChatBox.js`）和侧栏动画（使用Framer Motion库，`npm install framer-motion`）
- spotify api: https://developer.spotify.com/documentation/web-api 
  - 注册Spotify for Developers 账户获取 Client ID/Secret，**到时注册一个一起使用即可**
  - 初始化：使用spotify-web-api-js库`npm install spotify-web-api-js`；在组件中创建Spotify实例并授权。
- 使用 Redux / Zustand 管理状态（**待确认**）
  - 从npm获取：`npm install redux react-redux` 或 `npm install zustand`；初始化：在`src/store.js`中创建`store`，导入到`App.js`

**后端**

- **1. Java + Spring Boot**
速度快、并发强、适合后续扩展成微服务
Semantic Kernel Java SDK 已进入正式版（2025 年已成熟）
  - **从哪里获得**：
    - 从Maven Central下载
      - 仓库URL：https://central.sonatype.com/artifact/com.microsoft.semantic-kernel/semantickernel-core
      - GitHub源代码：https://github.com/microsoft/semantic-kernel/tree/main/java
  - 初始化流程：使用Maven创建Spring Boot项目（**待确认**）
    - mvn archetype:`generate -DgroupId=com.agentmusic -DartifactId=agentmusic-backend -DarchetypeArtifactId=maven-archetype-quickstart`；
    - 在pom.xml添加依赖如
     ```
        <dependency>
            <groupId>
                com.microsoft.semantic-kernel
            </groupId>
            <artifactId>
                semantickernel-core
            </artifactId>
            <version>
                1.4.0
            </version>
        </dependency>
    ```
    - 运行mvn install构建；
      - 在主类中初始化Kernel：`Kernel kernel = Kernel.builder().build()`；
      - 添加OpenAI服务：`kernel.addChatCompletionService(new OpenAIChatCompletion.Builder().withApiKey("YOUR_OPENAI_KEY").build())`。
  - 使用 Spring WebFlux + Semantic Kernel Java 构建 Agent
    - `npm install spring-boot-starter-webflux`依赖；
    - 在Controller中暴露API端点，集成Planner。

- **2. FastAPI/Python**
开发更快，但性能稍逊。
  - **从哪里获得**：
    - 通过pip安装
      - `pip install fastapi uvicorn semantic-kernel spotipy`；
      - Semantic Kernel GitHub：https://github.com/microsoft/semantic-kernel/tree/main/python
  - 初始化流程（**待确认**）：
    - 创建main.py文件，导入from fastapi import FastAPI；`app = FastAPI()`；
    - 添加路由如`@app.post("/agent")`；
    - 安装Semantic Kernel：`pip install semantic-kernel`；
    - 初始化Kernel：`import semantic_kernel as sk; kernel = sk.Kernel()`；
    - 添加服务：
      - `from semantic_kernel.connectors.ai.open_ai import OpenAIChatCompletion`； `kernel.add_service(OpenAIChatCompletion("gpt-4", api_key="YOUR_OPENAI_KEY"))`；
    - 运行uvicorn main:`app --reload`启动服务器。


- **AI Agent 核心：Semantic Kernel Planner + Memory + Plugins**
**Planner** 负责解析自然语言 → 调用对应 native functions
  - 从Semantic Kernel SDK获取
    - 初始化：`kernel.importPluginFromObject(new SpotifyPlugin())`；
  - Memory 存储历史歌单和用户偏好
    - 初始化：`kernel.addMemoryStore(new VolatileMemoryStore())`；
    - 使用`await memory.saveInformationAsync()`
  
ps. **Planner**：
**Planner**是**Semantic Kernel**中的一个组件，它允许AI根据用户自然语言请求（goal），自动生成并执行一个“计划”（plan）——即函数调用的序列，而无需开发者手动编码每个步骤。书将Planner比作“智能调度器”，它使用底层LLM（如GPT模型）分析目标，决定调用哪些插件/函数（semantic或native），并处理条件/循环。
**也就是说**，在下载了**Semantic Kernel**之后，就可以直接使用**Planner**这个**组件**了。

**Java示例**
```Java
import com.microsoft.semantickernel.Kernel;
import com.microsoft.semantickernel.planner.sequentialplanner.SequentialPlanner;

// 假设kernel已创建（见下文初始化Kernel）
SequentialPlanner planner = new SequentialPlanner(kernel, null, null);  // 可传选项如maxIterations
```

**Python示例**
```python
from semantic_kernel.planners import FunctionCallingStepwisePlanner, FunctionCallingStepwisePlannerOptions

# 假设kernel已创建并添加插件
planner_options = FunctionCallingStepwisePlannerOptions(max_tokens=4000, max_iterations=10)  # 可调参数：令牌限、迭代限
planner = FunctionCallingStepwisePlanner(service_id="gpt-4", options=planner_options)  # 指定LLM服务
```
