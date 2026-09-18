package com.learnclaudecode.scheduler;

import com.learnclaudecode.common.JsonUtils;
import com.learnclaudecode.common.WorkspacePaths;
import com.learnclaudecode.model.CronJob;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Stream;

/**
 * 定时调度器，对齐 s12 的持久化 cron 机制。
 *
 * 教学目标：“按计划触发，无需人工干预”。
 * 在这一阶段之前，Agent 只能被动等待用户输入才行动；引入 cron 之后，
 * Agent 可以把“未来某时刻要做的事”登记下来，由一个后台守护线程到点自动唤醒，
 * 并把预设 prompt 注入主循环——这就是自治 Agent 的时间维度。
 *
 * 设计要点（对齐 TaskManager 的“一记录一 JSON 文件”持久化 + BackgroundManager 的守护/队列模式）：
 * - 每个定时任务是一个独立的 .crons/&lt;id&gt;.json 文件，因此“计划”能跨重启存活；
 * - 构造时载入磁盘上的全部任务，恢复上次会话登记的调度；
 * - 一个 daemon 线程每秒 tick 一次，检查哪些任务到点，把触发的 prompt 压入队列；
 * - AgentRuntime 每轮循环调用 {@link #drainPendingPrompts()} 取出并清空队列；
 * - 写操作（登记、取消、tick）加 synchronized，读操作走并发安全容器。
 */
public class CronScheduler {
    /**
     * 守护线程的扫描间隔（毫秒）。1 秒粒度对教学演示足够精细，又不会空转浪费 CPU。
     */
    private static final long TICK_INTERVAL_MS = 1_000L;

    /**
     * prompt 展示截断长度。
     */
    private static final int SNIPPET_LEN = 80;

    /**
     * 人类可读的时间格式，用于 listCrons 展示下次/上次触发时刻。
     */
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    /**
     * 定时任务目录（.crons/），由 WorkspacePaths 保证存在。
     */
    private final Path cronsDir;

    /**
     * 内存索引：id -> 定时任务。使用 ConcurrentHashMap，让非同步读与同步写安全并发。
     */
    private final Map<String, CronJob> jobs = new ConcurrentHashMap<>();

    /**
     * 已触发但尚未被主循环消费的 prompt 队列。
     *
     * 这里刻意用线程安全的阻塞队列而非普通 List：写入方是守护线程（tick），
     * 读取方是主循环线程（drainPendingPrompts），二者并发访问，必须保证可见性与原子性。
     * 队列里每一项的格式是 "[CRON:&lt;id&gt;] &lt;prompt 文本&gt;"。
     */
    private final LinkedBlockingQueue<String> pendingPrompts = new LinkedBlockingQueue<>();

    /**
     * 守护线程运行标志，volatile 保证 stop() 的写入对 loop() 立即可见。
     */
    private volatile boolean running = false;

    /**
     * 守护线程引用，用于 stop() 时中断。
     */
    private Thread worker;

    /**
     * 初始化调度器，并载入磁盘上已有的全部定时任务。
     *
     * 这一步实现“计划跨重启存活”：上次会话登记、尚未触发（或仍在重复）的任务，
     * 会在新会话启动时被重新加载回来。
     *
     * @param paths 工作区路径工具
     */
    public CronScheduler(WorkspacePaths paths) {
        this.cronsDir = paths.cronsDir();
        loadAll();
    }

    /**
     * 登记一个定时任务。
     *
     * @param expression 简化 cron 表达式（every_Ns / every_Nm / every_Nh / at_HH:MM）
     * @param prompt 触发时注入的 prompt
     * @param once 是否一次性
     * @return 成功确认（含 id 与下次触发时间）或 "Error: ..." 说明
     */
    public synchronized String scheduleCron(String expression, String prompt, boolean once) {
        if (!CronJob.isValidExpression(expression)) {
            return "Error: Invalid cron expression '" + expression
                    + "'. Use every_<N>s, every_<N>m, every_<N>h, or at_HH:MM";
        }
        if (prompt == null || prompt.isBlank()) {
            return "Error: Cron prompt cannot be empty";
        }
        String id = UUID.randomUUID().toString();
        // 表达式统一小写规整，保证持久化后与校验/计算逻辑一致。
        CronJob job = new CronJob(id, expression.trim().toLowerCase(Locale.ROOT), prompt.trim(), once);
        try {
            save(job);
        } catch (IOException e) {
            return "Error: Failed to persist cron job: " + e.getMessage();
        }
        jobs.put(id, job);
        return "Scheduled cron " + id + " [" + job.expression + ", " + (once ? "once" : "repeating")
                + "]. Next trigger: " + formatTime(job.nextTriggerAt);
    }

    /**
     * 列出全部定时任务，活跃任务排在前面。
     *
     * @return 任务清单（可读格式），无任务时返回提示语
     */
    public String listCrons() {
        if (jobs.isEmpty()) {
            return "No scheduled cron jobs.";
        }
        List<CronJob> all = new ArrayList<>(jobs.values());
        // 活跃(active=true)排前面，其后按下次触发时间升序，最接近触发的最先看到。
        all.sort(Comparator.comparing((CronJob j) -> !j.active).thenComparingLong(j -> j.nextTriggerAt));
        StringBuilder sb = new StringBuilder();
        sb.append("Scheduled cron jobs (").append(all.size()).append(" total):");
        for (CronJob job : all) {
            String status = job.active ? "active" : "inactive";
            String kind = job.once ? "once" : "repeating";
            String next = job.active ? formatTime(job.nextTriggerAt) : "-";
            String last = job.lastTriggered > 0 ? formatTime(job.lastTriggered) : "never";
            sb.append("\n- ").append(job.id)
                    .append(" [").append(status).append(", ").append(kind).append("] ")
                    .append(job.expression)
                    .append("\n    prompt: ").append(oneLine(job.prompt))
                    .append("\n    next: ").append(next).append("   last: ").append(last);
        }
        return sb.toString().trim();
    }

    /**
     * 取消一个定时任务（标记为失活并持久化，不删除文件，保留可审计的历史）。
     *
     * @param id 任务 id
     * @return 取消确认，或未找到/已失活/失败时的 "Error: ..." 说明
     */
    public synchronized String cancelCron(String id) {
        if (id == null || id.isBlank()) {
            return "Error: Cron id cannot be empty";
        }
        String key = id.trim();
        CronJob job = jobs.get(key);
        if (job == null) {
            return "Error: Cron job not found: " + id;
        }
        if (!job.active) {
            return "Error: Cron job already inactive: " + id;
        }
        job.active = false;
        try {
            save(job);
        } catch (IOException e) {
            return "Error: Failed to update cron job: " + e.getMessage();
        }
        return "Cancelled cron " + key;
    }

    /**
     * 取出并清空当前已触发的 prompt 队列。
     *
     * AgentRuntime 每轮循环调用一次：把守护线程到点产生的 prompt 注入对话，
     * 让 Agent 得以“无人值守”地按计划行动。
     *
     * @return 待处理 prompt 列表（可能为空）
     */
    public List<String> drainPendingPrompts() {
        List<String> drained = new ArrayList<>();
        pendingPrompts.drainTo(drained);
        return drained;
    }

    /**
     * 启动守护调度线程。
     *
     * 线程被标记为 daemon，因此当主程序（REPL）退出时不会被它阻塞、能自然结束。
     * 重复调用是安全的：已在运行则直接返回。
     */
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        worker = new Thread(this::loop, "cron-scheduler");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * 停止守护调度线程。
     */
    public synchronized void stop() {
        running = false;
        if (worker != null) {
            // 中断可能正阻塞在 sleep 上的线程，让它尽快退出循环。
            worker.interrupt();
            worker = null;
        }
    }

    /**
     * 守护线程主循环：每秒 tick 一次，直到被要求停止。
     *
     * 单次 tick 抛出的运行时异常会被吞掉并继续循环，避免一个坏任务拖垮整个调度器。
     */
    private void loop() {
        while (running) {
            try {
                tick();
                Thread.sleep(TICK_INTERVAL_MS);
            } catch (InterruptedException e) {
                // 收到停止信号：恢复中断标志并退出循环。
                Thread.currentThread().interrupt();
                break;
            } catch (RuntimeException e) {
                // tick 内部已尽量容错，这里再兜一层，保证线程不会因偶发异常终止。
            }
        }
    }

    /**
     * 单次调度扫描：检查所有活跃任务，把到点的任务触发一次。
     *
     * 触发流程：
     * 1. 把 "[CRON:id] prompt" 压入待处理队列，等主循环注入；
     * 2. 记录 lastTriggered；
     * 3. 一次性任务(once)触发后失活；重复任务则基于“当前时刻”重算下次触发时间；
     * 4. 持久化最新状态，确保重启后调度信息不丢失。
     *
     * 加 synchronized 与 scheduleCron/cancelCron 互斥，避免并发修改同一批任务状态。
     */
    private synchronized void tick() {
        long now = System.currentTimeMillis();
        for (CronJob job : jobs.values()) {
            if (!job.active) {
                continue;
            }
            if (job.nextTriggerAt <= now) {
                pendingPrompts.offer("[CRON:" + job.id + "] " + job.prompt);
                job.lastTriggered = now;
                if (job.once) {
                    job.active = false;
                } else {
                    // 基于当前时刻重算，而不是从创建时刻累加：
                    // 这样即便进程曾停摆很久，重启后也只会补触发一次，不会疯狂追赶历史时间点。
                    job.nextTriggerAt = job.calculateNextTrigger();
                }
                try {
                    save(job);
                } catch (IOException e) {
                    // 持久化失败不阻断本次触发，仅跳过落盘，下个 tick 仍会重试状态。
                }
            }
        }
    }

    /**
     * 计算任务文件路径。
     *
     * @param id 任务 id
     * @return 对应的 .crons/&lt;id&gt;.json 路径
     */
    private Path path(String id) {
        return cronsDir.resolve(id + ".json");
    }

    /**
     * 将单个任务以格式化 JSON 落盘。
     *
     * @param job 定时任务
     * @throws IOException 写入失败时抛出，由调用方转成 "Error: ..." 字符串
     */
    private void save(CronJob job) throws IOException {
        Files.writeString(path(job.id), JsonUtils.toPrettyJson(job), StandardCharsets.UTF_8);
    }

    /**
     * 从磁盘载入全部定时任务到内存索引。
     *
     * 单个文件损坏静默跳过，目录不可读时保持为空，遵循项目“错误不外抛、优雅降级”的约定。
     */
    private void loadAll() {
        try (Stream<Path> stream = Files.list(cronsDir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .forEach(p -> {
                        try {
                            CronJob job = JsonUtils.fromJson(
                                    Files.readString(p, StandardCharsets.UTF_8), CronJob.class);
                            if (job != null && job.id != null) {
                                jobs.put(job.id, job);
                            }
                        } catch (IOException | RuntimeException e) {
                            // 跳过损坏的任务文件，不影响其余任务加载。
                        }
                    });
        } catch (IOException e) {
            // 目录不存在或不可读：保持为空，后续 scheduleCron 会重新创建文件。
        }
    }

    /**
     * 把毫秒时间戳格式化为可读时间。
     *
     * @param millis Unix 毫秒时间戳
     * @return 形如 "2026-09-18 14:30:00" 的时间串
     */
    private String formatTime(long millis) {
        return TIME_FMT.format(Instant.ofEpochMilli(millis));
    }

    /**
     * 把 prompt 压成单行并截断，便于清单展示。
     *
     * @param text 原始文本
     * @return 单行摘要
     */
    private String oneLine(String text) {
        if (text == null) {
            return "";
        }
        String one = text.replaceAll("\\s+", " ").trim();
        return one.length() <= SNIPPET_LEN ? one : one.substring(0, SNIPPET_LEN) + "...";
    }
}
