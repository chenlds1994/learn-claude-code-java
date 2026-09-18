package com.learnclaudecode.agents;

import com.learnclaudecode.skills.SkillLoader;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 阶段配置，控制不同教学脚本暴露的能力。
 *
 * 这个类是整个教学项目最关键的“课程编排器”之一。
 * 它表达了 Claude Code 风格 Agent 的一个核心思想：
 * Agent 的能力并不是写死在代码里的，而是由运行时配置决定的。
 *
 * 在这个项目中，一个 StageConfig 主要回答 3 个问题：
 * 1. 当前阶段给模型什么 system prompt；
 * 2. 当前阶段允许模型使用哪些工具；
 * 3. 当前阶段是否打开 Todo、压缩、后台、团队、自主认领、权限、
 *    Hook、记忆、定时、MCP、工作流、目标循环等高级机制。
 *
 * 因此，从 s01 到 s17 的演进，本质上不是“换了一套 Agent”，
 * 而是“在同一个 AgentRuntime 上逐步打开更多能力开关”。
 */
public record StageConfig(
        String prompt,
        boolean enableTodoNag,
        boolean enableCompression,
        boolean enableBackground,
        boolean enableInbox,
        boolean subagentWritable,
        boolean autonomousTeammates,
        boolean enablePermission,
        boolean enableHooks,
        boolean enableMemory,
        boolean enableCron,
        boolean enableMcp,
        boolean enableWorkflow,
        boolean enableGoalLoop,
        List<Map<String, Object>> tools,
        String systemTemplate
) {
    /**
     * 根据当前工作区与可用技能生成本阶段实际生效的 system prompt。
     *
     * @param skillLoader 技能加载器
     * @param workdir 当前工作区路径
     * @return 展开占位符后的 system prompt
     */
    public String systemPrompt(SkillLoader skillLoader, Path workdir) {
        // systemTemplate 中保留占位符，运行时再按当前工作区和可用技能动态展开。
        return systemTemplate
                .replace("${WORKDIR}", workdir.toString())
                .replace("${SKILLS}", skillLoader.getDescriptions());
    }

    /**
     * 返回最基础的文件与命令工具集合。
     *
     * @return 基础工具定义列表
     */
    public static List<Map<String, Object>> baseTools() {
        // baseTools 对应最基础的文件/命令操作能力。
        // 这是最小可工作的 coding agent 工具集：
        // - bash：执行命令
        // - read_file：查看代码
        // - write_file：新建或覆盖文件
        // - edit_file：对已有文件做精确替换
        // 从 Agent 视角看，这些工具就是它的“手脚”。
        List<Map<String, Object>> tools = new ArrayList<>();
        tools.add(tool("bash", "Run a shell command.", Map.of("type", "object", "properties", Map.of("command", Map.of("type", "string")), "required", List.of("command"))));
        tools.add(tool("read_file", "Read file contents.", Map.of("type", "object", "properties", Map.of("path", Map.of("type", "string"), "limit", Map.of("type", "integer")), "required", List.of("path"))));
        tools.add(tool("write_file", "Write content to file.", Map.of("type", "object", "properties", Map.of("path", Map.of("type", "string"), "content", Map.of("type", "string")), "required", List.of("path", "content"))));
        tools.add(tool("edit_file", "Replace exact text in file.", Map.of("type", "object", "properties", Map.of("path", Map.of("type", "string"), "old_text", Map.of("type", "string"), "new_text", Map.of("type", "string")), "required", List.of("path", "old_text", "new_text"))));
        return tools;
    }

    /**
     * 阶段配置构建器。
     *
     * StageConfig 的字段已经扩展到 16 个，如果继续使用位置参数构造，
     * 调用方需要写出一长串 true/false，既难读又极易写错顺序。
     * Builder 用“链式具名设置”替代“位置参数”，让每个阶段的配置一目了然：
     * 没有显式设置的开关默认为 false，工具默认为空列表。
     */
    public static class Builder {
        private String prompt;
        private boolean enableTodoNag, enableCompression, enableBackground;
        private boolean enableInbox, subagentWritable, autonomousTeammates;
        private boolean enablePermission, enableHooks, enableMemory;
        private boolean enableCron, enableMcp, enableWorkflow, enableGoalLoop;
        private List<Map<String, Object>> tools = new ArrayList<>();
        private String systemTemplate = "";

        /**
         * 使用阶段名初始化构建器。
         *
         * @param prompt 阶段名（REPL 提示符前缀）
         */
        public Builder(String prompt) {
            this.prompt = prompt;
        }

        /**
         * 设置是否开启 todo 提醒机制。
         *
         * @param value 开关值
         * @return 当前构建器
         */
        public Builder enableTodoNag(boolean value) {
            this.enableTodoNag = value;
            return this;
        }

        /**
         * 设置是否开启上下文压缩。
         *
         * @param value 开关值
         * @return 当前构建器
         */
        public Builder enableCompression(boolean value) {
            this.enableCompression = value;
            return this;
        }

        /**
         * 设置是否开启后台任务。
         *
         * @param value 开关值
         * @return 当前构建器
         */
        public Builder enableBackground(boolean value) {
            this.enableBackground = value;
            return this;
        }

        /**
         * 设置是否开启团队 inbox 轮询。
         *
         * @param value 开关值
         * @return 当前构建器
         */
        public Builder enableInbox(boolean value) {
            this.enableInbox = value;
            return this;
        }

        /**
         * 设置子代理是否可写文件。
         *
         * @param value 开关值
         * @return 当前构建器
         */
        public Builder subagentWritable(boolean value) {
            this.subagentWritable = value;
            return this;
        }

        /**
         * 设置队友是否具备自治认领能力。
         *
         * @param value 开关值
         * @return 当前构建器
         */
        public Builder autonomousTeammates(boolean value) {
            this.autonomousTeammates = value;
            return this;
        }

        /**
         * 设置是否开启工具权限系统。
         *
         * @param value 开关值
         * @return 当前构建器
         */
        public Builder enablePermission(boolean value) {
            this.enablePermission = value;
            return this;
        }

        /**
         * 设置是否开启生命周期 Hook。
         *
         * @param value 开关值
         * @return 当前构建器
         */
        public Builder enableHooks(boolean value) {
            this.enableHooks = value;
            return this;
        }

        /**
         * 设置是否开启持久记忆。
         *
         * @param value 开关值
         * @return 当前构建器
         */
        public Builder enableMemory(boolean value) {
            this.enableMemory = value;
            return this;
        }

        /**
         * 设置是否开启定时任务。
         *
         * @param value 开关值
         * @return 当前构建器
         */
        public Builder enableCron(boolean value) {
            this.enableCron = value;
            return this;
        }

        /**
         * 设置是否开启 MCP 插件。
         *
         * @param value 开关值
         * @return 当前构建器
         */
        public Builder enableMcp(boolean value) {
            this.enableMcp = value;
            return this;
        }

        /**
         * 设置是否开启工作流运行时。
         *
         * @param value 开关值
         * @return 当前构建器
         */
        public Builder enableWorkflow(boolean value) {
            this.enableWorkflow = value;
            return this;
        }

        /**
         * 设置是否开启目标循环。
         *
         * @param value 开关值
         * @return 当前构建器
         */
        public Builder enableGoalLoop(boolean value) {
            this.enableGoalLoop = value;
            return this;
        }

        /**
         * 设置本阶段暴露的工具列表。
         *
         * @param value 工具定义列表
         * @return 当前构建器
         */
        public Builder tools(List<Map<String, Object>> value) {
            this.tools = value;
            return this;
        }

        /**
         * 设置本阶段的 system prompt 模板。
         *
         * @param value 模板内容
         * @return 当前构建器
         */
        public Builder systemTemplate(String value) {
            this.systemTemplate = value;
            return this;
        }

        /**
         * 构建最终的阶段配置。
         *
         * @return 不可变的 StageConfig 实例
         */
        public StageConfig build() {
            return new StageConfig(prompt,
                    enableTodoNag, enableCompression, enableBackground,
                    enableInbox, subagentWritable, autonomousTeammates,
                    enablePermission, enableHooks, enableMemory,
                    enableCron, enableMcp, enableWorkflow, enableGoalLoop,
                    tools, systemTemplate);
        }
    }

    /**
     * 创建以指定阶段名开头的构建器。
     *
     * @param prompt 阶段名（REPL 提示符前缀）
     * @return 新的 Builder 实例
     */
    public static Builder builder(String prompt) {
        return new Builder(prompt);
    }

    /**
     * 构建 s01 最小闭环阶段配置。
     *
     * @return s01 阶段配置
     */
    public static StageConfig s01() {
        // s01 只开放 bash，目的是让读者先理解最原始的“模型思考 + 命令执行”闭环。
        return builder("s01")
                .tools(List.of(baseTools().get(0)))
                .systemTemplate("You are a coding agent at ${WORKDIR}. Use bash to solve tasks. Act, don't explain.")
                .build();
    }

    /**
     * 构建 s02 文件工具阶段配置。
     *
     * @return s02 阶段配置
     */
    public static StageConfig s02() {
        // s02 在 s01 基础上加入文件工具，Agent 开始能直接读写项目内容。
        return builder("s02")
                .tools(baseTools())
                .systemTemplate("You are a coding agent at ${WORKDIR}. Use tools to solve tasks. Act, don't explain.")
                .build();
    }

    /**
     * 构建 s03 权限系统阶段配置。
     *
     * @return s03 阶段配置
     */
    public static StageConfig s03() {
        List<Map<String, Object>> tools = new ArrayList<>(s02().tools());
        // s03 引入权限层：工具不再“默认可用”，而是由 allow/deny/confirm 策略显式管控。
        tools.add(tool("permission_list", "List all permission rules.", Map.of("type", "object", "properties", Map.of())));
        tools.add(tool("permission_set", "Set a permission policy for tools matching a pattern.", Map.of("type", "object", "properties", Map.of("tool_pattern", Map.of("type", "string"), "policy", Map.of("type", "string", "enum", List.of("allow", "deny", "confirm"))), "required", List.of("tool_pattern", "policy"))));
        return builder("s03")
                .enablePermission(true)
                .tools(dedupe(tools))
                .systemTemplate("You are a coding agent at ${WORKDIR}. Tool use is governed by permission rules. Check permission_list before risky actions, and use permission_set to adjust policies (allow/deny/confirm) when the user grants or revokes access.")
                .build();
    }

    /**
     * 构建 s04 生命周期 Hook 阶段配置。
     *
     * @return s04 阶段配置
     */
    public static StageConfig s04() {
        List<Map<String, Object>> tools = new ArrayList<>(s03().tools());
        // s04 引入 Hook：在工具调用前后等生命周期节点插入确定性动作，
        // 让 Agent 的行为可以被“拦截、审计、增强”，而不必修改运行时代码。
        tools.add(tool("hook_register", "Register a lifecycle hook.", Map.of("type", "object", "properties", Map.of("type", Map.of("type", "string", "enum", List.of("PreToolUse", "PostToolUse", "UserPromptSubmit", "Stop")), "action", Map.of("type", "string")), "required", List.of("type", "action"))));
        tools.add(tool("hook_list", "List all registered hooks.", Map.of("type", "object", "properties", Map.of())));
        tools.add(tool("hook_remove", "Remove a registered hook.", Map.of("type", "object", "properties", Map.of("hook_id", Map.of("type", "string")), "required", List.of("hook_id"))));
        return builder("s04")
                .enablePermission(true)
                .enableHooks(true)
                .tools(dedupe(tools))
                .systemTemplate("You are a coding agent at ${WORKDIR}. Hooks let you attach deterministic actions to lifecycle events (PreToolUse/PostToolUse/UserPromptSubmit/Stop). Use hook_register to observe or alter behavior, hook_list to inspect, and hook_remove to clean up.")
                .build();
    }

    /**
     * 构建 s05 Todo 规划阶段配置。
     *
     * @return s05 阶段配置
     */
    public static StageConfig s05() {
        List<Map<String, Object>> tools = new ArrayList<>(s04().tools());
        // s05 引入 todo，帮助模型把“长任务”拆成多个可跟踪步骤。
        tools.add(tool("todo", "Update task list. Track progress on multi-step tasks.", Map.of("type", "object", "properties", Map.of("items", Map.of("type", "array", "items", Map.of("type", "object"))), "required", List.of("items"))));
        return builder("s05")
                .enablePermission(true)
                .enableHooks(true)
                .enableTodoNag(true)
                .tools(dedupe(tools))
                .systemTemplate("You are a coding agent at ${WORKDIR}. Use the todo tool to plan multi-step tasks. Mark in_progress before starting, completed when done. Prefer tools over prose.")
                .build();
    }

    /**
     * 构建 s06 子代理阶段配置。
     *
     * @return s06 阶段配置
     */
    public static StageConfig s06() {
        List<Map<String, Object>> tools = new ArrayList<>(s05().tools());
        // s06 引入 subagent，体现 Claude Code 的重要思想：复杂问题可以分治。
        tools.add(tool("task", "Spawn a subagent with fresh context.", Map.of("type", "object", "properties", Map.of("prompt", Map.of("type", "string"), "description", Map.of("type", "string")), "required", List.of("prompt"))));
        return builder("s06")
                .enablePermission(true)
                .enableHooks(true)
                .enableTodoNag(true)
                .tools(dedupe(tools))
                .systemTemplate("You are a coding agent at ${WORKDIR}. Use the task tool to delegate exploration or subtasks.")
                .build();
    }

    /**
     * 构建 s07 技能加载阶段配置。
     *
     * @return s07 阶段配置
     */
    public static StageConfig s07() {
        List<Map<String, Object>> tools = new ArrayList<>(s06().tools());
        // s07 引入技能：把领域知识从 system prompt 中拆出去，按需加载，节省上下文。
        tools.add(tool("load_skill", "Load specialized knowledge by name.", Map.of("type", "object", "properties", Map.of("name", Map.of("type", "string")), "required", List.of("name"))));
        return builder("s07")
                .enablePermission(true)
                .enableHooks(true)
                .enableTodoNag(true)
                .tools(dedupe(tools))
                .systemTemplate("You are a coding agent at ${WORKDIR}. Use load_skill to access specialized knowledge before unfamiliar work.\n\nSkills available:\n${SKILLS}")
                .build();
    }

    /**
     * 构建 s08 上下文压缩阶段配置。
     *
     * @return s08 阶段配置
     */
    public static StageConfig s08() {
        List<Map<String, Object>> tools = new ArrayList<>(s07().tools());
        // s08 解决上下文窗口问题：对话太长时，Agent 需要学会压缩历史而不是无限堆积。
        tools.add(tool("compact", "Trigger manual conversation compression.", Map.of("type", "object", "properties", Map.of("focus", Map.of("type", "string")))));
        return builder("s08")
                .enablePermission(true)
                .enableHooks(true)
                .enableTodoNag(true)
                .enableCompression(true)
                .tools(dedupe(tools))
                .systemTemplate("You are a coding agent at ${WORKDIR}. Use tools to solve tasks.")
                .build();
    }

    /**
     * 构建 s09 持久记忆阶段配置。
     *
     * @return s09 阶段配置
     */
    public static StageConfig s09() {
        List<Map<String, Object>> tools = new ArrayList<>(s08().tools());
        // s09 引入记忆：让 Agent 拥有跨会话持久化的知识，
        // 上下文可以被压缩甚至清空，但重要事实不会随之丢失。
        tools.add(tool("memory_add", "Store a memory for future sessions.", Map.of("type", "object", "properties", Map.of("content", Map.of("type", "string"), "category", Map.of("type", "string", "enum", List.of("fact", "preference", "procedure"))), "required", List.of("content", "category"))));
        tools.add(tool("memory_search", "Search stored memories.", Map.of("type", "object", "properties", Map.of("query", Map.of("type", "string")), "required", List.of("query"))));
        tools.add(tool("memory_delete", "Delete a stored memory.", Map.of("type", "object", "properties", Map.of("id", Map.of("type", "string")), "required", List.of("id"))));
        tools.add(tool("memory_list", "List all stored memories.", Map.of("type", "object", "properties", Map.of())));
        return builder("s09")
                .enablePermission(true)
                .enableHooks(true)
                .enableTodoNag(true)
                .enableCompression(true)
                .enableMemory(true)
                .tools(dedupe(tools))
                .systemTemplate("You are a coding agent at ${WORKDIR}. You have persistent memory across sessions. Use memory_add to store durable facts, preferences, and procedures; memory_search to recall them later; memory_list and memory_delete to manage what is stored.")
                .build();
    }

    /**
     * 构建 s10 任务系统阶段配置。
     *
     * @return s10 阶段配置
     */
    public static StageConfig s10() {
        List<Map<String, Object>> tools = new ArrayList<>(s09().tools());
        // s10 把“计划”从易失的 todo 升级为持久化的任务看板：
        // 任务有状态、有依赖、可以跨会话恢复，也为后续多 Agent 认领打基础。
        tools.add(tool("task_create", "Create a new task.", Map.of("type", "object", "properties", Map.of("subject", Map.of("type", "string"), "description", Map.of("type", "string")), "required", List.of("subject"))));
        tools.add(tool("task_update", "Update task status or dependencies.", Map.of("type", "object", "properties", Map.of("task_id", Map.of("type", "integer"), "status", Map.of("type", "string"), "addBlockedBy", Map.of("type", "array"), "addBlocks", Map.of("type", "array")), "required", List.of("task_id"))));
        tools.add(tool("task_list", "List all tasks.", Map.of("type", "object", "properties", Map.of())));
        tools.add(tool("task_get", "Get task details.", Map.of("type", "object", "properties", Map.of("task_id", Map.of("type", "integer")), "required", List.of("task_id"))));
        return builder("s10")
                .enablePermission(true)
                .enableHooks(true)
                .enableTodoNag(true)
                .enableCompression(true)
                .enableMemory(true)
                .tools(dedupe(tools))
                .systemTemplate("You are a coding agent at ${WORKDIR}. Use task tools to plan and track work.")
                .build();
    }

    /**
     * 构建 s11 后台任务阶段配置。
     *
     * @return s11 阶段配置
     */
    public static StageConfig s11() {
        List<Map<String, Object>> tools = new ArrayList<>(s10().tools());
        // s11 引入后台执行：耗时命令不再阻塞主循环，
        // 结果稍后以通知形式注入回主上下文。
        tools.add(tool("background_run", "Run command in background thread.", Map.of("type", "object", "properties", Map.of("command", Map.of("type", "string"), "timeout", Map.of("type", "integer")), "required", List.of("command"))));
        tools.add(tool("check_background", "Check background task status.", Map.of("type", "object", "properties", Map.of("task_id", Map.of("type", "string")))));
        return builder("s11")
                .enablePermission(true)
                .enableHooks(true)
                .enableTodoNag(true)
                .enableCompression(true)
                .enableMemory(true)
                .enableBackground(true)
                .tools(dedupe(tools))
                .systemTemplate("You are a coding agent at ${WORKDIR}. Use background_run for long-running commands.")
                .build();
    }

    /**
     * 构建 s12 定时任务阶段配置。
     *
     * @return s12 阶段配置
     */
    public static StageConfig s12() {
        List<Map<String, Object>> tools = new ArrayList<>(s11().tools());
        // s12 引入 cron：Agent 不再只是“被动响应用户输入”，
        // 而是可以按计划在未来某个时刻自动唤醒并执行任务。
        tools.add(tool("schedule_cron", "Schedule a prompt to run on a cron expression.", Map.of("type", "object", "properties", Map.of("expression", Map.of("type", "string"), "prompt", Map.of("type", "string"), "once", Map.of("type", "boolean")), "required", List.of("expression", "prompt"))));
        tools.add(tool("list_crons", "List all scheduled crons.", Map.of("type", "object", "properties", Map.of())));
        tools.add(tool("cancel_cron", "Cancel a scheduled cron.", Map.of("type", "object", "properties", Map.of("id", Map.of("type", "string")), "required", List.of("id"))));
        return builder("s12")
                .enablePermission(true)
                .enableHooks(true)
                .enableTodoNag(true)
                .enableCompression(true)
                .enableMemory(true)
                .enableBackground(true)
                .enableCron(true)
                .tools(dedupe(tools))
                .systemTemplate("You are a coding agent at ${WORKDIR}. Use schedule_cron to run prompts on a schedule (cron expression, optionally once), list_crons to inspect schedules, and cancel_cron to remove them. Scheduled prompts survive restarts.")
                .build();
    }

    /**
     * 构建 s13 多 Agent 团队阶段配置。
     *
     * @return s13 阶段配置
     */
    public static StageConfig s13() {
        List<Map<String, Object>> tools = new ArrayList<>(s12().tools());
        // s13 把原来分散在多个阶段的团队能力合并成一个完整的“多 Agent 协作”阶段：
        // - lead 可以创建队友并通过 inbox 通信（原 s09）；
        // - 通过 shutdown / plan_approval 协议管理队友生命周期（原 s10）；
        // - 队友具备自治能力，空闲时主动认领任务（原 s11）；
        // - 用 worktree lane 把不同任务隔离到不同目录执行（原 s12）。
        tools.add(tool("spawn_teammate", "Spawn a persistent teammate.", Map.of("type", "object", "properties", Map.of("name", Map.of("type", "string"), "role", Map.of("type", "string"), "prompt", Map.of("type", "string")), "required", List.of("name", "role", "prompt"))));
        tools.add(tool("list_teammates", "List all teammates.", Map.of("type", "object", "properties", Map.of())));
        tools.add(tool("send_message", "Send a message to a teammate.", Map.of("type", "object", "properties", Map.of("to", Map.of("type", "string"), "content", Map.of("type", "string"), "msg_type", Map.of("type", "string")), "required", List.of("to", "content"))));
        tools.add(tool("read_inbox", "Read and drain the lead inbox.", Map.of("type", "object", "properties", Map.of())));
        tools.add(tool("broadcast", "Send message to all teammates.", Map.of("type", "object", "properties", Map.of("content", Map.of("type", "string")), "required", List.of("content"))));
        tools.add(tool("shutdown_request", "Request teammate shutdown.", Map.of("type", "object", "properties", Map.of("teammate", Map.of("type", "string")), "required", List.of("teammate"))));
        tools.add(tool("plan_approval", "Approve or reject a teammate plan.", Map.of("type", "object", "properties", Map.of("request_id", Map.of("type", "string"), "approve", Map.of("type", "boolean"), "feedback", Map.of("type", "string")), "required", List.of("request_id", "approve"))));
        tools.add(tool("claim_task", "Claim a task from the board.", Map.of("type", "object", "properties", Map.of("task_id", Map.of("type", "integer")), "required", List.of("task_id"))));
        tools.add(tool("idle", "Enter idle state.", Map.of("type", "object", "properties", Map.of())));
        tools.add(tool("worktree_create", "Create a task-bound worktree lane.", Map.of("type", "object", "properties", Map.of("name", Map.of("type", "string"), "task_id", Map.of("type", "integer")), "required", List.of("name", "task_id"))));
        tools.add(tool("worktree_list", "List all worktrees.", Map.of("type", "object", "properties", Map.of())));
        tools.add(tool("worktree_remove", "Remove a worktree.", Map.of("type", "object", "properties", Map.of("name", Map.of("type", "string"), "keep", Map.of("type", "boolean")), "required", List.of("name"))));
        tools.add(tool("worktree_events", "List recent worktree lifecycle events.", Map.of("type", "object", "properties", Map.of("limit", Map.of("type", "integer")))));
        return builder("s13")
                .enablePermission(true)
                .enableHooks(true)
                .enableTodoNag(true)
                .enableCompression(true)
                .enableMemory(true)
                .enableBackground(true)
                .enableCron(true)
                .enableInbox(true)
                .autonomousTeammates(true)
                .tools(dedupe(tools))
                .systemTemplate("You are a team lead at ${WORKDIR}. Spawn teammates with spawn_teammate and communicate via send_message/broadcast/read_inbox. Teammates are autonomous -- they claim tasks from the board themselves. Manage lifecycles with shutdown_request and plan_approval. Use worktree tools to isolate parallel work, and worktree_events for lifecycle visibility.")
                .build();
    }

    /**
     * 构建 s14 MCP 插件阶段配置。
     *
     * @return s14 阶段配置
     */
    public static StageConfig s14() {
        // s14 的教学重点是“外部能力接入”，与教学主线（s05-s13）解耦，
        // 因此工具集从 s04 分叉，只带基础工具 + 权限 + Hook。
        List<Map<String, Object>> tools = new ArrayList<>(s04().tools());
        // s14 引入 MCP：Agent 的工具集不再是编译期固定的，
        // 而是可以在运行时连接外部 MCP server 动态扩展。
        tools.add(tool("connect_mcp", "Connect to an MCP server and import its tools.", Map.of("type", "object", "properties", Map.of("server_name", Map.of("type", "string")), "required", List.of("server_name"))));
        return builder("s14")
                .enablePermission(true)
                .enableHooks(true)
                .enableMcp(true)
                .tools(dedupe(tools))
                .systemTemplate("You are a coding agent at ${WORKDIR}. Use connect_mcp to attach external MCP servers and extend your toolset at runtime. Discovered tools follow the same permission rules as built-in tools.")
                .build();
    }

    /**
     * 构建 s15 集成 Harness 阶段配置。
     *
     * @return s15 阶段配置
     */
    public static StageConfig s15() {
        // s15 是“完整体”的前一站：把 s01-s14 的所有能力开关全部打开，
        // 所有工具合并去重，验证各子系统可以在同一个运行时里协同工作。
        List<Map<String, Object>> tools = new ArrayList<>(s13().tools());
        tools.addAll(s14().tools());
        return builder("s15")
                .enablePermission(true)
                .enableHooks(true)
                .enableTodoNag(true)
                .enableCompression(true)
                .enableMemory(true)
                .enableBackground(true)
                .enableCron(true)
                .enableInbox(true)
                .autonomousTeammates(true)
                .subagentWritable(true)
                .enableMcp(true)
                .tools(dedupe(tools))
                .systemTemplate("You are a fully integrated coding agent at ${WORKDIR}. All subsystems are active: permissions, hooks, todos, compression, memory, background tasks, crons, teams, MCP servers. Orchestrate them to complete any task end-to-end. Skills: ${SKILLS}")
                .build();
    }

    /**
     * 构建 s16 工作流运行时阶段配置。
     *
     * @return s16 阶段配置
     */
    public static StageConfig s16() {
        List<Map<String, Object>> tools = new ArrayList<>(s15().tools());
        // s16 引入工作流：把“每次都要模型现场推理的多步流程”
        // 沉淀为可重复、可恢复的确定性编排。
        tools.add(tool("workflow", "Run a named workflow with arguments.", Map.of("type", "object", "properties", Map.of("name", Map.of("type", "string"), "args", Map.of("type", "object"), "resume_from_run_id", Map.of("type", "string")), "required", List.of("name"))));
        return builder("s16")
                .enablePermission(true)
                .enableHooks(true)
                .enableTodoNag(true)
                .enableCompression(true)
                .enableMemory(true)
                .enableBackground(true)
                .enableCron(true)
                .enableInbox(true)
                .autonomousTeammates(true)
                .subagentWritable(true)
                .enableMcp(true)
                .enableWorkflow(true)
                .tools(dedupe(tools))
                .systemTemplate("You are a coding agent at ${WORKDIR} with a workflow runtime. Use the workflow tool to execute named multi-step workflows with arguments. Workflows are deterministic and resumable -- pass resume_from_run_id to continue a failed run from its last checkpoint.")
                .build();
    }

    /**
     * 构建 s17 目标循环阶段配置。
     *
     * @return s17 阶段配置
     */
    public static StageConfig s17() {
        // s17 与教学主线解耦，工具集同样从 s04 分叉。
        List<Map<String, Object>> tools = new ArrayList<>(s04().tools());
        // s17 引入目标循环：Agent 围绕一个“可验证的目标条件”持续自主迭代，
        // 直到条件满足才停止，这是自治 Agent 的最小骨架。
        tools.add(tool("goal_set", "Set the goal condition to loop until satisfied.", Map.of("type", "object", "properties", Map.of("condition", Map.of("type", "string")), "required", List.of("condition"))));
        tools.add(tool("goal_status", "Report current goal and evaluation status.", Map.of("type", "object", "properties", Map.of())));
        tools.add(tool("goal_clear", "Clear the active goal and stop looping.", Map.of("type", "object", "properties", Map.of())));
        return builder("s17")
                .enablePermission(true)
                .enableHooks(true)
                .enableGoalLoop(true)
                .tools(dedupe(tools))
                .systemTemplate("You are an autonomous coding agent at ${WORKDIR}. Use goal_set to define a verifiable goal condition. Keep working each turn until goal_status reports the condition met; only then stop. Use goal_clear to abandon a goal explicitly.")
                .build();
    }

    /**
     * 构建完整能力版本的阶段配置。
     *
     * @return s_full 阶段配置
     */
    public static StageConfig sFull() {
        // s_full 不是重新定义一套工具，而是把前面各阶段的能力合并成一个完整视图。
        // 直接合并 s16（含 s01-s15 全部工具）与 s17 的目标工具，再统一去重。
        List<Map<String, Object>> tools = new ArrayList<>(s16().tools());
        tools.addAll(s17().tools());
        return builder("s_full")
                .enablePermission(true)
                .enableHooks(true)
                .enableTodoNag(true)
                .enableCompression(true)
                .enableMemory(true)
                .enableBackground(true)
                .enableCron(true)
                .enableInbox(true)
                .autonomousTeammates(true)
                .subagentWritable(true)
                .enableMcp(true)
                .enableWorkflow(true)
                .enableGoalLoop(true)
                .tools(dedupe(tools))
                .systemTemplate("You are a coding agent at ${WORKDIR}. Use tools to solve tasks. Prefer task_create/task_update/task_list for multi-step work. Use TodoWrite for short checklists. Use task for subagent delegation. Use load_skill for specialized knowledge. Skills: ${SKILLS}")
                .build();
    }

    /**
     * 按 messages API 约定构造单个工具定义。
     *
     * @param name 工具名
     * @param description 工具描述
     * @param schema 输入 schema
     * @return 工具定义映射
     */
    private static Map<String, Object> tool(String name, String description, Map<String, Object> schema) {
        // 这里构造的结构直接对齐 messages API 所需的 tool 定义格式。
        Map<String, Object> tool = new HashMap<>();
        tool.put("name", name);
        tool.put("description", description);
        tool.put("input_schema", schema);
        return tool;
    }

    /**
     * 对工具列表按名字去重。
     *
     * @param tools 原始工具列表
     * @return 去重后的工具列表
     */
    private static List<Map<String, Object>> dedupe(List<Map<String, Object>> tools) {
        // 多阶段合并时会出现同名工具，这里按名字去重，保留最后一次定义。
        Map<String, Map<String, Object>> unique = new HashMap<>();
        for (Map<String, Object> tool : tools) {
            unique.put(String.valueOf(tool.get("name")), tool);
        }
        return new ArrayList<>(unique.values());
    }
}
