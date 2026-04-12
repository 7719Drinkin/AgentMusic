# AgentMusic 开发笔记

版本：V2.0  
更新日期：2026-04-08

## 1. 文档目的

本文件保留项目开发过程中的有效结论、关键里程碑、当前实现状态与阻塞点。  
不再记录已经失效的初始化教学步骤、重复命令说明和冗长截图说明。

## 2. 当前项目基线

### 2.1 产品基线

当前版本的核心目标是验证以下闭环：

- 用户通过聊天表达音乐需求
- Agent 返回推荐或控制结果
- 系统保存推荐歌单历史
- 系统同步本地播放会话
- 前端通过聊天区、左侧歌单区、底部播放器展示结果

### 2.2 当前优先级基线

#### Priority 1

- 聊天主界面与文本输入
- 推荐歌单生成与历史歌单展示
- 曲目 / 歌手 / 搜索等音乐元数据查询
- 底部播放器状态同步
- 底部播放器基础控制：
  - 播放 / 暂停
  - 上一首 / 下一首
  - 播放模式切换
  - 进度拖动
- Agent 对上述能力的基础编排

#### Priority 2

- 设备列表展示
- 设备切换
- 歌词显示
- 播放队列管理
- 歌手详情页
- 语音识别完整链路
- 流式聊天输出
- MySQL / Redis 持久化替换内存实现

#### Priority 3

- 用户个人 Spotify 账号绑定产品化
- 社区能力
- 商业化账号体系
- 多端同步优化

## 3. 已完成里程碑

### 3.1 工程与配置整理

- 后端目录结构已整理为：
  - `controller`
  - `service`
  - `service.application`
  - `client`
  - `domain`
  - `dto`
  - `repository`
- Maven 已固定为项目内本地仓库与独立构建目录
- 敏感配置已改为本地配置/环境变量读取

### 3.2 后端基础能力

- 已落地领域模型、DTO、Redis key 基线
- 已完成 Repository 抽象与内存版实现
- 已完成 Service / Application Service / Controller 分层
- 已补齐基础 API 文档：
  - `agentmusic-backend/docs/frontend-controller-api.md`

### 3.3 Agent 与 Planner

- 已搭建 planner 骨架
- 已明确推荐歌单优先于普通搜索播放的规划方向
- 当前 `CHAT_ONLY` 仍以本地硬编码 / fallback 为主
- live LLM 分支已做成可开关，但当前受外部网络限制

### 3.4 Spotify 接入

- 已实现 bridge 模式配置基线
- 已实现 Spotify bridge 授权入口、callback、token 刷新
- 已修复 Spotify 授权 URL 的 `scope` 编码问题
- 已完成 catalog 查询能力：
  - 曲目查询
  - 歌手查询
  - 搜索歌曲
- 已完成 playback 控制基础链路骨架
- 当前播放控制仍受 bridge 账号授权和设备可用性影响

### 3.5 前端迁移与页面

- 已将参考前端迁移到新的 `agentmusic-frontend`
- 已建立双主页结构：
  - `/`：聊天主页
  - `/music`：音乐主界面
- 聊天页已改为简化单栏布局
- 左侧侧栏已开始接后端歌单
- 底部播放器已开始接后端播放会话

### 3.6 无 Premium 本地闭环

- 已新增《AgentMusic无Premium开发链路说明》
- 当前阶段以 `PlaybackSession` 作为播放器权威状态
- 已在后端会话中补充：
  - `currentPlaylistId`
  - `currentTrackIndex`
- 已完成本地 fallback：
  - 推荐默认写入本地播放会话
  - Spotify 调用失败时仍更新本地 session
  - 上一首 / 下一首可基于歌单上下文在本地切换

### 3.7 前端播放器稳定性

- 已显示真实歌手名而不是 `artistId`
- 已支持左侧推荐歌单点击后切入播放器上下文
- 已避免无 preview 音频时错误沿用旧音源
- 已补齐播放器状态边界：
  - 无曲目禁用播放
  - 无歌单上下文禁用上一首 / 下一首
  - 无时长禁用进度条
  - 请求进行中禁止重复点击

## 4. 当前验证结论

截至 2026-04-08，当前联调结果如下：

- 聊天区能够收到后端返回，但普通问候语当前仍返回：
  - `Planner skeleton ready. Intent=CHAT_ONLY...`
- 底部播放器的上一首 / 下一首在单曲测试歌单下已正确禁用
- 由于当前聊天推荐链路还未稳定进入真实推荐结果回写，暂未完成以下验证：
  - 左侧歌单自动刷新
  - 播放器自动切到推荐首曲

## 5. 当前主要阻塞点

### 5.1 聊天推荐链路仍未形成可验证闭环

- 聊天接口虽然已联通
- 但普通聊天当前仍走本地 skeleton/fallback
- 推荐生成与前端侧栏/播放器联动尚未完成稳定验证

### 5.2 Spotify 真实播放控制仍受外部条件影响

- bridge 账号授权尚未完成稳定验证
- 设备可用性尚未确认
- 即使后端代码具备链路，真实 Spotify 播放控制仍不应作为当前主线唯一依赖

### 5.3 持久化仍为过渡态

- 当前歌单、会话、聊天等仍存在内存实现
- MySQL / Redis 替换尚未开始主线推进

## 6. 当前推荐开发顺序

1. 打通“聊天推荐 -> 歌单刷新 -> 播放状态同步 -> 底部播放器回显”闭环
2. 验证并补齐推荐后左侧歌单与播放器联动
3. 明确推荐回显 UI 方案，再做聊天区回显优化
4. 再讨论侧栏歌单详情或选中态增强
5. 最后再进入：
   - 设备列表 / 设备切换
   - 真实 Spotify 控制验证
   - MySQL / Redis 替换
   - 流式输出 / 语音识别

## 7. 关联文档

- `AgentMusic需求分析说明书.md`
- `AgentMusic任务清单.md`
- `AgentMusic无Premium开发链路说明.md`
- `agentmusic-backend/docs/frontend-controller-api.md`

## 8. 2026-04-08：Spotify Web UI 对比结论

### 本轮目的

- 对照 `https://open.spotify.com/` 的播放器 UI
- 识别当前 AgentMusic 前端在底部播放器与当前播放栏上的缺口

### 结论

当前前端尚未实现以下 Spotify Web 常见播放器能力：

- 加入歌单入口
- 歌词入口
- 队列入口
- 迷你播放器入口
- 底部按钮 tooltip
- 曲名 / 歌手名点击行为
- 封面点击打开右侧当前播放栏

### 当前播放栏代码检查结果

检查 `App.jsx`、`home.jsx`、`chat.jsx` 和 `src/component/**` 后，当前前端代码中不存在独立成型的“右侧当前播放栏”组件。

后续若要实现该功能，需要新增：

1. 当前播放栏组件
2. 全局显隐状态
3. 与底部专辑封面点击行为的联动

### 文档

- 已新增对比与参考文档：
  - `AgentMusic-SpotifyUI对比与参考.md`

## 9. 2026-04-08：播放器 UI 细节补齐

本轮前端只处理 UI，不改现有链路逻辑，完成内容如下：

- 底部播放器控制按钮补齐 tooltip
- 曲名和歌手名改为可点击入口
- 专辑封面点击可切换右侧当前播放栏
- 新增右侧当前播放栏前端骨架
- 补齐歌词 / 队列 / 迷你播放器入口的 UI 壳

当前这些变更均停留在前端展示层，未引入新的后端依赖。

## 10. 2026-04-08：底部播放器 UI 纠偏

针对播放器细节补齐后的显示问题，本轮继续修正了以下内容：

- 删除底部左侧重复渲染的一组专辑封面/歌曲信息区域
- 调整底部三段式布局，恢复左中右区域的正确位置
- 将右侧弹出面板明确收敛为“当前播放栏”
- 将迷你播放器降级为 Priority 2 占位入口，不再复用当前播放栏
- 统一底部播放器主要图标风格，保持与歌词/队列图标风格一致
- 将底部播放器图标尺寸缩小到约原来的 60%，并恢复侧栏 Liked 图标的旧样式
- 修正 playlist 页面悬浮播放按钮中播放图标过大的问题，使其与暂停图标尺寸一致
## 11. 2026-04-11：右侧当前播放栏布局修正

针对右侧“当前播放栏”与参考 UI 不一致的问题，本轮完成：

- 将顶部标题区从滚动内容中拆出，固定在右侧栏上方
- 将右侧栏正文改为独立滚动内容区
- 调整右侧栏高度计算方式，使其底部以上方播放器为边界
- 保持右侧栏封面为圆角正方形

当前状态：

- 代码结构已完成“固定头部 + 可滚动内容区”的分离
- 前端 `npm run build` 通过
- 页面最终视觉效果仍需在浏览器中继续确认

## 12. 2026-04-11：Playwright 前端检查入口

为后续直接检查前端页面，已在前端工程中补充 Playwright 最小检查骨架：

- `agentmusic-frontend/scripts/inspect-ui.js`
- `agentmusic-frontend/scripts/inspect-current-playing.js`
- `agentmusic-frontend/package.json`
  - `npm run inspect:ui`
  - `npm run inspect:current-playing`

当前结论：

- Playwright 依赖已安装
- 本地 `npm run dev` 服务可读，地址为 `http://localhost:5173/`
- 但在当前会话沙箱中启动 Chromium 会报 `spawn EPERM`
- 因此这套脚本目前更适合在用户本机终端直接执行，用于生成截图和检查页面

## 13. 2026-04-11：右侧当前播放栏底部安全区继续修正

针对“队列中的下一首”区域仍被底部播放器遮挡的问题，本轮继续收敛右侧栏的底部布局策略：

- 将右侧“当前播放栏”的底部预留高度收敛为统一的 `footer safe height`
- 当前播放栏不再使用过小的底部固定值，而是按统一安全区计算高度与底边距
- 该约束已同步写入 `AgentMusic-SpotifyUI对比与参考.md`

当前目标：
- 确保右侧栏所有内容，包括“队列中的下一首”，都不会被底部播放器遮挡

## 14. 2026-04-11：右侧栏底边改为跟随播放器真实高度

在继续调试右侧“当前播放栏”时，发现单纯靠固定的底部安全区常量会在不同布局状态下出现两类问题：

- 预留过小：右侧栏内容被底部播放器遮挡
- 预留过大：右侧栏与底部播放器之间出现空隙，同时可用内容区被压缩

因此本轮改为：

- 由底部播放器在运行时测量自身真实高度
- 将测量值写入统一的 CSS 变量 `--footer-safe-height`
- 右侧栏按该真实高度计算底边位置

目标约束：
- 右侧栏底边应与底部播放器上边严格对齐

## 15. 2026-04-11：右侧栏内部滚动区补底部缓冲

在将右侧栏底边与播放器上边对齐后，仍发现最后一张“队列中的下一首歌”卡片存在显示不完整的问题。

本轮继续修正为：

- 在右侧栏内部滚动容器增加底部 padding
- 补充 `scroll-padding-bottom`
- 让最后一张卡片在滚动到底时仍有可见缓冲区

这说明右侧栏问题需要分成两层处理：

1. 外层面板底边与播放器上边对齐
2. 内层滚动区为末尾内容预留足够显示空间

## 16. 2026-04-11：推荐搜索查询提炼修正

针对“给我来点轻松的粤语歌，随机播放”这类自然语言推荐请求，后端之前会将整句话原样传递给 Spotify 搜索和本地 demo fallback，导致搜索结果为空，最终回复 `No suitable tracks were found for the current recommendation request.`

本轮修正：

- 新增 `SearchQueryRefiner`
  - 将自然语言推荐请求提炼为更可用的搜索候选词，例如将“给我来点轻松的粤语歌，随机播放”提炼为“轻松 粤语”等候选查询
- `DefaultMusicMetadataService.searchTracks(...)`
  - 改为对每个候选查询词依次尝试 Spotify 搜索
  - 若 Spotify 返回为空，再依次尝试本地 `DemoMusicCatalog` fallback
- `DemoMusicCatalog`
  - 搜索评分由精确匹配扩展到包含关系匹配，提高自然语言查询命中率
- 新增测试
  - `SearchQueryRefinerTests`
  - `MusicMetadataSearchIntegrationTests`

当前结果：

- 运行中的 `/api/agent/chat` 已成功返回
  - `intent = PLAY_RECOMMENDATION`
  - 生成 2 首 demo fallback 推荐歌曲
  - 同时写入 `PlaybackSession`
- 这说明当前阻塞点已经由“推荐搜索阶段不能把自然语言请求转成有效查询”转为“可以继续验证前端歌单刷新和播放器同步”

## 17. 2026-04-11：去除后端 demo fallback 与前端静态业务数据依赖

本轮按“代码保持可控、不再堆叠 mock / demo / sample data”的原则，执行了一次清理重构。

后端清理：

- 删除 `DemoMusicCatalog`
- 从 `DefaultMusicMetadataService` 中移除 demo fallback
- 删除基于 sample data / demo fallback 的测试：
  - `AgentControllerApiTests`
  - `MusicMetadataSearchIntegrationTests`
- 保留：
  - `planner / executor` 骨架
  - `SearchQueryRefiner`
  - Spotify bridge 与真实 service 分层

前端清理：

- 删除运行时静态业务数据文件 `src/data/index.jsx`
- 底部播放器不再回退到本地静态歌单切曲
- store 初始播放状态不再从静态歌曲初始化
- 当前播放栏与队列不再从本地静态歌单推导
- `home / library / playlist / search` 页面改为 UI 骨架占位，不再读取静态歌单或分类数据
- 删除已不再被运行路径引用的旧歌单卡片与旧歌单详情组件

当前结果：

- 前端运行时主路径已不再依赖静态歌单 / 静态歌曲数据
- 后端运行时主路径已不再依赖 demo 曲库 fallback
- 后续联调将更真实地暴露“真实接口是否可用、Spotify 是否可用、Agent 是否真的在工作”
## 2026-04-11 补充：Spotify bridge 持久化与搜索链路修正

- 将 Spotify bridge token 仓储从纯内存实现改为本地文件持久化，解决后端重启后 `authorized=false` 的问题。
- 持久化文件路径统一为 `agentmusic-backend/.spotify-bridge-token.properties`，并加入 `.gitignore`。
- 修正 `SpotifyWebApiCatalogClient` 的搜索 URI 构造方式，避免中文查询参数在 `/search` 请求中编码异常。
- 当前阶段的真实验证顺序调整为：
  1. 启动后端
  2. 完成一次 Spotify bridge 授权
  3. 直接验证 `/api/music/search/tracks`
  4. 搜索成功后再回到聊天推荐闭环联调
## 2026-04-12 补充：第一轮真实数据回填完成

- 当前播放栏已不再依赖静态 helper 返回固定标题。
- 当前播放栏现在会根据 `currentPlaylistId` 读取真实歌单详情，并显示真实歌单名称。
- 当前播放栏会根据歌单详情与艺人详情回填：
  - 当前曲目
  - 当前艺人
  - 歌曲提供者区域的 Main Artist
  - 队列中的下一首歌
- Playwright 已用于实际页面验证，确认：
  - 右侧当前播放栏显示真实歌单名
  - “队列中的下一首歌”卡片已显示真实曲目与艺人
- 下一阶段主任务切换为：
  1. 真实 Spotify 播放控制
  2. 左侧推荐歌单进入真实歌单页
  3. MySQL / Redis 持久化排期
