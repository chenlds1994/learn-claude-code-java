package com.learnclaudecode.memory;

import com.learnclaudecode.common.JsonUtils;
import com.learnclaudecode.common.WorkspacePaths;
import com.learnclaudecode.model.MemoryRecord;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * 持久记忆存储，对齐 s09 的跨会话记忆机制。
 *
 * 教学目标：“记住重要的，遗忘无关的”。
 * 上下文（对话历史）可以被压缩甚至清空，但真正重要的事实、偏好、操作流程
 * 不应该随之蒸发——它们被单独落到 .memory/ 目录，跨会话、跨重启依然存在。
 *
 * 设计要点（对齐 TaskManager 的“一记录一 JSON 文件”持久化模式）：
 * - 每条记忆是一个独立的 .memory/&lt;id&gt;.json 文件，天然可读、可 diff、可手工修复；
 * - 构造时把磁盘上的记忆全部载入内存 Map，后续检索走内存，避免每次搜索都扫盘；
 * - 写操作（增、删、整合）加 synchronized，读操作（搜索、列举、注入）走并发安全的 Map；
 * - 所有对外方法都返回“人类可读字符串”，错误以 "Error: ..." 返回而非抛异常，
 *   这样模型可以直接把返回值当作工具结果来理解。
 */
public class MemoryStore {
    /**
     * 合法的记忆分类，顺序同时用于 listMemories 的展示分组。
     */
    private static final List<String> CATEGORIES = List.of("fact", "preference", "procedure");

    /**
     * 检索/注入时返回的最大条数，避免一次性把太多记忆塞回上下文。
     */
    private static final int MAX_RESULTS = 5;

    /**
     * 整合时判定“重复”的 Jaccard 相似度阈值：达到或超过该值即视为可合并。
     */
    private static final double SIMILARITY_THRESHOLD = 0.8;

    /**
     * 内容摘要展示长度，超出部分以省略号截断。
     */
    private static final int SNIPPET_LEN = 80;

    /**
     * 记忆目录（.memory/），由 WorkspacePaths 保证存在。
     */
    private final Path memoryDir;

    /**
     * 内存索引：id -> 记忆记录。
     * 使用 ConcurrentHashMap，使得非同步的读方法可以与同步的写方法安全并发。
     */
    private final Map<String, MemoryRecord> memories = new ConcurrentHashMap<>();

    /**
     * 初始化记忆存储，并载入磁盘上已有的全部记忆。
     *
     * 这里体现“跨会话持久”的关键一步：新会话启动时，
     * 之前会话写下的记忆会被重新加载回内存，Agent 因此“记得”过去发生的事。
     *
     * @param paths 工作区路径工具
     */
    public MemoryStore(WorkspacePaths paths) {
        this.memoryDir = paths.memoryDir();
        loadAll();
    }

    /**
     * 新增一条记忆。
     *
     * @param content 记忆内容
     * @param category 记忆分类（fact / preference / procedure）
     * @return 成功确认（含新记忆 id）或 "Error: ..." 说明
     */
    public synchronized String addMemory(String content, String category) {
        if (content == null || content.isBlank()) {
            return "Error: Memory content cannot be empty";
        }
        // 分类统一小写后校验，只接受三种预定义分类，避免记忆库变得杂乱无章。
        String cat = category == null ? "" : category.trim().toLowerCase(Locale.ROOT);
        if (!CATEGORIES.contains(cat)) {
            return "Error: Invalid category '" + category + "'. Must be one of: " + String.join(", ", CATEGORIES);
        }
        // 用 UUID 作为 id：既保证全局唯一，又直接充当落盘文件名。
        String id = UUID.randomUUID().toString();
        MemoryRecord record = new MemoryRecord(id, content.trim(), cat);
        try {
            save(record);
        } catch (IOException e) {
            return "Error: Failed to persist memory: " + e.getMessage();
        }
        memories.put(id, record);
        return "Stored " + cat + " memory " + id + ": " + snippet(record.content);
    }

    /**
     * 按关键词检索记忆。
     *
     * 采用最朴素但直观可解释的打分方式：把查询切成词，
     * 统计每条记忆内容命中了多少个查询词，命中越多越相关；
     * 相关度相同时，越新更新的记忆排得越靠前。
     *
     * @param query 查询文本
     * @return 命中的前若干条记忆（可读格式），或“无匹配”提示
     */
    public String searchMemories(String query) {
        if (query == null || query.isBlank()) {
            return "Error: Search query cannot be empty";
        }
        List<String> tokens = tokenize(query);
        List<Scored> ranked = scoreAll(tokens);
        // 只保留真正命中（score>0）的记忆；scoreAll 已按相关度与时间排好序。
        List<MemoryRecord> matched = new ArrayList<>();
        for (Scored s : ranked) {
            if (s.score > 0) {
                matched.add(s.record);
            }
        }
        if (matched.isEmpty()) {
            return "No memories found matching: " + query;
        }
        List<MemoryRecord> top = matched.subList(0, Math.min(MAX_RESULTS, matched.size()));
        StringBuilder sb = new StringBuilder();
        sb.append("Found ").append(matched.size()).append(" memory(ies) matching \"").append(query.trim()).append("\":\n");
        int index = 1;
        for (MemoryRecord r : top) {
            sb.append(index++).append(". [").append(r.category).append("] ").append(r.id).append("\n")
                    .append("   ").append(snippet(r.content)).append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * 列出全部记忆，按分类分组展示。
     *
     * @return 分组后的记忆清单（可读格式），空库时返回提示语
     */
    public String listMemories() {
        if (memories.isEmpty()) {
            return "No memories stored yet.";
        }
        // 用 LinkedHashMap 固定展示顺序：先按预定义分类，未知分类兜底排在最后。
        Map<String, List<MemoryRecord>> byCategory = new LinkedHashMap<>();
        for (String cat : CATEGORIES) {
            byCategory.put(cat, new ArrayList<>());
        }
        for (MemoryRecord r : memories.values()) {
            String key = (r.category == null || r.category.isBlank()) ? "other" : r.category;
            byCategory.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Stored memories (").append(memories.size()).append(" total):");
        for (Map.Entry<String, List<MemoryRecord>> entry : byCategory.entrySet()) {
            List<MemoryRecord> list = entry.getValue();
            if (list.isEmpty()) {
                continue;
            }
            // 组内按创建时间升序，读起来更像一条时间线。
            list.sort(Comparator.comparingLong(r -> r.createdAt));
            sb.append("\n\n[").append(entry.getKey()).append("] (").append(list.size()).append(")");
            for (MemoryRecord r : list) {
                sb.append("\n- ").append(r.id).append(": ").append(snippet(r.content));
            }
        }
        return sb.toString().trim();
    }

    /**
     * 删除一条记忆。
     *
     * @param id 记忆 id
     * @return 删除确认，或未找到/失败时的 "Error: ..." 说明
     */
    public synchronized String deleteMemory(String id) {
        if (id == null || id.isBlank()) {
            return "Error: Memory id cannot be empty";
        }
        String key = id.trim();
        MemoryRecord removed = memories.remove(key);
        if (removed == null) {
            return "Error: Memory not found: " + id;
        }
        try {
            Files.deleteIfExists(path(key));
        } catch (IOException e) {
            return "Error: Failed to delete memory file: " + e.getMessage();
        }
        return "Deleted memory " + key + " [" + removed.category + "]";
    }

    /**
     * 取回与当前上下文最相关的记忆，用于注入 system prompt 的 ${MEMORY} 占位符。
     *
     * 与 searchMemories 的区别在于“兜底策略”：
     * - 若上下文能匹配到记忆，则返回最相关的前若干条；
     * - 若一条都匹配不上，则退化为返回最近更新的几条记忆，
     *   让 Agent 在新会话开场也具备最基本的“记忆感知”，而不是空白一片。
     *
     * @param context 当前上下文（通常是最近的用户输入或对话摘要）
     * @return 供注入的记忆块（可读格式）；无任何记忆时返回空串
     */
    public String getRelevantMemories(String context) {
        if (memories.isEmpty()) {
            return "";
        }
        List<String> tokens = tokenize(context == null ? "" : context);
        List<Scored> ranked = scoreAll(tokens);
        boolean anyMatch = ranked.stream().anyMatch(s -> s.score > 0);

        List<MemoryRecord> picked = new ArrayList<>();
        for (Scored s : ranked) {
            // 有匹配项时只注入匹配项；无匹配时（anyMatch=false）注入排序靠前的最近记忆。
            if (anyMatch && s.score <= 0) {
                break;
            }
            picked.add(s.record);
            if (picked.size() >= MAX_RESULTS) {
                break;
            }
        }
        if (picked.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (MemoryRecord r : picked) {
            sb.append("- [").append(r.category).append("] ").append(oneLine(r.content)).append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * 整合记忆：找出重复/高度相似的记忆并合并，只保留最新的一条。
     *
     * 这是“遗忘无关的”的教学演示：随着会话增多，Agent 可能反复记下几乎相同的事实，
     * 记忆库会冗余膨胀。整合逻辑按分类分组，在组内用 Jaccard 相似度找出近似重复项，
     * 保留更新时间最新的那条、删除其余，从而让记忆库保持精炼。
     *
     * @return 整合结果摘要（合并了哪些、删除了哪些），或“无可整合”提示
     */
    public synchronized String consolidateMemories() {
        if (memories.size() < 2) {
            return "Nothing to consolidate (fewer than 2 memories).";
        }
        // 按分类分组：只在同一分类内部比较相似度，避免把“事实”和“操作步骤”误判为重复。
        Map<String, List<MemoryRecord>> byCategory = new HashMap<>();
        for (MemoryRecord r : memories.values()) {
            String key = (r.category == null) ? "" : r.category;
            byCategory.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }

        List<String> removedIds = new ArrayList<>();
        for (List<MemoryRecord> group : byCategory.values()) {
            // 组内按更新时间降序：最新的排最前，作为“保留者”。
            group.sort((a, b) -> Long.compare(b.updatedAt, a.updatedAt));
            Set<String> dropped = new HashSet<>();
            for (int i = 0; i < group.size(); i++) {
                MemoryRecord keep = group.get(i);
                // 若当前项已被更靠前的记忆合并掉，则跳过，不再拿它当基准。
                if (dropped.contains(keep.id)) {
                    continue;
                }
                for (int j = i + 1; j < group.size(); j++) {
                    MemoryRecord other = group.get(j);
                    if (dropped.contains(other.id)) {
                        continue;
                    }
                    if (similarity(keep.content, other.content) >= SIMILARITY_THRESHOLD) {
                        // other 与 keep 高度相似且更旧，判定为可合并的重复项。
                        dropped.add(other.id);
                        removedIds.add(other.id);
                    }
                }
            }
        }

        if (removedIds.isEmpty()) {
            return "No duplicate memories found. Nothing consolidated.";
        }
        // 从内存和磁盘同时移除被合并掉的旧记忆，保证两处状态一致。
        for (String id : removedIds) {
            memories.remove(id);
            try {
                Files.deleteIfExists(path(id));
            } catch (IOException e) {
                return "Error: Failed to delete consolidated memory " + id + ": " + e.getMessage();
            }
        }
        return "Consolidated " + removedIds.size() + " duplicate memory(ies). Removed: " + String.join(", ", removedIds);
    }

    /**
     * 计算记忆文件路径。
     *
     * @param id 记忆 id
     * @return 对应的 .memory/&lt;id&gt;.json 路径
     */
    private Path path(String id) {
        return memoryDir.resolve(id + ".json");
    }

    /**
     * 将单条记忆以格式化 JSON 落盘。
     *
     * @param record 记忆记录
     * @throws IOException 写入失败时抛出，由调用方转成 "Error: ..." 字符串
     */
    private void save(MemoryRecord record) throws IOException {
        Files.writeString(path(record.id), JsonUtils.toPrettyJson(record), StandardCharsets.UTF_8);
    }

    /**
     * 从磁盘载入全部记忆到内存索引。
     *
     * 单个文件损坏不会阻断整体加载（静默跳过），目录不可读时保持内存为空，
     * 二者都遵循项目“错误不外抛、优雅降级”的容错约定。
     */
    private void loadAll() {
        try (Stream<Path> stream = Files.list(memoryDir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .forEach(p -> {
                        try {
                            MemoryRecord record = JsonUtils.fromJson(
                                    Files.readString(p, StandardCharsets.UTF_8), MemoryRecord.class);
                            if (record != null && record.id != null) {
                                memories.put(record.id, record);
                            }
                        } catch (IOException | RuntimeException e) {
                            // 单个记忆文件损坏只跳过它自己，不影响其余记忆的加载。
                        }
                    });
        } catch (IOException e) {
            // 目录不存在或不可读：保持内存为空即可，后续 addMemory 会重新创建文件。
        }
    }

    /**
     * 对全部记忆按“与查询词的相关度”打分并排序。
     *
     * 排序规则：命中分数降序；分数相同则更新时间降序（越新越靠前）。
     *
     * @param tokens 已切分的查询词
     * @return 排好序的打分列表
     */
    private List<Scored> scoreAll(List<String> tokens) {
        List<Scored> list = new ArrayList<>();
        for (MemoryRecord r : memories.values()) {
            list.add(new Scored(r, score(r.content, tokens)));
        }
        list.sort((x, y) -> {
            if (x.score != y.score) {
                return Integer.compare(y.score, x.score);
            }
            return Long.compare(y.record.updatedAt, x.record.updatedAt);
        });
        return list;
    }

    /**
     * 统计内容命中了多少个查询词。
     *
     * @param content 记忆内容
     * @param tokens 查询词列表
     * @return 命中数量（去重前的累加分）
     */
    private int score(String content, List<String> tokens) {
        if (content == null || tokens.isEmpty()) {
            return 0;
        }
        String lower = content.toLowerCase(Locale.ROOT);
        int total = 0;
        for (String token : tokens) {
            if (lower.contains(token)) {
                total++;
            }
        }
        return total;
    }

    /**
     * 把文本切分为小写词元，用于匹配与相似度计算。
     *
     * 同时按中英文空白与常见标点切分，尽量兼容中文语料的粗粒度分词需求。
     *
     * @param text 原始文本
     * @return 词元列表（可能为空）
     */
    private List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String[] parts = text.toLowerCase(Locale.ROOT)
                .split("[\\s,.;:!?，。、；：！？\"'()\\[\\]{}<>]+");
        List<String> tokens = new ArrayList<>();
        for (String part : parts) {
            if (!part.isBlank()) {
                tokens.add(part);
            }
        }
        return tokens;
    }

    /**
     * 计算两段文本的 Jaccard 相似度（交集词数 / 并集词数），取值 [0,1]。
     *
     * @param a 文本 A
     * @param b 文本 B
     * @return 相似度
     */
    private double similarity(String a, String b) {
        Set<String> ta = new HashSet<>(tokenize(a));
        Set<String> tb = new HashSet<>(tokenize(b));
        if (ta.isEmpty() && tb.isEmpty()) {
            return 1.0;
        }
        if (ta.isEmpty() || tb.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new HashSet<>(ta);
        intersection.retainAll(tb);
        Set<String> union = new HashSet<>(ta);
        union.addAll(tb);
        return (double) intersection.size() / union.size();
    }

    /**
     * 生成用于展示的内容摘要，超长截断。
     *
     * @param content 记忆内容
     * @return 单行摘要
     */
    private String snippet(String content) {
        String one = oneLine(content);
        return one.length() <= SNIPPET_LEN ? one : one.substring(0, SNIPPET_LEN) + "...";
    }

    /**
     * 把内容压成单行（合并连续空白），便于清单展示。
     *
     * @param content 记忆内容
     * @return 单行文本
     */
    private String oneLine(String content) {
        if (content == null) {
            return "";
        }
        return content.replaceAll("\\s+", " ").trim();
    }

    /**
     * 内部打分载体：把记忆和它相对某次查询的得分绑在一起，避免排序时反复重算。
     */
    private static final class Scored {
        final MemoryRecord record;
        final int score;

        Scored(MemoryRecord record, int score) {
            this.record = record;
            this.score = score;
        }
    }
}
