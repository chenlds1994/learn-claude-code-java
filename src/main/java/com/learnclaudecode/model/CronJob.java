package com.learnclaudecode.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Locale;

/**
 * 定时任务记录，持久化到 .crons/ 目录。
 *
 * 这里刻意不引入完整的 Quartz/cron 五段式表达式，而是用一套“够用且好懂”的简化语法，
 * 让读者把注意力放在“调度状态如何被计算和持久化”上，而不是被表达式语法劝退。
 * 支持的格式：
 * - "every_30s"：每 30 秒触发一次；
 * - "every_5m"：每 5 分钟触发一次；
 * - "every_1h"：每 1 小时触发一次；
 * - "at_14:30"：每天 14:30 触发一次（今天已过则顺延到明天）。
 *
 * 加 {@link JsonIgnoreProperties} 与 MemoryRecord 同理：向前兼容，
 * 旧会话写下的 JSON 即使缺少后续新增字段也能被安全读取。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CronJob {
    /**
     * 定时任务唯一 ID，使用 UUID 字符串，同时作为落盘文件名（&lt;id&gt;.json）。
     */
    public String id;

    /**
     * cron 表达式（简化版：支持 "every_Ns"、"every_Nm"、"every_Nh"、"at_HH:MM"）。
     */
    public String expression;

    /**
     * 触发时注入给 Agent 的 prompt 文本。
     */
    public String prompt;

    /**
     * 是否一次性任务：true 表示触发一次后自动失活，false 表示按周期重复触发。
     */
    public boolean once;

    /**
     * 是否活跃：只有 active=true 的任务才会被调度线程扫描并触发。
     */
    public boolean active;

    /**
     * 创建时间，Unix 毫秒时间戳。
     */
    public long createdAt;

    /**
     * 上次触发时间，Unix 毫秒时间戳；0 表示尚未触发过。
     */
    public long lastTriggered;

    /**
     * 下次触发时间，Unix 毫秒时间戳。调度线程用它判断“到点了没有”。
     */
    public long nextTriggerAt;

    /**
     * 供 Jackson 反序列化使用的无参构造函数。
     */
    public CronJob() {
    }

    /**
     * 创建一条新的定时任务，并立即算出首次触发时间。
     *
     * 注意：这里假设 expression 已经是合法格式。合法性由调用方
     * （{@code CronScheduler.scheduleCron}）先用 {@link #isValidExpression(String)} 校验，
     * 因此本构造函数不再重复校验，只在计算失败时退化为“立即触发”。
     *
     * @param id 任务唯一 ID
     * @param expression 简化 cron 表达式
     * @param prompt 触发时注入的 prompt
     * @param once 是否一次性
     */
    public CronJob(String id, String expression, String prompt, boolean once) {
        this.id = id;
        this.expression = expression;
        this.prompt = prompt;
        this.once = once;
        this.active = true;
        this.createdAt = System.currentTimeMillis();
        this.lastTriggered = 0;
        this.nextTriggerAt = calculateNextTrigger();
    }

    /**
     * 校验简化 cron 表达式是否合法。
     *
     * 这是整套表达式语法的“单一事实来源”：{@link #calculateNextTrigger()} 与
     * 调度器的入参校验都围绕同一套规则，避免两处各写一份、日后语法漂移。
     *
     * @param expression 待校验表达式
     * @return 合法返回 true，否则返回 false
     */
    public static boolean isValidExpression(String expression) {
        if (expression == null) {
            return false;
        }
        String expr = expression.trim().toLowerCase(Locale.ROOT);
        if (expr.startsWith("every_")) {
            String body = expr.substring("every_".length()).trim();
            // 至少要有一个数字加一个单位，例如 "30s"。
            if (body.length() < 2) {
                return false;
            }
            char unit = body.charAt(body.length() - 1);
            if (unit != 's' && unit != 'm' && unit != 'h') {
                return false;
            }
            String numPart = body.substring(0, body.length() - 1).trim();
            try {
                // 间隔必须是正整数，"every_0s" 或负数都会造成忙触发，直接拒绝。
                return Long.parseLong(numPart) > 0;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        if (expr.startsWith("at_")) {
            String hhmm = expr.substring("at_".length()).trim();
            try {
                // 交给 LocalTime 校验 HH:MM（含 24 小时制范围），比手写正则更可靠。
                LocalTime.parse(hhmm);
                return true;
            } catch (DateTimeException e) {
                return false;
            }
        }
        return false;
    }

    /**
     * 根据当前表达式计算“下一次触发”的毫秒时间戳。
     *
     * 之所以声明为 public 而不是 private：调度线程在每次触发一个重复任务后，
     * 需要基于“当前时刻”重新算出下一次触发时间（而不是从创建时刻累加，
     * 这样即便进程曾经停摆，也不会因为堆积了多个过期时间点而一次性疯狂补触发）。
     * 复用同一个方法可以保证“创建时”和“触发后”用的是同一套计算逻辑。
     *
     * @return 下次触发时间（Unix 毫秒）；表达式无法解析时退化为当前时刻
     */
    public long calculateNextTrigger() {
        long now = System.currentTimeMillis();
        if (expression == null) {
            return now;
        }
        String expr = expression.trim().toLowerCase(Locale.ROOT);

        // 周期型：every_<N><s|m|h>，下次触发 = 现在 + 间隔。
        if (expr.startsWith("every_")) {
            String body = expr.substring("every_".length()).trim();
            if (body.length() >= 2) {
                char unit = body.charAt(body.length() - 1);
                String numPart = body.substring(0, body.length() - 1).trim();
                try {
                    long n = Long.parseLong(numPart);
                    long intervalMs = switch (unit) {
                        case 's' -> n * 1_000L;
                        case 'm' -> n * 60_000L;
                        case 'h' -> n * 3_600_000L;
                        default -> -1L;
                    };
                    if (intervalMs > 0) {
                        return now + intervalMs;
                    }
                } catch (NumberFormatException ignored) {
                    // 落到方法末尾的统一兜底逻辑。
                }
            }
        }

        // 定点型：at_HH:MM，下次触发 = 今天或明天的该时刻。
        if (expr.startsWith("at_")) {
            String hhmm = expr.substring("at_".length()).trim();
            try {
                LocalTime target = LocalTime.parse(hhmm);
                ZonedDateTime nowZoned = ZonedDateTime.now();
                ZonedDateTime next = nowZoned
                        .withHour(target.getHour())
                        .withMinute(target.getMinute())
                        .withSecond(0)
                        .withNano(0);
                // 若今天的该时刻已经过去（或正好卡在这一毫秒），顺延到明天，避免算出一个过期时间。
                if (!next.isAfter(nowZoned)) {
                    next = next.plusDays(1);
                }
                return next.toInstant().toEpochMilli();
            } catch (DateTimeException ignored) {
                // 落到方法末尾的统一兜底逻辑。
            }
        }

        // 兜底：无法解析时返回当前时刻（非法表达式应已在 scheduleCron 处被拦截，
        // 这里只保证方法本身永不抛异常，符合“错误不外抛”的项目约定）。
        return now;
    }
}
