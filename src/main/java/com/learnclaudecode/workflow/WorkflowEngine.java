package com.learnclaudecode.workflow;

import com.learnclaudecode.common.JsonUtils;
import com.learnclaudecode.common.WorkspacePaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 工作流引擎，负责注册、执行、持久化与恢复工作流。
 *
 * <p>它承载 s16 的核心命题：“编排形状固定时，写进代码而非对话”。
 * 引擎本身不关心某个具体流程怎么做，只负责：
 * <ul>
 *   <li>维护工作流注册表，并校验元数据；</li>
 *   <li>为每次运行分配 runId、准备 {@link ExecutionState}；</li>
 *   <li>把 journal 与输出落盘到 {@code .runtime/} 目录；</li>
 *   <li>在 Resume 时从磁盘回灌 journal，让确定性语义 key 命中缓存、跳过重复计算。</li>
 * </ul>
 */
public class WorkflowEngine {
    private final WorkspacePaths paths;
    /** 工作流注册表：名字 -> 定义。用 LinkedHashMap 保持注册顺序，列表展示更稳定。 */
    private final Map<String, WorkflowDefinition> registry = new LinkedHashMap<>();

    /**
     * 初始化工作流引擎，并注册一个示例工作流。
     *
     * @param paths 工作区路径工具，journal 与输出都落在其 runtimeDir() 下
     */
    public WorkflowEngine(WorkspacePaths paths) {
        this.paths = paths;
        registerSampleWorkflows();
    }

    /**
     * 注册内置示例工作流，供教学演示。
     *
     * <p>review-changes 演示了两个关键原语的组合：
     * 先用 pipeline 让每个审查维度独立流过“审查”阶段，
     * 再用 parallel 把所有审查结果并行送入“复核”阶段。
     */
    private void registerSampleWorkflows() {
        // 示例工作流：代码审查
        registerWorkflow("review-changes", "Review code changes across multiple dimensions",
                List.of("Review", "Verify"),
                (state, args) -> {
                    state.phase("Review");
                    var dimensions = List.of("correctness", "security", "performance");
                    // pipeline：每个维度独立地进入审查，维度之间没有屏障。
                    var reviews = state.pipeline(dimensions, item ->
                            state.agent("Review the code changes for " + item + " issues",
                                    "review-" + item, null));
                    state.phase("Verify");
                    // parallel：把每条审查结论并行送入复核。
                    // 显式把 lambda 标注为 Supplier，帮助编译器推断流元素类型。
                    var verifications = state.parallel(
                            reviews.stream()
                                    .map(r -> (Supplier<ExecutionState.AgentResult>) () ->
                                            state.agent("Verify finding: " + r.output(), "verify", null))
                                    .toList());
                    return Map.of("reviews", reviews.size(), "verifications", verifications.size());
                });
    }

    /**
     * 注册一个工作流。
     *
     * @param name 工作流名称
     * @param description 工作流描述
     * @param phases 阶段标题列表
     * @param script 编排脚本
     * @return 注册确认信息
     */
    public synchronized String registerWorkflow(String name, String description, List<String> phases,
                                                WorkflowDefinition.WorkflowScript script) {
        // 元数据先校验，非法名字直接拒绝，避免污染注册表与磁盘文件名。
        WorkflowDefinition.validateMeta(name, description);
        registry.put(name, new WorkflowDefinition(name, description,
                phases == null ? List.of() : List.copyOf(phases), script));
        return "Registered workflow '" + name + "' (" + (phases == null ? 0 : phases.size()) + " phases).";
    }

    /**
     * 执行一个已注册的工作流。
     *
     * @param name 工作流名称
     * @param args 模型提交的参数，可为 null
     * @param resumeFromRunId 需要从哪次运行恢复，可为 null 表示全新运行
     * @return 人类可读的执行摘要
     */
    public synchronized String executeWorkflow(String name, Map<String, Object> args, String resumeFromRunId) {
        WorkflowDefinition definition = registry.get(name);
        if (definition == null) {
            return "Error: Unknown workflow: " + name + ". Available: " + registry.keySet();
        }

        // Resume 时复用原 runId，让本次执行继续写回同一次运行的落盘文件；否则分配新 runId。
        boolean resuming = resumeFromRunId != null && !resumeFromRunId.isBlank();
        String runId = resuming ? resumeFromRunId.trim() : UUID.randomUUID().toString();
        ExecutionState state = new ExecutionState(runId, name);
        if (resuming) {
            // 从磁盘回灌上一次的 journal，agent 步骤的语义 key 命中后即可跳过重复执行。
            state.loadJournal(loadJournalFromDisk(runId));
        }

        Object result;
        String error = null;
        try {
            result = definition.script().execute(state, args == null ? Map.of() : args);
        } catch (Exception e) {
            // 编排中途失败也要保留已有 journal，方便下次 Resume 从断点继续。
            result = null;
            error = e.getClass().getSimpleName() + ": " + e.getMessage();
        }

        // 无论成功失败，都把 journal 与输出落盘，保证运行痕迹可追溯、可恢复。
        saveJournal(runId, state.getJournal());
        saveOutput(runId, name, result, error, state);

        long phasesDone = state.getJournal().stream().filter(entry -> "phase".equals(entry.kind())).count();
        StringBuilder summary = new StringBuilder();
        summary.append("Workflow '").append(name).append("' ").append(error == null ? "completed" : "failed").append('\n');
        summary.append("  runId: ").append(runId).append('\n');
        summary.append("  resumedFrom: ").append(resuming ? resumeFromRunId : "(fresh run)").append('\n');
        summary.append("  phases completed: ").append(phasesDone).append('\n');
        summary.append("  agents run: ").append(state.agentCount()).append('\n');
        summary.append("  tokens used: ").append(state.tokenCount()).append('\n');
        summary.append("  cached hits: ").append(state.cacheHits()).append('\n');
        if (error != null) {
            summary.append("  error: ").append(error).append('\n');
            summary.append("  tip: resume with resume_from_run_id=").append(runId);
        } else {
            summary.append("  result: ").append(JsonUtils.toJson(result));
        }
        return summary.toString();
    }

    /**
     * 列出所有已注册工作流及其描述。
     *
     * @return 人类可读的工作流清单
     */
    public synchronized String listWorkflows() {
        if (registry.isEmpty()) {
            return "No workflows registered.";
        }
        StringBuilder sb = new StringBuilder("Registered workflows:\n");
        for (WorkflowDefinition definition : registry.values()) {
            sb.append("  - ").append(definition.name())
                    .append(": ").append(definition.description())
                    .append(" [phases: ").append(String.join(", ", definition.phases())).append("]\n");
        }
        return sb.toString().stripTrailing();
    }

    /**
     * 读取某次运行的落盘 journal，返回状态摘要。
     *
     * @param runId 运行 ID
     * @return 人类可读的运行状态摘要
     */
    public synchronized String getRunStatus(String runId) {
        List<ExecutionState.JournalEntry> entries = loadJournalFromDisk(runId);
        if (entries.isEmpty()) {
            return "No journal found for runId: " + runId;
        }
        long phases = entries.stream().filter(e -> "phase".equals(e.kind())).count();
        long agents = entries.stream().filter(e -> "agent".equals(e.kind())).count();
        long logs = entries.stream().filter(e -> "log".equals(e.kind())).count();
        return "Run " + runId + "\n"
                + "  journal entries: " + entries.size() + "\n"
                + "  phases: " + phases + "\n"
                + "  agents: " + agents + "\n"
                + "  logs: " + logs;
    }

    /**
     * 把 journal 以 JSONL 格式落盘。
     *
     * @param runId 运行 ID
     * @param entries journal 记录
     */
    private void saveJournal(String runId, List<ExecutionState.JournalEntry> entries) {
        try {
            Path file = paths.runtimeDir().resolve(runId + ".journal.jsonl");
            List<String> lines = new ArrayList<>();
            for (ExecutionState.JournalEntry entry : entries) {
                // 一行一个 JSON 对象，便于流式追加与逐行回放。
                lines.add(JsonUtils.toJson(entry));
            }
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("保存工作流 journal 失败", e);
        }
    }

    /**
     * 把本次运行的输出与统计信息落盘为格式化 JSON。
     *
     * @param runId 运行 ID
     * @param workflow 工作流名称
     * @param result 脚本返回值，可为 null
     * @param error 错误信息，可为 null
     * @param state 执行状态，用于提取统计数据
     */
    private void saveOutput(String runId, String workflow, Object result, String error, ExecutionState state) {
        try {
            Path file = paths.runtimeDir().resolve(runId + ".output.json");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("runId", runId);
            payload.put("workflow", workflow);
            payload.put("status", error == null ? "completed" : "failed");
            payload.put("result", result);
            if (error != null) {
                payload.put("error", error);
            }
            payload.put("agents", state.agentCount());
            payload.put("tokens", state.tokenCount());
            payload.put("cacheHits", state.cacheHits());
            Files.writeString(file, JsonUtils.toPrettyJson(payload), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("保存工作流输出失败", e);
        }
    }

    /**
     * 从磁盘读取指定运行的 journal。
     *
     * @param runId 运行 ID
     * @return journal 记录列表；文件不存在或损坏时返回空列表
     */
    private List<ExecutionState.JournalEntry> loadJournalFromDisk(String runId) {
        List<ExecutionState.JournalEntry> entries = new ArrayList<>();
        Path file = paths.runtimeDir().resolve(runId + ".journal.jsonl");
        if (!Files.exists(file)) {
            return entries;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                entries.add(JsonUtils.fromJson(line, ExecutionState.JournalEntry.class));
            }
        } catch (IOException e) {
            throw new IllegalStateException("读取工作流 journal 失败", e);
        }
        return entries;
    }
}
