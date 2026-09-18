# Java 开源项目 1 周学习计划(面向 Java 基础人群)

> **样本项目**:learn-claude-code-java(Claude Code 风格 Agent 的 Java 渐进式实现)
> **适用人群**:熟悉 Java 语法 / 集合 / 多线程,了解 Spring 基本概念,缺乏大型项目经验
> **总周期**:1 周(7 天),每天 2~3 小时,合计约 17~21 小时
> **核心方法论**:先理解项目做什么 → 再理解整体架构 → 最后深入核心代码
> **阶段总数**:17 个渐进阶段(S01 ~ S17) + 完整版(SFull)

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
| 复杂并发与线程安全细节 | `BackgroundManager`、`MessageBus`、`CronScheduler` 的并发控制 | 只需知道"它异步执行",不必深究线程模型 |
| JSON 序列化的边界 case | `JsonUtils`、`model/` 下的 DTO 注解 | 只需知道"它负责对象 ↔ JSON 转换" |
| 路径安全校验细节 | `WorkspacePaths` 的 `../` 防逃逸逻辑 | 知道"它管路径"即可,安全细节后面再看 |
| worktree 的 Git 操作细节 | `WorktreeManager` 的 lane 生命周期 | 只需理解"目录级隔离"这个概念 |
| 前端项目 `web/` | `web/src/**` | 展示用前端,与 Java 后端学习无关 |
| 工作流的 Resume 缓存细节 | `ExecutionState.computeSemanticKey()` | 知道"语义 key 实现幂等"即可 |

---

## 7 天总览表

| 天次 | 主题 | 覆盖阶段 | 核心任务 | 预计时长 |
|------|------|---------|---------|---------|
| Day 1 | 让 Agent 行动 | S01~S04 | Agent Loop + Tool Use + Permission + Hooks | 3h |
| Day 2 | 处理复杂工作 | S05, S06, S08 | TodoWrite + Subagent + Context Compact | 2~3h |
| Day 3 | 知识与记忆 | S07, S09 | Skill Loading + Memory System | 2~3h |
| Day 4 | 长时间任务 | S10~S12 | Task System + Background + Cron Scheduler | 2~3h |
| Day 5 | 多Agent协作 | S13 | Agent Teams 完整运行时 | 3h |
| Day 6 | 扩展与集成 | S14, S15 | MCP Plugin + Integrated Harness | 2~3h |
| Day 7 | 编排与目标 | S16, S17 | Workflow Runtime + Goal Loop + 总复习 | 3h |

---

## Day 1: 让 Agent 行动(S01~S04)

> **阶段目标**:理解 Agent 的最小闭环,掌握权限控制与钩子扩展两大基础设施。

### 每日学习目标表

| 序号 | 阶段 | 学习目标 | 核心代码 | 动手验证 |
|------|------|---------|---------|---------|
| 1.1 | S01 Agent Loop | 理解"对话→模型→bash→反馈"最小循环 | `S01AgentLoop.java`, `AgentRuntime.java` | 运行 S01,输入"列出当前目录文件" |
| 1.2 | S02 Tool Use | 理解工具注册与分发机制 | `S02ToolUse.java`, `CommandTools.java` | 让 Agent 读写一个文件 |
| 1.3 | S03 Permission | 理解三态权限控制 | `S03Permission.java`, `PermissionManager.java` | 尝试执行 `rm -rf /`,观察 deny 拦截 |
| 1.4 | S04 Hooks | 理解生命周期钩子注入 | `S04Hooks.java`, `HookManager.java` | 注册一个 PreToolUse 钩子,观察阻断效果 |

### 核心代码文件路径

```
src/main/java/com/learnclaudecode/
├── agents/S01AgentLoop.java          # 最小闭环入口
├── agents/S02ToolUse.java            # 文件工具入口
├── agents/S03Permission.java         # 权限系统入口
├── agents/S04Hooks.java              # 钩子系统入口
├── agents/AgentRuntime.java          # 主循环(全项目最关键)
├── agents/Launcher.java              # 统一启动器
├── agents/AppContext.java            # 服务装配器
├── agents/StageConfig.java           # 阶段配置中心
├── common/AnthropicClient.java       # 模型 API 调用
├── tools/CommandTools.java           # bash/文件工具实现
├── permission/PermissionManager.java # 权限三态检查
├── hooks/HookManager.java            # 钩子注册与触发
└── hooks/HookContext.java            # 钩子上下文
```

### 关键概念速记

| 概念 | 一句话解释 |
|------|-----------|
| Agent Loop | 模型思考→调用工具→执行→结果回传→再思考,循环直到完成 |
| tool_use | 模型不直接回答,而是请求"我要调用某工具,参数是 xxx" |
| PermissionRule | 一条权限规则:pattern + action(allow/deny/confirm) |
| HookType | 四种钩子:PreToolUse / PostToolUse / UserPromptSubmit / Stop |
| StageConfig | "档位选择器"——同一个运行时配不同 StageConfig = 不同阶段 |

### 阶段配置对比(S01~S04)

| 配置项 | S01 | S02 | S03 | S04 |
|--------|-----|-----|-----|-----|
| bash 工具 | ✓ | ✓ | ✓ | ✓ |
| 文件工具 | ✗ | ✓ | ✓ | ✓ |
| enablePermission | ✗ | ✗ | ✓ | ✓ |
| enableHooks | ✗ | ✗ | ✗ | ✓ |
| permission_list/set 工具 | ✗ | ✗ | ✓ | ✓ |
| hook_register/list/remove | ✗ | ✗ | ✗ | ✓ |

### 常见卡点

| 卡点 | 简化策略 |
|------|---------|
| `.env` 配置后跑不起来 | 检查 API Key 和 Base URL;暂跑不起来先读代码 |
| 不理解 tool_use | 通俗:模型说"帮我调 read_file",Java 去执行,再把结果喂回 |
| PermissionManager 代码太长 | 只看 `checkPermission()` 方法的三层检查逻辑 |

---

## Day 2: 处理复杂工作(S05, S06, S08)

> **阶段目标**:理解 Agent 如何处理多步骤复杂任务——规划、分治、压缩。

### 每日学习目标表

| 序号 | 阶段 | 学习目标 | 核心代码 | 动手验证 |
|------|------|---------|---------|---------|
| 2.1 | S05 TodoWrite | 理解待办清单的状态机 | `S05TodoWrite.java`, `TodoManager.java` | 让 Agent 创建一个 3 步计划并逐步标记完成 |
| 2.2 | S06 Subagent | 理解子代理的上下文隔离 | `S06Subagent.java`, `StageConfig.java` | 让 Agent 派生子代理处理子任务 |
| 2.3 | S08 Context Compact | 理解上下文压缩策略 | `S08ContextCompact.java`, `CompressionService.java` | 长对话后触发 compact,观察消息变化 |

### 核心代码文件路径

```
src/main/java/com/learnclaudecode/
├── agents/S05TodoWrite.java          # Todo 入口
├── agents/S06Subagent.java           # 子代理入口
├── agents/S08ContextCompact.java     # 上下文压缩入口
├── tools/TodoManager.java            # 待办清单管理器
└── context/CompressionService.java   # 压缩服务
```

### 关键概念速记

| 概念 | 一句话解释 |
|------|-----------|
| TodoManager | 维护 pending/in_progress/completed 状态机,限制同时 in_progress 数量 |
| Subagent | 派生一个新上下文的 Agent 处理子任务,结果回传主 Agent |
| subagentWritable | 子代理是否有文件写入权限(S06 启用) |
| ContextCompactor | token 超限时压缩早期对话,保留关键信息 |
| enableCompression | 压缩开关(S08 启用) |

### 阶段配置对比(S05~S08)

| 配置项 | S05 | S06 | S07 | S08 |
|--------|-----|-----|-----|-----|
| enableTodoNag | ✓ | ✓ | ✓ | ✓ |
| subagentWritable | ✗ | ✓ | ✓ | ✓ |
| enableCompression | ✗ | ✗ | ✗ | ✓ |
| todo_write 工具 | ✓ | ✓ | ✓ | ✓ |
| task(子代理) 工具 | ✗ | ✓ | ✓ | ✓ |
| compact 工具 | ✗ | ✗ | ✗ | ✓ |

### 常见卡点

| 卡点 | 简化策略 |
|------|---------|
| 不理解为什么需要 Todo | Agent 一次只能做一步,Todo 让它知道"接下来做什么" |
| 子代理与主代理的区别 | 子代理有独立上下文,不共享主代理的 messages |
| 压缩后信息丢失怎么办 | 压缩保留"摘要+关键结论",丢弃冗余过程 |

---

## Day 3: 知识与记忆(S07, S09)

> **阶段目标**:理解 Agent 如何获取外部知识(技能)和保持跨会话记忆。

### 每日学习目标表

| 序号 | 阶段 | 学习目标 | 核心代码 | 动手验证 |
|------|------|---------|---------|---------|
| 3.1 | S07 Skill Loading | 理解动态知识加载机制 | `S07SkillLoading.java`, `SkillLoader.java` | 在 skills/ 下创建技能文件,让 Agent 加载 |
| 3.2 | S09 Memory System | 理解跨会话持久记忆 | `S09MemorySystem.java`, `MemoryStore.java`, `MemoryRecord.java` | 添加记忆,重启后搜索验证持久化 |

### 核心代码文件路径

```
src/main/java/com/learnclaudecode/
├── agents/S07SkillLoading.java       # 技能加载入口
├── agents/S09MemorySystem.java       # 记忆系统入口
├── skills/SkillLoader.java           # 技能文件扫描与加载
├── memory/MemoryStore.java           # 记忆存储与检索
└── model/MemoryRecord.java           # 记忆数据模型

skills/                                # 技能文件目录
└── <skill-name>/SKILL.md             # 单个技能的说明文件
```

### 关键概念速记

| 概念 | 一句话解释 |
|------|-----------|
| SkillLoader | 扫描 skills/ 目录,把 SKILL.md 内容注入 system prompt |
| load_skill 工具 | 模型按需加载某个技能的详细内容 |
| MemoryRecord | 记忆条目:id + content + category + timestamps |
| 记忆分类 | fact(事实) / preference(偏好) / procedure(步骤) |
| .memory/ 目录 | JSON 文件持久化,跨重启存活 |
| 记忆整合 | Jaccard 相似度 > 0.8 时合并重复记忆 |

### 阶段配置对比(S07, S09)

| 配置项 | S07 | S09 |
|--------|-----|-----|
| load_skill 工具 | ✓ | ✓ |
| enableMemory | ✗ | ✓ |
| memory_add/search/delete/list | ✗ | ✓ |
| system prompt 注入技能描述 | ✓ | ✓ |
| system prompt 注入相关记忆 | ✗ | ✓ |

### 常见卡点

| 卡点 | 简化策略 |
|------|---------|
| 技能与记忆的区别 | 技能=外部知识文件(静态),记忆=Agent 自己积累的经验(动态) |
| MemoryStore 检索原理 | 关键词匹配 + Jaccard 相似度,不是向量搜索 |
| .memory/ 文件格式 | 每条记忆一个 JSON 文件,文件名=记忆 ID |

---

## Day 4: 长时间任务(S10~S12)

> **阶段目标**:理解 Agent 如何管理持久任务、异步执行和定时调度。

### 每日学习目标表

| 序号 | 阶段 | 学习目标 | 核心代码 | 动手验证 |
|------|------|---------|---------|---------|
| 4.1 | S10 Task System | 理解文件任务板与依赖管理 | `S10TaskSystem.java`, `TaskManager.java` | 创建任务、设依赖、更新状态 |
| 4.2 | S11 Background Tasks | 理解异步执行与通知队列 | `S11BackgroundTasks.java`, `BackgroundManager.java` | 提交长命令到后台,轮询状态 |
| 4.3 | S12 Cron Scheduler | 理解定时调度与 prompt 注入 | `S12CronScheduler.java`, `CronScheduler.java`, `CronJob.java` | 注册 every_30s 任务,观察自动触发 |

### 核心代码文件路径

```
src/main/java/com/learnclaudecode/
├── agents/S10TaskSystem.java         # 任务系统入口
├── agents/S11BackgroundTasks.java    # 后台任务入口
├── agents/S12CronScheduler.java      # 定时调度入口
├── tasks/TaskManager.java            # 文件任务板
├── tasks/WorktreeManager.java        # 工作树管理
├── background/BackgroundManager.java # 异步执行管理器
├── scheduler/CronScheduler.java      # 定时调度器
└── model/CronJob.java                # 定时任务数据模型

持久化目录:
├── .tasks/                           # 任务 JSON 文件
└── .crons/                           # 定时任务 JSON 文件
```

### 关键概念速记

| 概念 | 一句话解释 |
|------|-----------|
| TaskManager | 基于文件的轻量任务板,支持 CRUD + 依赖 + 原子认领 |
| 任务状态机 | pending → in_progress → completed/failed |
| BackgroundManager | 长命令放入后台线程,不阻塞主循环 |
| drainCompletedResults() | 每轮循环开始时取出已完成的后台结果注入 messages |
| CronScheduler | 守护线程每秒 tick,检查 nextTriggerAt 是否到期 |
| pendingPrompts 队列 | LinkedBlockingQueue,调度线程→主循环线程的跨线程通信 |
| 简化 cron 表达式 | every_Ns / every_Nm / every_Nh / at_HH:MM |

### 阶段配置对比(S10~S12)

| 配置项 | S10 | S11 | S12 |
|--------|-----|-----|-----|
| task_create/update/list/get | ✓ | ✓ | ✓ |
| enableBackground | ✗ | ✓ | ✓ |
| background_run/check | ✗ | ✓ | ✓ |
| enableCron | ✗ | ✗ | ✓ |
| schedule_cron/list_crons/cancel_cron | ✗ | ✗ | ✓ |

### 常见卡点

| 卡点 | 简化策略 |
|------|---------|
| 任务依赖 blockedBy 怎么用 | A blockedBy B = B 完成前 A 不能开始 |
| 后台任务结果什么时候出现 | 下一轮循环开始时自动注入(drain) |
| cron 守护线程会不会阻止 JVM 退出 | 不会,设置为 daemon 线程 |

---

## Day 5: 多Agent协作(S13)

> **阶段目标**:理解完整的 Lead + Teammates 运行时,掌握消息总线、原子认领与 Worktree 隔离。

### 每日学习目标表

| 序号 | 主题 | 学习目标 | 核心代码 | 动手验证 |
|------|------|---------|---------|---------|
| 5.1 | MessageBus | 理解文件型消息总线 | `MessageBus.java` | spawn 队友并发消息 |
| 5.2 | TeammateManager | 理解队友生命周期管理 | `TeammateManager.java` | 观察队友 spawn → work → shutdown |
| 5.3 | 原子认领 | 理解 claim_task 的并发安全 | `TaskManager.java` | 多队友同时扫描,验证不重复认领 |
| 5.4 | Worktree 隔离 | 理解任务级目录隔离 | `WorktreeManager.java` | 创建 worktree,观察 .worktrees/ |
| 5.5 | 治理协议 | 理解 shutdown/plan_approval | `TeammateManager.java` | 发送 shutdown_request,观察优雅退出 |

### 核心代码文件路径

```
src/main/java/com/learnclaudecode/
├── agents/S13AgentTeams.java         # 多 Agent 入口
├── team/TeammateManager.java         # 队友生命周期管理
├── team/MessageBus.java              # 文件型消息总线
├── tasks/WorktreeManager.java        # 工作树管理器
├── tasks/TaskManager.java            # 任务板(提供 claim)
└── model/TeamMessage.java            # 消息数据模型

持久化目录:
├── .team/inbox/<agent-name>/         # 各 Agent 收件箱
└── .worktrees/<task-id>/             # 任务工作树
```

### 关键概念速记

| 概念 | 一句话解释 |
|------|-----------|
| Lead + Teammates | Lead 分配任务,Teammates 执行,通过 inbox 通信 |
| MessageBus | 文件系统实现的低耦合消息传递(JSONL 追加写入) |
| spawn_teammate | 创建一个持久队友(独立线程+独立上下文) |
| claim_task | 原子认领:synchronized 保证不重复 |
| Worktree lane | 为每个任务绑定独立工作目录 |
| shutdown_request | Lead 请求队友关闭,队友完成后优雅退出 |
| plan_approval | 队友提交方案,Lead 审批后才执行(Plan gate) |
| 自治循环 | idle → scanUnclaimed → claim → work → idle |

### S13 工具列表(完整)

| 工具名 | 功能 |
|--------|------|
| spawn_teammate | 创建持久队友 |
| list_teammates | 列出所有队友 |
| send_message | 向指定队友发消息 |
| read_inbox | 读取自己的收件箱 |
| broadcast | 向所有队友广播 |
| shutdown_request | 请求队友关闭 |
| plan_approval | 审批队友计划 |
| claim_task | 原子认领任务 |
| idle | 进入空闲等待 |
| worktree_create | 为任务创建工作树 |
| worktree_list | 列出所有工作树 |
| worktree_remove | 删除工作树 |
| worktree_events | 查看工作树事件 |

### 常见卡点

| 卡点 | 简化策略 |
|------|---------|
| 消息总线的文件结构 | `.team/inbox/agent-name/messages.jsonl`,一行一条 JSON |
| read_inbox 为什么是"消费即删除" | 避免重复处理,类似消息队列的 ack 语义 |
| 自治队友什么时候启动 | autonomousTeammates=true 时,队友自动进入 idle-scan-claim 循环 |

---

## Day 6: 扩展与集成(S14, S15)

> **阶段目标**:理解 MCP 动态工具发现,验证全部能力的集成运行。

### 每日学习目标表

| 序号 | 阶段 | 学习目标 | 核心代码 | 动手验证 |
|------|------|---------|---------|---------|
| 6.1 | S14 MCP Plugin | 理解 MCP 协议的发现+调用模型 | `S14McpPlugin.java`, `McpClient.java`, `MockMcpServer.java`, `ToolPoolAssembler.java` | connect_mcp("docs"),然后调用 mcp__docs__search_docs |
| 6.2 | S15 Integrated | 理解全部能力如何协同 | `S15IntegratedHarness.java`, `StageConfig.s15()` | 运行 S15,验证权限+钩子+记忆+团队+MCP 同时工作 |

### 核心代码文件路径

```
src/main/java/com/learnclaudecode/
├── agents/S14McpPlugin.java          # MCP 插件入口
├── agents/S15IntegratedHarness.java  # 集成运行时入口
├── mcp/McpClient.java                # MCP 客户端(连接+发现+代理调用)
├── mcp/MockMcpServer.java            # 模拟 MCP 服务端(docs/deploy)
└── mcp/ToolPoolAssembler.java        # 工具池合并(内置+MCP)
```

### 关键概念速记

| 概念 | 一句话解释 |
|------|-----------|
| MCP | Model Context Protocol,运行时动态发现外部工具的协议 |
| 命名空间 | `mcp__<server>__<tool>`,避免不同 server 的同名工具冲突 |
| ToolPoolAssembler | 合并内置工具与 MCP 工具(内置优先、名称限长 64 字符) |
| MockMcpServer | 进程内模拟的 MCP 服务(docs/deploy 两个示例 server) |
| S15 集成运行时 | 不引入新机制,把 S01-S14 的全部开关打开验证协同 |
| dedupe(tools) | 按 name 字段去重,保留首次出现的工具定义 |

### 阶段配置对比(S14, S15)

| 配置项 | S14 | S15 |
|--------|-----|-----|
| enableMcp | ✓ | ✓ |
| connect_mcp 工具 | ✓ | ✓ |
| enablePermission | ✓ | ✓ |
| enableHooks | ✓ | ✓ |
| enableMemory | ✓ | ✓ |
| enableCron | ✓ | ✓ |
| enableBackground | ✓ | ✓ |
| enableInbox | ✓ | ✓ |
| autonomousTeammates | ✓ | ✓ |
| 全部能力同时启用 | 部分 | ✓(全部) |

### S15 完整流水线

```
用户输入 → UserPromptSubmit 钩子
         → Cron prompt 注入 + Background 结果注入
         → Memory 检索 → 注入 system prompt
         → Skill 加载 → 注入 system prompt
         → ToolPoolAssembler.assemble() → 工具池
         → ContextCompactor → token 超限时压缩
         → AnthropicClient.createMessage() → 模型推理
         → tool_use → PreToolUse 钩子 → Permission 检查
         → ToolDispatcher → (MCP 路由 / 本地执行)
         → PostToolUse 钩子
         → tool_result → 回到模型推理
         → end_turn → 输出回复
```

### 常见卡点

| 卡点 | 简化策略 |
|------|---------|
| MCP 与直接加工具有什么区别 | MCP=运行时动态发现,不修改源码;直接加工具=编译时固定 |
| S15 工具太多记不住 | 不需要记,只需理解"全开关验证协同"的设计意图 |
| ToolPoolAssembler 的去重规则 | 内置优先、名称≤64字符、规范化后碰撞检测 |

---

## Day 7: 编排与目标(S16, S17) + 总复习

> **阶段目标**:理解确定性编排与自主终止判断,完成全部 17 阶段的总复习。

### 每日学习目标表

| 序号 | 阶段 | 学习目标 | 核心代码 | 动手验证 |
|------|------|---------|---------|---------|
| 7.1 | S16 Workflow | 理解工作流的编排原语与 Resume | `S16WorkflowRuntime.java`, `WorkflowEngine.java`, `ExecutionState.java`, `WorkflowDefinition.java` | 执行 review-changes 工作流 |
| 7.2 | S17 Goal Loop | 理解独立评判者的终止判断 | `S17GoalLoop.java`, `GoalController.java`, `GoalEvaluator.java`, `GoalState.java`, `GoalDecision.java` | 设定目标,观察自动循环与 pass |
| 7.3 | 总复习 | 串联全部 17 阶段 | `StageConfig.java`(全文件) | 画一张完整的能力演进图 |

### 核心代码文件路径

```
src/main/java/com/learnclaudecode/
├── agents/S16WorkflowRuntime.java    # 工作流入口
├── agents/S17GoalLoop.java           # 目标循环入口
├── workflow/WorkflowEngine.java      # 工作流引擎(注册+执行+持久化+Resume)
├── workflow/ExecutionState.java      # 执行状态(编排原语+Journal)
├── workflow/WorkflowDefinition.java  # 工作流定义 record
├── goal/GoalController.java          # 目标控制器
├── goal/GoalEvaluator.java           # 独立评判者
├── goal/GoalState.java               # 目标状态 record
└── goal/GoalDecision.java            # 评判结果 record

持久化目录:
└── .runtime/                          # 工作流 journal + output
    ├── <runId>.journal.jsonl
    └── <runId>.output.json
```

### 关键概念速记

| 概念 | 一句话解释 |
|------|-----------|
| WorkflowDefinition | record(name, description, phases, script),编排形状的代码化 |
| WorkflowScript | 函数式接口,用 Java 代码定义编排逻辑 |
| ExecutionState | 一次运行的"工作台+账本",提供 5 个编排原语 |
| phase() | 进入新阶段 |
| agent() | 执行一个 agent 步骤(带语义 key 缓存) |
| parallel() | 并行执行一组 agent 任务 |
| pipeline() | 每个条目独立流经所有阶段 |
| Journal | 逐条记录每步的语义 key 与结果,支撑 Resume |
| 确定性语义 key | kind|label|prompt|schema → hashCode → 10位数字,相同输入=相同 key |
| Resume | 从磁盘加载 journal,agent() 命中缓存跳过已完成步骤 |
| GoalController | 管理目标生命周期,驱动评判,实施安全出口 |
| GoalEvaluator | 独立模型调用(无工具),判断目标是否达成 |
| GoalDecision | pass(达成)/block(继续)/defer(等待)/impossible(不可达) |
| MAX_CONSECUTIVE_BLOCKS | 安全出口:连续 10 次 block 后强制 pass |

### 阶段配置对比(S16, S17)

| 配置项 | S16 | S17 |
|--------|-----|-----|
| 继承 S15 全部能力 | ✓ | ✓ |
| enableWorkflow | ✓ | ✓ |
| workflow 工具 | ✓ | ✓ |
| enableGoalLoop | ✗ | ✓ |
| goal_set/goal_status/goal_clear | ✗ | ✓ |

### 常见卡点

| 卡点 | 简化策略 |
|------|---------|
| 工作流与普通对话的区别 | 工作流=确定性步骤序列(代码定义);对话=模型即兴推理 |
| Resume 怎么知道从哪继续 | 语义 key 命中=已做过,跳过;未命中=从这里继续 |
| 评判者与执行者的区别 | 评判者只看历史不执行工具;执行者有全部工具 |
| 安全出口为什么是 10 次 | 经验值:够尝试多种策略,又不会无限消耗 |

---

## 全部 17 阶段配置对比总表

| 阶段 | 名称 | 核心开关 | 新增工具 |
|------|------|---------|---------|
| S01 | Agent Loop | (基础) | bash |
| S02 | Tool Use | (基础) | read_file, write_file, edit_file, list_dir, grep_search, glob_search |
| S03 | Permission | enablePermission | permission_list, permission_set |
| S04 | Hooks | enableHooks | hook_register, hook_list, hook_remove |
| S05 | TodoWrite | enableTodoNag | todo_write |
| S06 | Subagent | subagentWritable | task(子代理) |
| S07 | Skill Loading | (技能注入) | load_skill |
| S08 | Context Compact | enableCompression | compact |
| S09 | Memory | enableMemory | memory_add, memory_search, memory_delete, memory_list |
| S10 | Task System | (任务板) | task_create, task_update, task_list, task_get |
| S11 | Background | enableBackground | background_run, background_check |
| S12 | Cron | enableCron | schedule_cron, list_crons, cancel_cron |
| S13 | Teams | enableInbox, autonomousTeammates | spawn_teammate, list_teammates, send_message, read_inbox, broadcast, shutdown_request, plan_approval, claim_task, idle, worktree_create/list/remove/events |
| S14 | MCP | enableMcp | connect_mcp + 动态发现工具 |
| S15 | Integrated | 全部开关 | (合并 S13+S14 工具池) |
| S16 | Workflow | enableWorkflow | workflow |
| S17 | Goal Loop | enableGoalLoop | goal_set, goal_status, goal_clear |

---

## 能力演进路线图

```
Day 1: 基础能力
┌─────────────────────────────────────────────────────────┐
│ S01 最小循环 → S02 文件工具 → S03 权限 → S04 钩子       │
│ "能行动"        "能操作"       "有边界"   "可扩展"       │
└─────────────────────────────────────────────────────────┘
         ↓
Day 2: 复杂任务处理
┌─────────────────────────────────────────────────────────┐
│ S05 Todo 规划 → S06 子代理分治 → S08 上下文压缩         │
│ "能计划"         "能分身"          "能持续"              │
└─────────────────────────────────────────────────────────┘
         ↓
Day 3: 知识获取
┌─────────────────────────────────────────────────────────┐
│ S07 技能加载 → S09 记忆系统                             │
│ "能学习"        "能记住"                                │
└─────────────────────────────────────────────────────────┘
         ↓
Day 4: 持久化与异步
┌─────────────────────────────────────────────────────────┐
│ S10 任务板 → S11 后台执行 → S12 定时调度                │
│ "能管理"     "能异步"        "能定时"                    │
└─────────────────────────────────────────────────────────┘
         ↓
Day 5: 多Agent协作
┌─────────────────────────────────────────────────────────┐
│ S13 完整团队运行时                                       │
│ "能协作"(消息+认领+隔离+治理)                           │
└─────────────────────────────────────────────────────────┘
         ↓
Day 6: 动态扩展
┌─────────────────────────────────────────────────────────┐
│ S14 MCP 插件 → S15 集成验证                             │
│ "能扩展"       "能协同"                                 │
└─────────────────────────────────────────────────────────┘
         ↓
Day 7: 高阶控制
┌─────────────────────────────────────────────────────────┐
│ S16 工作流编排 → S17 目标循环                            │
│ "能确定性执行"   "能自主终止"                            │
└─────────────────────────────────────────────────────────┘
```

---

## 动手验证检查点

每天结束前,用以下检查点验证学习效果:

### Day 1 检查点
- [ ] 能一句话说清 Agent Loop 的闭环流程
- [ ] 能解释 tool_use 与普通文本回复的区别
- [ ] 能说出 PermissionManager 的三层检查顺序
- [ ] 能描述 PreToolUse 钩子如何阻断工具执行

### Day 2 检查点
- [ ] 能画出 Todo 的状态机(pending → in_progress → completed)
- [ ] 能解释子代理为什么需要独立上下文
- [ ] 能说出上下文压缩的触发时机和保留策略

### Day 3 检查点
- [ ] 能描述 SkillLoader 如何扫描和加载技能文件
- [ ] 能区分技能(静态外部知识)与记忆(动态积累经验)
- [ ] 能说出 MemoryStore 的三种分类和持久化方式

### Day 4 检查点
- [ ] 能画出任务状态机(pending → in_progress → completed/failed)
- [ ] 能解释后台任务的"提交-轮询-注入"流程
- [ ] 能描述 CronScheduler 的 tick 循环和 pendingPrompts 队列

### Day 5 检查点
- [ ] 能画出 Lead-Teammate 的消息交互时序图
- [ ] 能解释原子认领(claim_task)的 synchronized 保护
- [ ] 能说出 Worktree 隔离的价值和生命周期

### Day 6 检查点
- [ ] 能描述 MCP 的"连接→发现→调用"三阶段
- [ ] 能解释命名空间 `mcp__server__tool` 的设计意图
- [ ] 能画出 S15 的完整流水线(标注各子系统介入点)

### Day 7 检查点
- [ ] 能说出工作流 5 个编排原语的语义区别
- [ ] 能解释确定性语义 key 如何保证 Resume 幂等
- [ ] 能描述 GoalDecision 四种判定的运行时行为
- [ ] 能解释 MAX_CONSECUTIVE_BLOCKS 安全出口的必要性
- [ ] 能画出 17 阶段的完整能力演进图

---

## 附录 A: 核心设计思想总结

### 1. 运行时和能力配置分离

`AgentRuntime` 只负责"怎么跑",`StageConfig` 负责"能做什么"。同一个运行时配不同 StageConfig = 不同阶段。

### 2. 工具是 Agent 的执行器官

大模型只负责决策。执行命令、读写文件、更新 Todo、管理任务、收发消息——这些都是本地 Java 代码做的。

### 3. 消息历史是 Agent 的工作记忆

用户输入、工具结果、队友消息、后台任务结果、Cron prompt——都进入 messages,Agent 依赖它做决策。

### 4. 长任务必须有状态管理

Todo、Task、Worktree、Memory、Cron 都在解决同一个问题:当任务变长变复杂时,Agent 不能只靠一轮对话记住所有事。

### 5. 多 Agent 协作 = 协议 + 状态 + 通信

文件 inbox、JSON 任务板、状态字段、简单协议——不需要复杂消息队列就能实现多 Agent 协作。

### 6. 可组合性是架构的核心验证

S15 把全部开关打开——如果能跑通,说明 14 种机制是正交的、可组合的。

### 7. 确定性编排优于即兴推理

S16 的工作流把"每次都要模型推理的多步流程"沉淀为代码——更可靠、可恢复、可复现。

### 8. 评判者与执行者必须分离

S17 的目标循环让一个"无工具的独立评判者"决定是否完成——避免执行者既当运动员又当裁判。

---

## 附录 B: 快速运行指引

### 环境准备

1. 配置 `.env`(复制 `.env.example`):
   - `ANTHROPIC_API_KEY`:你的 API Key
   - `MODEL_ID`:模型名称
   - `ANTHROPIC_BASE_URL`:兼容服务地址(可选)

2. 确保 Java 17 + Maven 已安装

### 编译项目

```bash
mvn compile
```

### 运行各阶段

```bash
# Day 1: 基础能力
mvn exec:java -Dexec.mainClass=com.learnclaudecode.agents.S01AgentLoop
mvn exec:java -Dexec.mainClass=com.learnclaudecode.agents.S02ToolUse
mvn exec:java -Dexec.mainClass=com.learnclaudecode.agents.S03Permission
mvn exec:java -Dexec.mainClass=com.learnclaudecode.agents.S04Hooks

# Day 2: 复杂任务
mvn exec:java -Dexec.mainClass=com.learnclaudecode.agents.S05TodoWrite
mvn exec:java -Dexec.mainClass=com.learnclaudecode.agents.S06Subagent
mvn exec:java -Dexec.mainClass=com.learnclaudecode.agents.S08ContextCompact

# Day 3: 知识与记忆
mvn exec:java -Dexec.mainClass=com.learnclaudecode.agents.S07SkillLoading
mvn exec:java -Dexec.mainClass=com.learnclaudecode.agents.S09MemorySystem

# Day 4: 长时间任务
mvn exec:java -Dexec.mainClass=com.learnclaudecode.agents.S10TaskSystem
mvn exec:java -Dexec.mainClass=com.learnclaudecode.agents.S11BackgroundTasks
mvn exec:java -Dexec.mainClass=com.learnclaudecode.agents.S12CronScheduler

# Day 5: 多Agent协作
mvn exec:java -Dexec.mainClass=com.learnclaudecode.agents.S13AgentTeams

# Day 6: 扩展与集成
mvn exec:java -Dexec.mainClass=com.learnclaudecode.agents.S14McpPlugin
mvn exec:java -Dexec.mainClass=com.learnclaudecode.agents.S15IntegratedHarness

# Day 7: 编排与目标
mvn exec:java -Dexec.mainClass=com.learnclaudecode.agents.S16WorkflowRuntime
mvn exec:java -Dexec.mainClass=com.learnclaudecode.agents.S17GoalLoop

# 完整版
mvn exec:java -Dexec.mainClass=com.learnclaudecode.agents.SFull
```

---

## 附录 C: 持久化目录速查

| 目录 | 来源阶段 | 用途 |
|------|---------|------|
| `.tasks/` | S10 | 任务 JSON 文件 |
| `.team/inbox/` | S13 | 消息总线收件箱 |
| `.worktrees/` | S13 | 工作树隔离目录 |
| `.memory/` | S09 | 记忆 JSON 文件 |
| `.crons/` | S12 | 定时任务 JSON 文件 |
| `.runtime/` | S16 | 工作流 journal + output |
| `skills/` | S07 | 技能说明文件(SKILL.md) |
| `transcripts/` | S01+ | 对话历史记录 |
