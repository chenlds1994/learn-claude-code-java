# Java 开源项目 1 周学习计划(面向 Java 基础人群)

> **样本项目**:learn-claude-code-java(Claude Code 风格 Agent 的 Java 渐进式实现)
> **适用人群**:熟悉 Java 语法 / 集合 / 多线程,了解 Spring 基本概念,缺乏大型项目经验
> **总周期**:1 周(7 天),每天 2~3 小时,合计约 17~21 小时
> **核心方法论**:先理解项目做什么 → 再理解整体架构 → 最后深入核心代码

---

## 学习心法(开始前必读)

| 原则 | 通俗解释 |
|------|---------|
| **不要一上来就读 main / 初始化 / 工具类** | 入口类往往充满装配细节,会让你误以为项目很复杂,核心逻辑其实在别处 |
| **先抓主线,后看细节** | 一条核心流程走通,胜过逐行通读十个文件 |
| **跳过你目前看不懂的部分** | 并发、泛型边界、反射代理等高级用法,第一遍标记跳过,Day 5 再回头 |
| **每读完一个模块,用自己的话复述一遍** | 能复述 = 真懂;复述不出来 = 还没懂 |
| **能跑起来 > 能读懂** | 跑起来改一改,比纯阅读印象深刻 10 倍 |

### 初学者应"暂时忽略"的内容清单

| 暂时忽略的内容 | 所在位置 | 为什么先跳过 |
|---------------|---------|-------------|
| 复杂并发与线程安全细节 | `BackgroundManager`、`MessageBus` 的并发控制 | 只需知道"它异步执行",不必深究线程模型 |
| JSON 序列化的边界 case | `JsonUtils`、`model/` 下的 DTO 注解 | 只需知道"它负责对象 ↔ JSON 转换" |
| 路径安全校验细节 | `WorkspacePaths` 的 `../` 防逃逸逻辑 | 知道"它管路径"即可,安全细节 Day 5 再看 |
| worktree 的 Git 操作细节 | `WorktreeManager` 的 lane 生命周期 | 只需理解"目录级隔离"这个概念 |
| 前端项目 `web/` | `web/src/**` | 展示用前端,与 Java 后端学习无关 |
| 自动装配 / SPI 机制(本项目无) | — | 本项目未用 Spring,学其他 Spring Boot 项目时同样先跳过 |

---

## 7 天总览表

| 天次 | 阶段 | 核心任务 | 预计时长 |
|------|------|---------|---------|
| Day 1 | 宏观认知(What & Why) | 读 README、跑起来、一句话说清项目用途 | 2~3h |
| Day 2 | 整体架构(Big Picture) | 画模块关系图、识别 2 条技术主线 | 2~3h |
| Day 3 | 关键路径 · 上(入口→装配→配置) | S01 → Launcher → AppContext → StageConfig | 2~3h |
| Day 4 | 关键路径 · 下(主循环→模型客户端) | AgentRuntime 主循环 → AnthropicClient | 3h |
| Day 5 | 局部深挖(Deep Dive) | 选一个模块精读 + 设计模式对照 | 2~3h |
| Day 6 | 动手验证(Learning by Doing) | 改配置 / 加日志 / 加工具 | 2~3h |
| Day 7 | 提交 PR + 总结 | 写测试、提一个小 PR、复盘 | 2~3h |

---

## Day 1:宏观认知(What & Why)

> **阶段目标(一句话)**:能用一句话向别人说明"这个项目是干什么的,没有它会怎样"。

### 核心学习任务

| 序号 | 任务 | 具体动作 | 预计时长 |
|------|------|---------|---------|
| 1.1 | 读 README | 通读 `README.md`,重点看"适合谁""能学到什么""整体运行流程"三节 | 45min |
| 1.2 | 搞懂领域概念 | 查阅理解 3 个关键词:**LLM API 调用**、**Agent 闭环**、**Tool Use**。明白"Agent ≠ 聊天机器人" | 30min |
| 1.3 | 把项目跑起来 | 配置 `.env`(填 `ANTHROPIC_API_KEY` / `MODEL_ID`),执行 `mvn compile` 后跑 `S01AgentLoop` | 45min |
| 1.4 | 观察一次完整运行 | 在终端输入简单任务(如"列出当前目录文件"),观察模型如何分步调用工具 | 30min |
| 1.5 | 对比同类项目 | 浏览参考项目 [shareAI-lab/learn-claude-code](https://github.com/shareAI-lab/learn-claude-code) 原版,对比 Java 版差异 | 15min |

### 一句话总结练习(完成后你应该能说出)

> "这个项目用 Java 实现了一个 Claude Code 风格的 Agent——**大模型负责决策,本地 Java 代码负责执行工具(读写文件、跑命令),循环直到任务完成**。没有它,你只能和大模型聊天,不能让大模型真正操作你的电脑。"

### 推荐重点关注的文件

- `README.md`(第 1~117 行:项目定位、运行流程、学习顺序)
- `.env.example`(环境变量样例)
- `pom.xml`(看依赖:只有 dotenv-java + Jackson,极简)

### 常见卡点 + 简化阅读策略

| 卡点 | 简化阅读策略 |
|------|-------------|
| 不懂 LLM / Agent,README 读不进去 | 先看 README 第 49~66 行的"Claude Code 风格 Agent 到底是什么",就 3 句话,看懂再往下 |
| `.env` 配置后跑不起来 | 检查 `ANTHROPIC_API_KEY` 是否有效、`ANTHROPIC_BASE_URL` 是否指向兼容服务;暂时跑不起来也没关系,Day 2 先读代码 |
| 不理解 `tool_use` 是什么 | 通俗解释:模型不直接回答,而是说"我要调用 read_file 工具,参数是 xxx",本地程序执行后把结果喂回模型 |

---

## Day 2:整体架构(Big Picture)

> **阶段目标(一句话)**:能画出项目的模块结构图,并指出 1~2 条最关键的技术主线。

### 核心学习任务

| 序号 | 任务 | 具体动作 | 预计时长 |
|------|------|---------|---------|
| 2.1 | 读懂目录结构 | 对照 README 第 174~310 行"目录结构说明",把每个包的职责填入下方速查表 | 30min |
| 2.2 | 画一张模块关系图 | 用纸/工具画出:入口 → 装配 → 配置 → 运行时 → 工具/任务/团队 的层次关系 | 45min |
| 2.3 | 识别核心主线 | 找到"一次 Agent 循环"主线:用户输入 → messages → 模型 → tool_use → 本地执行 → tool_result → 再调模型 | 30min |
| 2.4 | 理解"运行时与配置分离" | 读 `AgentRuntime` 和 `StageConfig` 的类注释,理解"怎么跑"和"能做什么"分离 | 45min |
| 2.5 | 数一数阶段演进 | 浏览 S01~S12 的类名,理解每个阶段只加一个新能力(渐进式设计) | 30min |

### 模块职责速查表(你来填,答案对 README)

| 包路径 | 职责 | 关键类 |
|--------|------|--------|
| `agents/` | Agent 如何跑 | `AgentRuntime`、`StageConfig`、`S01~S12` |
| `common/` | ? | `AnthropicClient`、`EnvConfig` |
| `tools/` | ? | `CommandTools`、`TodoManager` |
| `tasks/` | ? | `TaskManager`、`WorktreeManager` |
| `team/` | ? | `MessageBus`、`TeammateManager` |
| `context/` | ? | `CompressionService` |
| `background/` | ? | `BackgroundManager` |
| `skills/` | ? | `SkillLoader` |

### 关键技术主线(本项目最重要的 2 条)

**主线 A:Agent 闭环(必懂)**

```
用户输入 → AgentRuntime 加入 messages
         → AnthropicClient 发请求给模型
         → 模型返回文本 or tool_use
         → 若 tool_use:本地执行工具,结果回写 messages
         → 再次调用模型
         → 循环直到模型不再请求工具
```

**主线 B:能力渐进叠加(理解设计哲学)**

```
S01 最小闭环 → S02 加文件工具 → S03 加 Todo
→ S04 加子代理 → S05 加技能 → S06 加压缩
→ S07 加任务板 → S08 加后台任务
→ S09~S11 加多 Agent 协作 → S12 加工作区隔离
→ SFull 全部叠加
```

### 推荐重点关注的文件

- `README.md` 第 174~310 行(目录结构说明)
- `README.md` 第 406~457 行(5 个核心设计思想)
- 各包下的类(只看类注释和字段,**不看方法实现**)

### 常见卡点 + 简化阅读策略

| 卡点 | 简化阅读策略 |
|------|-------------|
| 模块太多,记不住关系 | 只记主线 A 的 5 个角色:入口、运行时、模型客户端、工具、消息历史;其余都是"外挂能力" |
| "运行时与配置分离"听不懂 | 通俗解释:`AgentRuntime` 是"发动机",`StageConfig` 是"档位选择器";同一个发动机配不同档位 = 不同阶段 |
| S01~S12 太多看不过来 | 第一遍只需看 S01、S02、SFull 三个,理解"最小 → 加工具 → 全量"的演进即可 |

---

## Day 3:关键路径 · 上(入口 → 装配 → 配置)

> **阶段目标(一句话)**:走通从入口到配置的链路,理解"一个阶段是如何被启动的"。

### 核心学习任务(按阅读顺序)

| 序号 | 任务 | 具体动作 | 预计时长 |
|------|------|---------|---------|
| 3.1 | 读入口 | 读 `S01AgentLoop`,看它如何选择 StageConfig 并交给 Launcher | 20min |
| 3.2 | 读启动器 | 读 `Launcher`,理解它如何用 AppContext 装配服务并启动 Runtime | 40min |
| 3.3 | 读装配器 | 读 `AppContext`,理解它创建了哪些共享服务(client、tools、tasks、team) | 40min |
| 3.4 | 读配置中心 | 读 `StageConfig`,看 S01 配置了哪些工具、system prompt | 40min |
| 3.5 | 复述链路 | 用自己的话写出:S01 如何 → Launcher → AppContext → StageConfig → AgentRuntime | 20min |

### 自上而下阅读路径图

```
S01AgentLoop (入口,选配置)
     ↓
Launcher (启动,装配)
     ↓
AppContext (创建共享服务)
     ↓
StageConfig (定义本阶段能力)
     ↓
AgentRuntime (主循环 ← Day 4 重点)
```

### 推荐重点关注的文件

- `src/main/java/com/learnclaudecode/agents/S01AgentLoop.java`
- `src/main/java/com/learnclaudecode/agents/Launcher.java`
- `src/main/java/com/learnclaudecode/agents/AppContext.java`
- `src/main/java/com/learnclaudecode/agents/StageConfig.java`

### 常见卡点 + 简化阅读策略

| 卡点 | 简化阅读策略 |
|------|-------------|
| AppContext 装配的服务太多看不懂 | 只关注 4 个:`AnthropicClient`(模型)、`CommandTools`(工具)、`TaskManager`(任务)、`TodoManager`(待办),其余先跳过 |
| StageConfig 字段太多 | 只看 3 类字段:工具列表(`tools`)、能力开关(各种 boolean)、system prompt,其余先忽略 |
| 入口类太薄,感觉没东西 | 这正是设计巧妙之处:入口类越薄,说明运行时与配置分离做得越好;记录下这个观察 |

---

## Day 4:关键路径 · 下(主循环 → 模型客户端)

> **阶段目标(一句话)**:走通 Agent 主循环,理解"模型决策 → 本地执行 → 结果回注"的完整闭环。

### 核心学习任务

| 序号 | 任务 | 具体动作 | 预计时长 |
|------|------|---------|---------|
| 4.1 | **读主循环(核心)** | 读 `AgentRuntime` 的主循环方法,跟踪:调模型 → 判断 tool_use → 分发执行 → 回写结果 → 再调模型 | 1.5h |
| 4.2 | 读模型客户端 | 读 `AnthropicClient`,理解 HTTP 请求怎么发、响应怎么解析 | 45min |
| 4.3 | 读数据结构 | 读 `ChatMessage` 和 `AnthropicResponse`,理解 messages 的 role/content 结构 | 30min |
| 4.4 | 端到端复述 | 用自己的话把 Day 3 + Day 4 串起来,讲一遍完整闭环 | 15min |

### AgentRuntime 主循环精读清单

阅读时带着这 5 个问题:

- [ ] 用户输入如何进入 `messages`?
- [ ] 一次模型调用的入参(system + messages + tools)如何组装?
- [ ] 模型返回 `tool_use` 时,代码如何找到对应的 Java 工具实现并执行?
- [ ] 工具执行结果如何变成 `tool_result` 回写到 `messages`?
- [ ] 循环的退出条件是什么?(模型不再请求工具)

### 推荐重点关注的文件

- `src/main/java/com/learnclaudecode/agents/AgentRuntime.java`(**本周最核心**)
- `src/main/java/com/learnclaudecode/common/AnthropicClient.java`
- `src/main/java/com/learnclaudecode/model/ChatMessage.java`
- `src/main/java/com/learnclaudecode/model/AnthropicResponse.java`

### 常见卡点 + 简化阅读策略

| 卡点 | 简化阅读策略 |
|------|-------------|
| AgentRuntime 太长,方法太多 | **只看主循环方法**,其他方法(subagent、后台回注、inbox 轮询)先跳过,标记"Day 5 再看" |
| `messages` 数据结构看不懂 | 先看 `ChatMessage` 和 `AnthropicResponse` 的字段定义,理解 role/content 结构 |
| HTTP 请求细节复杂 | `AnthropicClient` 里只需关注:请求体如何组装(拼 system + messages + tools)、响应体如何取 `content` 数组、如何区分文本和 `tool_use` |
| 工具分发逻辑绕 | 通俗解释:模型说"我要调 read_file",代码就用 if/switch 或 map 找到对应 Java 方法执行;本质就是"命令模式" |

---

## Day 5:局部深挖(Deep Dive)

> **阶段目标(一句话)**:选一个模块精读,结合设计模式讲清它的设计巧妙之处。

### 选一个模块深挖(三选一)

| 选项 | 深挖模块 | 设计模式 / Java 特性 | 推荐理由 |
|------|---------|---------------------|---------|
| **A(推荐新手)** | `StageConfig` + 阶段演进 | 策略模式、配置驱动 | 理解"同一运行时如何演化多阶段",是理解整个项目设计的钥匙 |
| **B** | `CommandTools` 工具系统 | 命令模式、工具分发 | 理解"模型决策与本地执行的边界",最贴近实际 coding agent |
| **C** | `CompressionService` 上下文压缩 | 模板方法、状态裁剪 | 理解"长对话如何不撑爆上下文窗口",实战价值高 |

### 以选项 A 为例的深挖任务

| 序号 | 任务 | 具体动作 | 预计时长 |
|------|------|---------|---------|
| 5.1 | 对比 S01 与 S02 的 StageConfig | 看 `S01AgentLoop` 和 `S02ToolUse` 各自选了什么配置 | 30min |
| 5.2 | 列出 StageConfig 的能力开关 | 读 `StageConfig`,记录每个 boolean 字段控制的开关 | 45min |
| 5.3 | 理解配置如何影响 Runtime 行为 | 在 `AgentRuntime` 中搜索 StageConfig 字段被读取的地方 | 45min |
| 5.4 | 总结设计模式 | 画出:StageConfig(策略接口)→ 各阶段(具体策略)→ AgentRuntime(上下文)的关系 | 30min |

### 设计模式对照表(本项目可见)

| 设计模式 | 体现位置 | 通俗解释 |
|---------|---------|---------|
| **策略模式** | StageConfig + 各阶段 | 同一个发动机,换不同档位跑出不同能力 |
| **命令模式** | 工具分发(tool_use → Java 方法) | 模型下"命令",本地按命令类型找对应执行器 |
| **模板方法** | AgentRuntime 主循环骨架 | 循环骨架固定,具体工具/能力可插拔 |
| **外观模式** | AppContext | 把一堆服务的创建藏在背后,对外只露一个入口 |
| **观察者(变体)** | MessageBus 文件 inbox | 队友往 inbox 写消息,另一个 Agent 轮询读取 |

### 推荐重点关注的文件

- `src/main/java/com/learnclaudecode/agents/StageConfig.java`
- `src/main/java/com/learnclaudecode/tools/CommandTools.java`
- `src/main/java/com/learnclaudecode/context/CompressionService.java`
- `src/main/java/com/learnclaudecode/skills/SkillLoader.java`

### 常见卡点 + 简化阅读策略

| 卡点 | 简化阅读策略 |
|------|-------------|
| 设计模式名词看不懂 | 不要纠结"这算不算标准策略模式",先理解"配置字段如何改变行为"这个本质 |
| 泛型/反射看不懂 | 本项目基本没用复杂泛型和反射,遇到就标记跳过 |
| 不确定选哪个模块深挖 | 选 A(StageConfig),因为它串起了"配置 → 运行时 → 阶段演进"三个核心概念,性价比最高 |

---

## Day 6:动手验证(Learning by Doing)

> **阶段目标(一句话)**:通过小修改验证理解,确认自己真的读懂了。

### 核心学习任务

| 序号 | 任务 | 具体动作 | 预计时长 |
|------|------|---------|---------|
| 6.1 | 改配置验证理解 | 修改某个 StageConfig 的能力开关(如关闭某工具),观察 Agent 行为变化 | 30min |
| 6.2 | 加日志验证流程 | 在 `AgentRuntime` 主循环关键节点加 `System.out.println`,观察每轮 messages/tool_use | 45min |
| 6.3 | 加一个简单工具 | 参考 `CommandTools` 的写法,加一个简单工具(如 `get_time`),并在 StageConfig 注册 | 1h |
| 6.4 | 写一个小测试 | 为 `TodoManager` 或 `JsonUtils` 写一个 JUnit 测试 | 30min |

### 验证清单(每项打勾说明理解到位)

- [ ] 改了 StageConfig 的工具列表后,Agent 调用行为确实变了
- [ ] 加日志后,能亲眼看到"tool_use → 执行 → tool_result"的循环
- [ ] 自己加的工具能被模型正确调用
- [ ] 能解释"为什么 S01 没有 Todo,而 S03 有"

### 推荐重点关注的文件

- `src/main/java/com/learnclaudecode/agents/StageConfig.java`(改配置)
- `src/main/java/com/learnclaudecode/agents/AgentRuntime.java`(加日志)
- `src/main/java/com/learnclaudecode/tools/CommandTools.java`(加工具参考)
- `src/main/java/com/learnclaudecode/tools/TodoManager.java`(写测试参考)

### 常见卡点 + 简化阅读策略

| 卡点 | 简化阅读策略 |
|------|-------------|
| 加了工具但模型不调用 | 检查:工具是否注册到 StageConfig 的 tools 列表、tool spec 的 JSON schema 是否正确、system prompt 是否引导模型使用 |
| 不知道怎么写 JUnit | 参考项目现有依赖,`pom.xml` 目前没有 JUnit,需要先加 `junit-jupiter` 依赖;或只写一个 main 方法做简单断言 |
| 改完编译不过 | 优先用 IDE(如 IntelliJ IDEA)打开项目,让它帮你定位编译错误 |

---

## Day 7:提交 PR + 总结

> **阶段目标(一句话)**:提交一个小 PR,并复盘这一周的学习成果。

### 核心学习任务

| 序号 | 任务 | 具体动作 | 预计时长 |
|------|------|---------|---------|
| 7.1 | 选择 PR 类型 | 从下方 3 类 PR 中选一个 | 15min |
| 7.2 | Fork 并 clone | Fork 仓库到自己的 GitHub,clone 到本地 | 15min |
| 7.3 | 做小修改 | 只改一两个文件,保持改动小而聚焦 | 1h |
| 7.4 | 提交并发起 PR | commit → push → 在 GitHub 发起 Pull Request | 30min |
| 7.5 | 复盘总结 | 用自己的话写一段 200 字的学习总结,记录还遗留的疑问 | 30min |

### 适合新手提交的 3 类 PR

| PR 类型 | 具体示例 | 难度 | 价值 |
|--------|---------|------|------|
| **1. 文档改进类** | 补充某个类的 Javadoc 注释;修正 README 中的错别字/过时描述;为某个 StageConfig 字段加说明 | ⭐ | 维护者最欢迎,合并快,适合破冰 |
| **2. 测试补充类** | 为 `JsonUtils`、`TodoManager`、`WorkspacePaths` 的工具方法补 JUnit 单测;为路径安全校验补边界测试 | ⭐⭐ | 既能练手又能提升项目质量 |
| **3. 示例/小工具增强类** | 新增一个无害的小工具(如 `get_time`、`list_env`);给某个阶段加一个示例 skills/ 文档;优化日志输出格式 | ⭐⭐ | 展示对项目的理解,有实际功能价值 |

### PR 提交流程(新手友好版)

```
1. Fork 仓库到自己的 GitHub
2. git clone 你的 fork
3. git checkout -b docs/add-javadoc-for-stageconfig
4. 做小修改(只改一两个文件)
5. git commit -m "docs: add Javadoc for StageConfig fields"
6. git push origin docs/add-javadoc-for-stageconfig
7. 在 GitHub 上发起 Pull Request,描述清楚改了什么、为什么改
```

### 复盘清单

- [ ] 能一句话说清项目用途
- [ ] 能画出模块关系图
- [ ] 能走通 Agent 闭环主线
- [ ] 精读过至少一个模块并理解其设计模式
- [ ] 动手改过代码并验证
- [ ] 提交过一个小 PR
- [ ] 记录了 3 个还想深入的疑问(留给后续)

---

## 每日详细代码指引(Day 1 ~ Day 7)

> 以下内容基于项目实际源码,给出具体的类、方法、行号和关键代码片段解读。
> 建议配合 IDE(IntelliJ IDEA)边看边读,遇到不懂的代码先跳过,不纠结细节。

### Day 1 详细指引:把项目跑起来

#### 1. `.env` 配置详解

环境变量由 `common/EnvConfig.java` 读取,核心逻辑在构造函数(第 22~29 行):

```java
public EnvConfig() {
    this.dotenv = Dotenv.configure().ignoreIfMissing().load();
    this.modelId = require("MODEL_ID");           // 必填,缺失会抛异常
    this.apiKey = getenv("ANTHROPIC_API_KEY").orElse("");  // 可空
    this.baseUrl = getenv("ANTHROPIC_BASE_URL").orElse("https://api.anthropic.com");
    this.workdir = Paths.get("").toAbsolutePath().normalize();  // 当前目录
}
```

**通俗解释**:`MODEL_ID` 是必填的(没填会报错),其余两个有默认值。`workdir` 就是项目根目录,后面所有文件操作都在这个目录内进行。

**你需要做的**:在项目根目录创建 `.env` 文件,填入:
```env
ANTHROPIC_API_KEY=你的key
MODEL_ID=你的模型名
ANTHROPIC_BASE_URL=你的兼容服务地址(如用官方可留空)
```

#### 2. `pom.xml` 依赖解读

项目只有 2 个核心依赖(第 19~34 行),非常精简:

| 依赖 | 作用 | 通俗解释 |
|------|------|--------|
| `dotenv-java` v3.0.0 | 读取 `.env` 文件 | 像 Spring 的 @Value,但更简单 |
| `jackson-databind` v2.17.2 | JSON 序列化/反序列化 | 把 Java 对象变成 JSON 发给模型,把模型返回的 JSON 变成 Java 对象 |
| `jackson-datatype-jsr310` | 支持 Java 8+ 日期类型 | 让 Jackson 能处理 `Instant` 等 |

**构建插件**(第 37~66 行):
- `maven-compiler-plugin` — 编译 Java 17
- `exec-maven-plugin` — 让你可以用 `mvn exec:java` 直接运行

#### 3. 运行命令与终端交互

```bash
# 编译
mvn compile

# 运行 S01 最小闭环
mvn exec:java -Dexec.mainClass=com.learnclaudecode.agents.S01AgentLoop
```

运行后你会看到终端出现 `s01 >> ` 提示符(来自 `AgentRuntime.java` 第 102 行):
```java
System.out.print("\u001B[36m" + config.prompt() + " >> \u001B[0m");
```

输入 `q` 或 `exit` 退出(第 107 行)。输入任意任务后,Agent 会进入主循环。

**验证练习**:输入 `list files in current directory`,观察模型如何通过 `bash` 工具执行 `ls`/`dir` 命令并返回结果。

### Day 2 详细指引:AppContext 装配链路

#### 1. AppContext 装配顺序解读(`agents/AppContext.java` 第 35~58 行)

```java
// 第一层:基础环境
EnvConfig env = new EnvConfig();                    // 读 .env
WorkspacePaths paths = new WorkspacePaths(env.getWorkdir());  // 管路径

// 第二层:模型客户端 + 基础工具
AnthropicClient client = new AnthropicClient(env);  // 调模型
CommandTools commandTools = new CommandTools(paths); // 跑命令/读写文件
TodoManager todoManager = new TodoManager();        // 管待办

// 第三层:扩展机制
SkillLoader skillLoader = new SkillLoader(paths);   // 加载技能
CompressionService compressionService = new CompressionService(paths, client, 50000, 3);
TaskManager taskManager = new TaskManager(paths);   // 任务板
BackgroundManager backgroundManager = new BackgroundManager(paths);  // 后台任务

// 第四层:团队协作
MessageBus messageBus = new MessageBus(paths);
TeammateManager teammateManager = new TeammateManager(paths, client, commandTools, messageBus, taskManager);
WorktreeManager worktreeManager = new WorktreeManager(paths, taskManager);

// 第五层:统一运行时
this.runtime = new AgentRuntime(client, paths, commandTools, todoManager,
    skillLoader, compressionService, taskManager, backgroundManager,
    messageBus, teammateManager, worktreeManager);
```

**通俗解释**:就像组装一台电脑——先装主板(EnvConfig),再装 CPU(AnthropicClient)、硬盘(CommandTools),然后加装显卡(SkillLoader)、网卡(MessageBus),最后按下开机键(AgentRuntime)。

#### 2. 模块职责速查表(答案)

| 包路径 | 职责 | 关键类 |
|--------|------|--------|
| `agents/` | Agent 如何跑 | `AgentRuntime`、`StageConfig`、`S01~S12`、`Launcher`、`AppContext` |
| `common/` | 基础设施:模型调用、配置、路径、JSON | `AnthropicClient`、`EnvConfig`、`WorkspacePaths`、`JsonUtils` |
| `tools/` | Agent 的手脚:命令执行、文件读写 | `CommandTools`、`TodoManager` |
| `tasks/` | 任务编排:任务板、工作区隔离 | `TaskManager`、`WorktreeManager` |
| `team/` | 多 Agent 协作:消息总线、队友管理 | `MessageBus`、`TeammateManager` |
| `context/` | 上下文压缩:防止对话太长 | `CompressionService` |
| `background/` | 后台异步任务 | `BackgroundManager` |
| `skills/` | 技能加载:外挂知识包 | `SkillLoader` |
| `model/` | 数据结构:消息、响应、任务等 | `ChatMessage`、`AnthropicResponse`、`TodoItem` 等 |

#### 3. WorkspacePaths 路径管理(`common/WorkspacePaths.java`)

关键方法 `safeResolve`(第 42~49 行)做路径安全校验:
```java
public Path safeResolve(String relativePath) {
    Path resolved = workdir.resolve(relativePath).normalize();
    if (!resolved.startsWith(workdir)) {
        throw new IllegalArgumentException("Path escapes workspace: " + relativePath);
    }
    return resolved;
}
```

**通俗解释**:防止模型通过 `../../etc/passwd` 读取工作区外的文件。所有文件工具都必须走这个方法。

它还管理这些目录(第 86~148 行):

| 方法 | 目录 | 用途 |
|------|------|------|
| `tasksDir()` | `.tasks/` | 任务板数据 |
| `teamDir()` | `.team/` | 团队协作数据 |
| `inboxDir()` | `.team/inbox/` | 队友消息收件箱 |
| `skillsDir()` | `skills/` | 技能文件 |
| `transcriptDir()` | `.transcripts/` | 压缩前的对话备份 |
| `worktreesDir()` | `.worktrees/` | 工作区隔离数据 |

### Day 3 详细指引:入口 → 装配 → 配置链路

#### 1. S01AgentLoop 逐行解读(`agents/S01AgentLoop.java` 全 12 行)

```java
public class S01AgentLoop {
    public static void main(String[] args) {
        Launcher.launch(StageConfig.s01());  // 唯一一行核心代码
    }
}
```

**关键观察**:入口类只有一行有意义的代码。它做了两件事:
1. 调 `StageConfig.s01()` 拿到 S01 阶段的配置
2. 把配置交给 `Launcher.launch()` 启动

**对比 S02**(`agents/S02ToolUse.java`):
```java
public static void main(String[] args) {
    Launcher.launch(StageConfig.s02());  // 只是换了 s02()
}
```

所有 S01~S12 的入口类结构完全一样,只是传不同的 `StageConfig.xxx()`。这就是"运行时与配置分离"的直接体现。

#### 2. Launcher 逐行解读(`agents/Launcher.java` 第 23~26 行)

```java
public static void launch(StageConfig config) {
    new AppContext().runtime().runRepl(config);
}
```

**拆解**:这一行做了三件事:
1. `new AppContext()` — 创建所有共享服务(装配)
2. `.runtime()` — 拿到装配好的 `AgentRuntime` 实例
3. `.runRepl(config)` — 用指定阶段配置启动交互式循环

#### 3. StageConfig 的 record 结构(`agents/StageConfig.java` 第 26~36 行)

```java
public record StageConfig(
    String prompt,               // 终端提示符前缀,如 "s01"
    boolean enableTodoNag,       // 是否启用 Todo 提醒
    boolean enableCompression,   // 是否启用上下文压缩
    boolean enableBackground,    // 是否启用后台任务回注
    boolean enableInbox,         // 是否启用 inbox 轮询
    boolean subagentWritable,    // 子代理是否可写文件
    boolean autonomousTeammates, // 队友是否自治
    List<Map<String, Object>> tools,  // 可用工具列表
    String systemTemplate        // system prompt 模板
) { ... }
```

**通俗解释**:Java 14+ 的 `record` 关键字自动生成构造函数、getter、equals 等。你可以把它当成一个不可变的数据类。9 个字段中,7 个是 boolean 开关,控制 Agent 的能力边界。

#### 4. s01() 工厂方法解读(第 77~82 行)

```java
public static StageConfig s01() {
    return new StageConfig(
        "s01",                          // prompt
        false, false, false, false,     // 所有高级开关全关
        false, false,                   // subagentWritable, autonomous
        List.of(baseTools().get(0)),    // 只给 bash 工具
        "You are a coding agent at ${WORKDIR}. Use bash to solve tasks. Act, don't explain."
    );
}
```

**关键点**:S01 只开放了 `baseTools().get(0)`,即 `bash` 工具(第 65 行定义)。其他文件工具(read_file/write_file/edit_file)都没有。所以 S01 的 Agent 只能通过执行命令来完成任务。

#### 5. baseTools() 基础工具集(第 56~69 行)

```java
public static List<Map<String, Object>> baseTools() {
    tools.add(tool("bash", "Run a shell command.", ...));
    tools.add(tool("read_file", "Read file contents.", ...));
    tools.add(tool("write_file", "Write content to file.", ...));
    tools.add(tool("edit_file", "Replace exact text in file.", ...));
    return tools;
}
```

每个工具定义就是一个 `Map<String, Object>`,包含 name、description、input_schema,直接对齐 Anthropic Messages API 的 tool 定义格式。

### Day 4 详细指引:AgentRuntime 主循环(本周最核心)

#### 1. runRepl() 交互循环(第 97~123 行)

```java
public void runRepl(StageConfig config) {
    List<ChatMessage> history = new ArrayList<>();  // 消息历史(工作记忆)
    Scanner scanner = new Scanner(System.in);
    while (true) {
        System.out.print(config.prompt() + " >> ");  // 打印提示符
        String query = scanner.nextLine();
        if ("q".equalsIgnoreCase(query)) break;     // 退出

        history.add(new ChatMessage("user", query));  // 用户输入进入历史
        agentLoop(history, config);                   // 进入 Agent 主循环

        // 打印最后一条 assistant 消息中的文本
        Object content = history.get(history.size() - 1).content();
        if (content instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> block && block.containsKey("text"))
                    System.out.println(block.get("text"));
            }
        }
    }
}
```

**通俗解释**:这是外层循环——读用户输入 → 调 agentLoop → 打印结果 → 再读下一条。`history` 是整个会话的工作记忆,在多次对话间累积。

#### 2. agentLoop() 主循环逐段解读(第 131~261 行)

这是**整个项目最核心的方法**,建议花 1.5 小时精读。

**第 1 段:压缩检查(第 136~145 行)**
```java
if (config.enableCompression()) {
    compressionService.microCompact(messages);    // 先做轻量裁剪
    if (compressionService.needsAutoCompact(messages)) {
        List<ChatMessage> compacted = compressionService.autoCompact(new ArrayList<>(messages));
        messages.clear();
        messages.addAll(compacted);               // 用摘要替换旧历史
    }
}
```
→ 如果对话太长,先把旧的工具结果清掉(microCompact),还不够就把整段对话压缩成摘要(autoCompact)。

**第 2 段:后台任务回注(第 146~159 行)**
```java
if (config.enableBackground()) {
    List<Map<String, Object>> notifs = backgroundManager.drain();
    if (!notifs.isEmpty()) {
        messages.add(new ChatMessage("user", "<background-results>\n" + builder + "</background-results>"));
    }
}
```
→ 异步任务完成后,结果作为新的 user 消息注入主对话。

**第 3 段:Inbox 轮询(第 160~167 行)**
```java
if (config.enableInbox()) {
    List<Map<String, Object>> inbox = messageBus.readInbox("lead");
    if (!inbox.isEmpty()) {
        messages.add(new ChatMessage("user", "<inbox>" + ... + "</inbox>"));
    }
}
```
→ 团队阶段,lead 会检查队友发来的消息。

**第 4 段:调用模型(第 168~176 行)— 最关键**
```java
var response = client.createMessage(
    config.systemPrompt(skillLoader, paths.workdir()),  // system prompt
    messages,                                           // 消息历史
    config.tools(),                                     // 可用工具
    8000                                                // max_tokens
);
messages.add(new ChatMessage("assistant", response.content()));  // 模型回复进入历史
if (!"tool_use".equals(response.stop_reason())) {
    return;  // 不是 tool_use → 模型给出最终回复,循环结束
}
```
→ **这是 Agent 与普通聊天的核心区别**:模型如果返回 `stop_reason = "tool_use"`,说明它想调工具,循环继续;否则循环结束。

**第 5 段:工具分发执行(第 177~241 行)— 最核心**
```java
for (Map<String, Object> block : response.content()) {
    if (!"tool_use".equals(block.get("type"))) continue;

    String toolName = String.valueOf(block.get("name"));
    Map<String, Object> input = (Map<String, Object>) block.getOrDefault("input", Map.of());

    String output = switch (toolName) {
        case "bash" -> commandTools.runBash(String.valueOf(input.get("command")));
        case "read_file" -> commandTools.runRead(...);
        case "write_file" -> commandTools.runWrite(...);
        case "edit_file" -> commandTools.runEdit(...);
        case "todo" -> { usedTodo = true; yield todoManager.update(items); }
        case "task" -> runSubagent(...);
        // ... 更多工具
        default -> "Unknown tool: " + toolName;
    };

    results.add(Map.of("type", "tool_result", "tool_use_id", block.get("id"), "content", output));
}
```
→ Java 14+ 的 switch 表达式。模型返回的每个 `tool_use` 块,都映射到一个 Java 方法执行。结果包装成 `tool_result` 放入 results 列表。

**第 6 段:结果回注(第 242~259 行)**
```java
messages.add(new ChatMessage("user", results));  // 工具结果作为 user 消息回写
if (manualCompact) {
    List<ChatMessage> compacted = compressionService.autoCompact(messages);
    messages.clear();
    messages.addAll(compacted);
}
// → while(true) 回到第 1 段,继续下一轮
```
→ 工具结果回到 `messages` 中,模型在下一轮循环就能看到"执行后的真实世界状态"。

#### 3. 主循环流程图(带行号)

```
agentLoop() 第131行 while(true)
  │
  ├─ 136行: 压缩检查(如开启)
  ├─ 146行: 后台任务回注(如开启)
  ├─ 160行: Inbox轮询(如开启)
  │
  ├─ 171行: client.createMessage(system, messages, tools) → 调模型
  ├─ 172行: messages.add(assistant回复)
  ├─ 173行: stop_reason != "tool_use"? → return 退出循环
  │
  ├─ 180行: for (每个tool_use块)
  │    ├─ 189行: switch(toolName) → 执行对应Java方法
  │    └─ 236行: 收集 tool_result
  │
  ├─ 244行: Todo提醒检查
  ├─ 252行: messages.add(user, results) → 结果回写历史
  └─ 回到while(true)顶部 → 下一轮
```

#### 4. AnthropicClient 请求组装(`common/AnthropicClient.java` 第 52~101 行)

```java
public AnthropicResponse createMessage(String system, List<?> messages, List<?> tools, int maxTokens) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("model", config.getModelId());
    payload.put("max_tokens", maxTokens);
    payload.put("messages", messages);
    if (system != null) payload.put("system", system);
    if (tools != null) payload.put("tools", tools);

    HttpRequest.Builder builder = HttpRequest.newBuilder()
        .uri(URI.create(config.getBaseUrl().replaceAll("/$", "") + "/v1/messages"))
        .header("content-type", "application/json")
        .header("anthropic-version", "2023-06-01")
        .POST(HttpRequest.BodyPublishers.ofString(JsonUtils.toJson(payload)));

    // 同时发两种认证头,兼容官方和第三方
    builder.header("x-api-key", config.getApiKey());
    builder.header("authorization", "Bearer " + config.getApiKey());

    HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    return JsonUtils.fromJson(response.body(), AnthropicResponse.class);
}
```

**通俗解释**:把 system + messages + tools 拼成一个 JSON,通过 HTTP POST 发给模型服务端。模型返回的 JSON 被解析成 `AnthropicResponse` 对象。

#### 5. 数据结构解读

**ChatMessage**(`model/ChatMessage.java` 全 8 行):
```java
public record ChatMessage(String role, Object content) {}
```
→ 极简设计!`role` 是 "user"/"assistant",`content` 是 Object 类型——可以是 String(普通文本),也可以是 List<Map>(结构化的 tool_use/tool_result 块)。这种设计用 Object 兼容了两种内容形态。

**AnthropicResponse**(`model/AnthropicResponse.java` 全 14 行):
```java
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnthropicResponse(String stop_reason, List<Map<String, Object>> content) {}
```
→ `stop_reason` 是关键:值为 `"tool_use"` 表示模型想调工具,其他值(如 `"end_turn"`)表示模型给出最终回复。`@JsonIgnoreProperties(ignoreUnknown = true)` 让 Jackson 忽略响应中不需要的字段。

### Day 5 详细指引:StageConfig 阶段演进 + 设计模式

#### 1. 各阶段配置对比表

| 阶段 | 新增工具 | 开启的开关 | system prompt 要点 |
|------|---------|-----------|------------------|
| s01 | bash | 无 | "Use bash to solve tasks" |
| s02 | +read_file, write_file, edit_file | 无 | "Use tools to solve tasks" |
| s03 | +todo | enableTodoNag | "Use the todo tool to plan" |
| s04 | +task(子代理) | 无 | "Use task to delegate" |
| s05 | +load_skill | 无 | "Use load_skill for knowledge" + ${SKILLS} |
| s06 | +compact | enableCompression | "Use tools to solve tasks" |
| s07 | +task_create/update/list/get | 无 | "Use task tools to plan and track" |
| s08 | +background_run/check | enableBackground | "Use background_run for long commands" |
| s09 | +spawn_teammate/list/send/read/broadcast | enableInbox | "You are a team lead" |
| s10 | +shutdown_request/plan_approval | enableInbox | "Manage teammates with protocols" |
| s11 | +claim_task/idle | autonomousTeammates | "Teammates are autonomous" |
| s12 | +worktree_create/list/remove/events | autonomousTeammates | "Use task + worktree tools" |
| sFull | 全部工具 | 全部开启 | 完整 system prompt |

**阅读练习**:打开 `StageConfig.java`,对照上表,找到每个阶段的 `new StageConfig(...)` 调用,确认哪些 boolean 是 true。

#### 2. CompressionService 三层压缩解读(`context/CompressionService.java`)

**第 1 层:token 估算(第 56~60 行)**
```java
public int estimateTokens(List<ChatMessage> messages) {
    return JsonUtils.toJson(messages).length() / 4;
    // 通俗解释:1个token约等于4个字符,用JSON长度粗略估算
}
```

**第 2 层:microCompact 微压缩(第 69~106 行)**
```
作用:不删除整条消息,只把较老的 tool_result 内容替换为 "[cleared]"
保留:最近 keepRecent(默认3)条工具结果完整不动
```

**第 3 层:autoCompact 全量压缩(第 118~160 行)**
```
步骤:
1. 把完整对话写入 .transcripts/transcript_xxx.jsonl(落盘备份)
2. 把整段对话发给模型,让它生成摘要
3. 用 [摘要 + assistant确认] 两条消息替换原来的长历史
```

**通俗解释**:像整理抽屉——先扔掉旧的草稿纸(microCompact),如果还放不下就把所有东西拍照存档再只留一张清单(autoCompact)。

#### 3. CommandTools 工具实现解读(`tools/CommandTools.java`)

| 方法 | 行号 | 核心逻辑 | 安全措施 |
|------|------|---------|--------|
| `runBash` | 36~65 | ProcessBuilder 执行命令,120秒超时 | 黑名单拦截 `rm -rf /`、`sudo` 等 |
| `runRead` | 87~101 | Files.readAllLines + limit 截断 | `paths.safeResolve()` 防逃逸 |
| `runWrite` | 110~121 | Files.writeString | `paths.safeResolve()` 防逃逸 |
| `runEdit` | 131~144 | 精确文本替换 replaceFirst | 先检查 oldText 是否存在 |

**关键细节**:`runBash` 第 73~78 行做了操作系统适配:
```java
public List<String> shellCommand(String command) {
    if (IS_WINDOWS) return List.of("powershell", "-Command", command);
    return List.of("bash", "-lc", command);
}
```
→ Windows 用 PowerShell,Linux 用 bash。你在 Windows 上跑项目时,模型执行的命令实际是通过 PowerShell 运行的。

#### 4. 设计模式代码位置对照

| 设计模式 | 代码位置 | 关键代码 |
|---------|---------|--------|
| **策略模式** | `StageConfig.java` s01()~sFull() | 每个 `s0x()` 返回不同配置,同一个 `AgentRuntime` 表现不同能力 |
| **命令模式** | `AgentRuntime.java` 第 189~234 行 switch | `case "bash" -> commandTools.runBash(...)`,模型下命令,本地找执行器 |
| **模板方法** | `AgentRuntime.java` agentLoop() | while(true) 骨架固定,具体工具/能力由 config 决定 |
| **外观模式** | `AppContext.java` 构造函数 | 把 11 个服务的创建藏在背后,对外只露 `runtime()` |
| **工厂方法** | `StageConfig.java` baseTools() / tool() | `tool(name, desc, schema)` 统一构造工具定义 |

### Day 6 详细指引:动手验证具体操作

#### 1. 加日志的具体位置和代码

在 `AgentRuntime.java` 的 `agentLoop()` 方法中,在以下位置加 `System.out.println`:

```java
// 第 171 行之前(调模型前):
System.out.println("[DEBUG] 准备调模型, 当前消息数: " + messages.size());

// 第 171 行之后(模型返回后):
System.out.println("[DEBUG] 模型返回, stop_reason: " + response.stop_reason());
System.out.println("[DEBUG] content块数: " + response.content().size());

// 第 189 行 switch 之前(工具分发时):
System.out.println("[DEBUG] 执行工具: " + toolName + ", 输入: " + input);

// 第 235 行(工具执行后):
System.out.println("[DEBUG] 工具结果(前100字): " + output.substring(0, Math.min(100, output.length())));
```

重新编译运行,你会在终端看到类似输出:
```
[DEBUG] 准备调模型, 当前消息数: 1
[DEBUG] 模型返回, stop_reason: tool_use
[DEBUG] content块数: 1
[DEBUG] 执行工具: bash, 输入: {command=dir}
> bash: ...(命令输出)
[DEBUG] 工具结果(前100字): ...(结果)
[DEBUG] 准备调模型, 当前消息数: 3
[DEBUG] 模型返回, stop_reason: end_turn
```

#### 2. 加一个 `get_time` 工具的完整步骤

**步骤 1:在 `StageConfig.java` 的 `baseTools()` 后面加工具定义**

```java
// 在 baseTools() 方法内,return 之前添加:
tools.add(tool("get_time", "Get current date and time.",
    Map.of("type", "object", "properties", Map.of(), "required", List.of())));
```

**步骤 2:在 `AgentRuntime.java` 的 switch 中加 case**

```java
// 在 agentLoop() 的 switch 表达式中(第 189 行附近)添加:
case "get_time" -> java.time.LocalDateTime.now().toString();
```

**步骤 3:在 `runSubagent()` 的 switch 中也加同样的 case**(第 298 行附近)

**步骤 4:修改 s01() 的工具列表,加入新工具**

```java
// StageConfig.java s01() 方法中,把:
List.of(baseTools().get(0)),
// 改为:
List.of(baseTools().get(0), baseTools().get(4)),  // 加上 get_time
```

**步骤 5:编译运行,测试**
```bash
mvn compile
mvn exec:java -Dexec.mainClass=com.learnclaudecode.agents.S01AgentLoop
# 输入: what time is it?
# 观察模型是否调用 get_time 工具
```

#### 3. 写一个简单测试(无需 JUnit)

由于 `pom.xml` 当前没有 JUnit 依赖,可以写一个最简单的 main 方法测试:

```java
// 新建文件:src/test/java/com/learnclaudecode/tools/TodoManagerTest.java
package com.learnclaudecode.tools;

import java.util.List;
import java.util.Map;

public class TodoManagerTest {
    public static void main(String[] args) {
        TodoManager tm = new TodoManager();

        // 测试1:正常更新
        String result = tm.update(List.of(
            Map.of("id", "1", "text", "任务A", "status", "pending"),
            Map.of("id", "2", "text", "任务B", "status", "in_progress")
        ));
        System.out.println("测试1 - 正常更新:");
        System.out.println(result);
        assert result.contains("[>]") : "应有一个in_progress标记";
        assert result.contains("0/2 completed") : "应显示0/2";

        // 测试2:多个in_progress应报错
        try {
            tm.update(List.of(
                Map.of("id", "1", "text", "任务A", "status", "in_progress"),
                Map.of("id", "2", "text", "任务B", "status", "in_progress")
            ));
            System.out.println("测试2 失败: 应抛异常");
        } catch (IllegalArgumentException e) {
            System.out.println("测试2 通过: " + e.getMessage());
        }

        // 测试3:超过20个todo应报错
        try {
            List<Map<String, Object>> big = new java.util.ArrayList<>();
            for (int i = 0; i < 21; i++) {
                big.add(Map.of("id", String.valueOf(i), "text", "任务" + i, "status", "pending"));
            }
            tm.update(big);
            System.out.println("测试3 失败: 应抛异常");
        } catch (IllegalArgumentException e) {
            System.out.println("测试3 通过: " + e.getMessage());
        }

        System.out.println("\n所有测试完成");
    }
}
```

运行:
```bash
mvn exec:java -Dexec.mainClass=com.learnclaudecode.tools.TodoManagerTest
```

### Day 7 详细指引:PR 具体示例

#### PR 示例 1:为 StageConfig 字段补充 Javadoc

**目标文件**:`agents/StageConfig.java`

**改动内容**:为 record 的 9 个字段补充中文说明注释。例如在 `prompt` 字段上方加:
```java
/**
 * 终端提示符前缀,例如 "s01"、"s_full"。
 * 用户在终端看到的输入提示就是它加上 " >> "。
 */
String prompt,
```

**Commit message**:`docs: add Javadoc for StageConfig record fields`

#### PR 示例 2:为 WorkspacePaths 补充路径安全测试

**目标文件**:新建 `src/test/java/com/learnclaudecode/common/WorkspacePathsTest.java`

**测试内容**:
```java
// 测试 safeResolve 正常路径
Path safe = paths.safeResolve("src/main/java/Main.java");
assert safe.startsWith(paths.workdir());

// 测试 safeResolve 逃逸路径应抛异常
try {
    paths.safeResolve("../../etc/passwd");
    assert false : "应抛异常";
} catch (IllegalArgumentException e) {
    assert e.getMessage().contains("escapes workspace");
}
```

**Commit message**:`test: add path safety tests for WorkspacePaths.safeResolve`

#### PR 示例 3:新增 `list_env` 工具

**改动文件**:
1. `StageConfig.java` — 在 `baseTools()` 中添加工具定义
2. `AgentRuntime.java` — 在两个 switch 中添加 case

```java
// StageConfig.java baseTools() 中添加:
tools.add(tool("list_env", "List environment variable names (no values).",
    Map.of("type", "object", "properties", Map.of(), "required", List.of())));

// AgentRuntime.java switch 中添加:
case "list_env" -> System.getenv().keySet().stream()
    .filter(k -> !k.toLowerCase().contains("key") && !k.toLowerCase().contains("secret"))
    .reduce("", (a, b) -> a + "\n" + b);
```

**Commit message**:`feat: add list_env tool for environment inspection`

---

## 迁移到其他 Java 开源项目的方法

这份计划的框架可复用,迁移时只需做如下替换:

| 本项目概念 | 迁移到其他项目时的对应物 |
|-----------|------------------------|
| `README.md` | 目标项目的 README / 官方文档 |
| S01~S12 阶段入口 | 目标项目的 starter / example / quickstart |
| `AgentRuntime` 主循环 | 目标项目的核心流程(如请求处理链、任务调度器) |
| `StageConfig` 配置中心 | 目标项目的配置类 / auto-configuration / properties |
| `AnthropicClient` | 目标项目的外部依赖客户端(HTTP / DB / MQ) |
| 工具分发逻辑 | 目标项目的扩展点(SPI / 插件 / Factory) |

**通用阅读顺序永远是**:

```
README → 目录结构 → 入口类 → 装配类 → 配置类 → 核心运行时 → 外部交互层
→ 选一个模块深挖 → 动手改 → 提 PR
```
