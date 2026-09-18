---
kind: error_handling
name: Java Agent 运行时错误处理：异常上抛与字符串化结果混合模式
category: error_handling
scope:
    - '**'
source_files:
    - src/main/java/com/learnclaudecode/common/JsonUtils.java
    - src/main/java/com/learnclaudecode/common/AnthropicClient.java
    - src/main/java/com/learnclaudecode/common/WorkspacePaths.java
    - src/main/java/com/learnclaudecode/background/BackgroundManager.java
    - src/main/java/com/learnclaudecode/mcp/McpClient.java
    - src/main/java/com/learnclaudecode/agents/AgentRuntime.java
    - src/main/java/com/learnclaudecode/mcp/ToolPoolAssembler.java
---

## 1. 整体方法

该仓库没有定义统一的业务异常类型体系，也没有使用日志框架。错误处理采用**两种互补策略**：
- **基础设施层（common）**：通过 `JsonUtils`、`AnthropicClient` 等工具类把底层异常（`IOException`、`InterruptedException`、Jackson `JsonProcessingException`）统一包装为 `IllegalStateException` 向上抛出，调用方无需感知具体 IO/序列化细节。
- **Agent 交互层（agents/background/mcp/tools）**：对外暴露的 API（如 `BackgroundManager.run/check`、`McpClient.callTool/connect`、`AgentRuntime` 各阶段能力开关）不抛异常，而是返回以 `Error: ...` 开头的**人类可读字符串**，由上层 Agent 循环直接作为文本输出给模型或前端。

此外，`WorkspacePaths.safeResolve` 在检测到路径逃逸工作区时直接 `throw new IllegalArgumentException(...)`，作为安全边界校验；`BackgroundManager.execute` 中捕获所有异常后把状态写回任务表并投递通知，而不是让后台线程崩溃。

## 2. 关键文件与位置

| 文件 | 职责 | 错误处理方式 |
|---|---|---|
| `src/main/java/com/learnclaudecode/common/JsonUtils.java` | JSON 序列化/反序列化封装 | 捕获 `JsonProcessingException` → 抛 `IllegalStateException("JSON 序列化/反序列化失败", e)` |
| `src/main/java/com/learnclaudecode/common/AnthropicClient.java` | 调用 Anthropic-compatible HTTP API | HTTP ≥400 抛 `IllegalStateException`；IO/中断统一转成 `IllegalStateException` 并恢复中断标志 |
| `src/main/java/com/learnclaudecode/common/WorkspacePaths.java` | 工作区路径安全访问 | `safeResolve` 对 `../` 逃逸路径抛 `IllegalArgumentException`；目录创建失败静默忽略 |
| `src/main/java/com/learnclaudecode/background/BackgroundManager.java` | 后台命令执行 | `catch (Exception e)` 将状态设为 `error`，结果字段写入 `"Error: " + e.getMessage()`，并通过 `notifications` 队列上报 |
| `src/main/java/com/learnclaudecode/mcp/McpClient.java` | MCP 工具发现与调用 | `callTool` 捕获 `Exception` 返回 `"Error calling ..."` 字符串；未连接/未知 server 返回 `"Error: ..."` |
| `src/main/java/com/learnclaudecode/agents/AgentRuntime.java` | 各阶段能力开关 | 当某阶段未启用时返回 `"Error: xxx not enabled in this stage"` 文本 |
| `src/main/java/com/learnclaudecode/mcp/ToolPoolAssembler.java` | 工具名去重 | 冲突时 `System.err.println("WARNING: ...")` 打印告警 |

## 3. 架构约定

- **无自定义异常类**：仓库未定义任何 `extends Exception` 的业务异常类型，仅复用 JDK 标准异常（`IllegalArgumentException`、`IllegalStateException`、`IOException`）。
- **分层异常策略**：
  - 纯工具/基础设施方法（`JsonUtils.*`、`WorkspacePaths.readText/writeText`）选择**抛出受检/非受检异常**，由调用方决定如何处理。
  - 面向 Agent/用户可见的接口（`BackgroundManager`、`McpClient`、`AgentRuntime`）选择**返回字符串**，错误以 `Error:` 前缀标识，便于直接渲染到对话界面。
- **网络与序列化统一包装**：`AnthropicClient` 和 `JsonUtils` 是仓库中唯一两处把底层异常包装为 `IllegalStateException` 的地方，形成“基础设施异常上抛”的集中点。
- **后台任务隔离**：`BackgroundManager.execute` 用 `try/catch (Exception)` 包裹整个进程生命周期，确保单个命令失败不会导致后台线程退出，同时通过 `notifications` 队列把错误摘要推送到主循环。
- **安全边界校验前置**：`WorkspacePaths.safeResolve` 是所有文件读写的入口，先做路径规范化与白名单校验，再放行后续 I/O 操作。

## 4. 约定与约束

- **字符串错误格式**：Agent 层错误统一以 `Error:` 开头（见 `BackgroundManager`、`McpClient`、`AgentRuntime`），这是代码中反复出现的约定，用于让上层能区分正常输出与错误输出。
- **路径逃逸即异常**：`WorkspacePaths.safeResolve` 一旦发现 `resolved.startsWith(workdir)` 为假就立即抛 `IllegalArgumentException`，不允许静默降级——这是安全硬性约束。
- **HTTP 错误码即异常**：`AnthropicClient.createMessage` 对 `statusCode >= 400` 直接抛异常，不尝试解析响应体中的错误字段；只有成功响应才进入 JSON 反序列化流程。
- **后台任务超时强制终止**：`process.waitFor(timeout, SECONDS)` 返回 false 时调用 `destroyForcibly()`，避免失控子进程长期占用资源。
- **目录创建失败静默**：`ensureDir` 捕获 `IOException` 并忽略，保持 getter 语义轻量，真正的读写失败会在后续 `Files.readString/writeString` 处暴露。
- **无日志框架**：仓库未引入 SLF4J/Logback 等日志库，调试信息通过 `System.err.println`（`ToolPoolAssembler`）或直接拼接到返回字符串中呈现。
- **无 `panic/recover` 等价物**：Java 侧未使用 `Thread.stop` 或类似机制；异步错误通过 `BlockingQueue` 通知而非全局恢复。