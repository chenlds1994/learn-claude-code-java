package com.learnclaudecode.workflow;

import com.learnclaudecode.common.JsonUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 工作流执行状态，向编排脚本暴露一组“编排原语”。
 *
 * <p>可以把 ExecutionState 理解为一次工作流运行的“工作台 + 账本”：
 * <ul>
 *   <li>工作台：提供 {@link #phase}/{@link #log}/{@link #agent}/{@link #parallel}/{@link #pipeline}
 *       等原语，让脚本用统一的方式推进编排；</li>
 *   <li>账本：{@code journal} 逐条记录每一步的语义 key 与结果，
 *       落盘后即可支撑失败重跑的 Resume。</li>
 * </ul>
 *
 * <p>Resume 的关键在于“确定性语义 key”：同一段编排逻辑，
 * 只要 prompt/label/schema 相同，算出来的 key 就相同，
 * 因此重跑时能命中 journal 里已经算过的结果，直接跳过重复执行。
 */
public class ExecutionState {
    /** 一次运行的唯一 ID，用于关联 journal 与 output 落盘文件。 */
    private final String runId;
    /** 当前运行的工作流名称。 */
    private final String workflowName;
    /** 当前所处阶段标题，由 {@link #phase(String)} 推进。 */
    private String phase;
    /** 编排账本，逐条记录 phase/log/agent 等步骤。 */
    private final List<JournalEntry> journal = new ArrayList<>();
    /** 实际执行（未命中缓存）的 agent 次数。 */
    private int agentCount;
    /** 累计消耗的 token 数（含缓存命中复用的结果）。 */
    private int tokenCount;
    /** 命中缓存、被跳过执行的次数，用于体现 Resume 的价值。 */
    private int cacheHits;

    /**
     * journal 中的一条记录。
     *
     * @param key 确定性语义 key，agent 步骤用于缓存命中判断；phase/log 步骤可为描述性标识
     * @param kind 步骤类型，例如 "phase" / "log" / "agent"
     * @param label 人类可读的步骤标签
     * @param result 结果载荷；agent 步骤存 AgentResult 的 JSON，phase/log 步骤存原始文本
     */
    public record JournalEntry(String key, String kind, String label, String result) {
    }

    /**
     * 单个 agent 步骤的执行结果。
     *
     * @param output agent 产出文本
     * @param tokens 本次产出消耗的 token 数
     */
    public record AgentResult(String output, int tokens) {
    }

    /**
     * 初始化执行状态。
     *
     * @param runId 本次运行 ID
     * @param workflowName 工作流名称
     */
    public ExecutionState(String runId, String workflowName) {
        this.runId = runId;
        this.workflowName = workflowName;
    }

    /**
     * 进入一个新阶段，并把阶段切换记入 journal。
     *
     * @param title 阶段标题
     * @return 原样返回的阶段标题，方便脚本内联使用
     */
    public String phase(String title) {
        // 阶段本身没有“可缓存的计算结果”，因此 key 直接用描述性标识即可。
        this.phase = title;
        journal.add(new JournalEntry("phase:" + title, "phase", title, title));
        return title;
    }

    /**
     * 记录一条进度日志。
     *
     * @param message 日志文本
     * @return 带阶段前缀的格式化日志，便于阅读
     */
    public String log(String message) {
        String formatted = "[" + (phase == null ? workflowName : phase) + "] " + message;
        journal.add(new JournalEntry("log:" + formatted, "log", phase == null ? workflowName : phase, formatted));
        return formatted;
    }

    /**
     * 执行一个 agent 步骤，带确定性语义 key 的缓存与 Resume 支持。
     *
     * <p>执行流程：
     * <ol>
     *   <li>由 kind/label/prompt/schema 计算确定性语义 key；</li>
     *   <li>在 journal 中查找是否已有相同 key 的 agent 结果（Resume 命中）；</li>
     *   <li>命中则复用缓存结果并累加 cacheHits，跳过真实执行；</li>
     *   <li>未命中则模拟执行一个 agent（教学版 mock），并把结果写入 journal。</li>
     * </ol>
     *
     * @param prompt 交给 agent 的指令
     * @param label agent 步骤标签
     * @param schema 期望的结构化输出 schema，可为 null
     * @return agent 执行结果
     */
    public AgentResult agent(String prompt, String label, Map<String, Object> schema) {
        String key = computeSemanticKey("agent", label, prompt, schema);

        // Resume 命中：journal 里已经算过同样的 agent 步骤，直接复用结果。
        for (JournalEntry entry : journal) {
            if ("agent".equals(entry.kind()) && key.equals(entry.key())) {
                AgentResult cached = decodeResult(entry.result());
                cacheHits++;
                tokenCount += cached.tokens();
                return cached;
            }
        }

        // 未命中缓存：模拟一次 agent 执行。
        // 教学版本不接真实模型，用一个确定性的 mock 返回结构化响应，
        // 这样即便没有 API Key，也能完整演示编排、journal 与 Resume 的效果。
        AgentResult result = mockAgent(prompt, label, schema, key);
        journal.add(new JournalEntry(key, "agent", label, encodeResult(result)));
        agentCount++;
        tokenCount += result.tokens();
        return result;
    }

    /**
     * 并行执行一组 agent 任务。
     *
     * <p>教学版本为了保持确定性与可读性，采用顺序执行，
     * 但对外语义仍是“提交一批任务、收集全部结果”，
     * 未来可平滑替换为线程池而不改变调用方代码。
     *
     * @param tasks agent 任务供给者列表
     * @return 全部任务的结果，顺序与入参一致
     */
    public List<AgentResult> parallel(List<Supplier<AgentResult>> tasks) {
        List<AgentResult> results = new ArrayList<>();
        for (Supplier<AgentResult> task : tasks) {
            results.add(task.get());
        }
        return results;
    }

    /**
     * 让每个条目独立地流经所有阶段（流水线）。
     *
     * <p>与“先做完全部条目的阶段 1、再统一进入阶段 2”的批处理不同，
     * 这里每个条目一旦产生就立刻进入下一阶段，条目之间没有屏障（barrier），
     * 上一阶段的输出文本会作为下一阶段 agent 的输入。
     *
     * @param items 待处理的条目列表
     * @param stages 依次作用于每个条目的阶段函数
     * @return 每个条目流经全部阶段后的最终结果
     */
    @SafeVarargs
    public final List<AgentResult> pipeline(List<String> items, Function<String, AgentResult>... stages) {
        List<AgentResult> results = new ArrayList<>();
        for (String item : items) {
            AgentResult current = null;
            String carry = item;
            for (Function<String, AgentResult> stage : stages) {
                // 把上一阶段的输出喂给下一阶段，实现条目在阶段间的独立流动。
                current = stage.apply(carry);
                carry = current.output();
            }
            if (current != null) {
                results.add(current);
            }
        }
        return results;
    }

    /**
     * 返回 journal 的只读副本，供引擎落盘持久化。
     *
     * @return journal 记录列表
     */
    public List<JournalEntry> getJournal() {
        return new ArrayList<>(journal);
    }

    /**
     * 从磁盘加载已有 journal，用于 Resume 场景。
     *
     * @param entries 上一次运行落盘的 journal 记录
     */
    public void loadJournal(List<JournalEntry> entries) {
        journal.clear();
        if (entries != null) {
            journal.addAll(entries);
        }
    }

    /**
     * 返回本次运行 ID。
     *
     * @return 运行 ID
     */
    public String runId() {
        return runId;
    }

    /**
     * 返回工作流名称。
     *
     * @return 工作流名称
     */
    public String workflowName() {
        return workflowName;
    }

    /**
     * 返回当前阶段标题。
     *
     * @return 当前阶段标题
     */
    public String currentPhase() {
        return phase;
    }

    /**
     * 返回实际执行的 agent 次数（不含缓存命中）。
     *
     * @return agent 执行次数
     */
    public int agentCount() {
        return agentCount;
    }

    /**
     * 返回累计 token 数。
     *
     * @return token 数
     */
    public int tokenCount() {
        return tokenCount;
    }

    /**
     * 返回缓存命中（被跳过执行）的次数。
     *
     * @return 缓存命中次数
     */
    public int cacheHits() {
        return cacheHits;
    }

    /**
     * 计算确定性语义 key。
     *
     * <p>把 kind/label/prompt/schema 拼成稳定字符串后取 hashCode，
     * 再格式化为 10 位数字串。相同输入必然得到相同 key，这是 Resume 幂等的基础。
     *
     * @param kind 步骤类型
     * @param label 步骤标签
     * @param prompt 指令文本
     * @param schema 输出 schema，可为 null
     * @return 10 位数字语义 key
     */
    private String computeSemanticKey(String kind, String label, String prompt, Map<String, Object> schema) {
        String schemaJson = schema == null ? "" : JsonUtils.toJson(schema);
        String material = kind + "|" + label + "|" + prompt + "|" + schemaJson;
        // 用无符号扩展避免负数 hashCode 带来的符号问题，再补足 10 位。
        long unsigned = material.hashCode() & 0xffffffffL;
        return String.format("%010d", unsigned);
    }

    /**
     * 教学版 mock agent：用确定性规则生成结构化响应，避免依赖真实模型调用。
     *
     * @param prompt 指令文本
     * @param label 步骤标签
     * @param schema 输出 schema，可为 null
     * @param key 语义 key，用于派生稳定的 token 数
     * @return 模拟的 agent 结果
     */
    private AgentResult mockAgent(String prompt, String label, Map<String, Object> schema, String key) {
        // token 数由 key 派生，保证同一 agent 步骤每次 mock 出来的开销一致。
        int tokens = 40 + Math.abs(key.hashCode() % 60);
        String head = prompt.length() > 60 ? prompt.substring(0, 60) + "..." : prompt;
        String output = "[mock-agent:" + label + "] " + head;
        if (schema != null && !schema.isEmpty()) {
            // 若声明了 schema，则用 JSON 形式返回，模拟“结构化输出”的效果。
            Map<String, Object> structured = new LinkedHashMap<>();
            structured.put("label", label);
            structured.put("summary", head);
            structured.put("schema", schema);
            output = JsonUtils.toJson(structured);
        }
        return new AgentResult(output, tokens);
    }

    /**
     * 把 AgentResult 编码为 JSON 字符串，存入 journal。
     *
     * @param result agent 结果
     * @return JSON 载荷
     */
    private String encodeResult(AgentResult result) {
        return JsonUtils.toJson(Map.of("output", result.output(), "tokens", result.tokens()));
    }

    /**
     * 从 journal 载荷还原 AgentResult。
     *
     * @param payload journal 中保存的 JSON 载荷
     * @return 还原后的 agent 结果
     */
    @SuppressWarnings("unchecked")
    private AgentResult decodeResult(String payload) {
        try {
            Map<String, Object> raw = JsonUtils.fromJson(payload, Map.class);
            Object output = raw.get("output");
            Object tokens = raw.get("tokens");
            return new AgentResult(
                    output == null ? "" : String.valueOf(output),
                    tokens instanceof Number number ? number.intValue() : 0);
        } catch (RuntimeException e) {
            // 载荷损坏时退化为“把原文当输出”，保证 Resume 不因单条脏数据中断。
            return new AgentResult(payload, 0);
        }
    }
}
