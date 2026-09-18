package com.learnclaudecode.hooks;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 钩子管理器，实现生命周期事件的扩展点机制。
 *
 * 教学理念："围绕循环挂钩子，而非重写循环"——通过在 Agent 主循环的关键节点
 * 注册钩子，可以在不修改运行时代码的前提下注入审计、拦截、日志等横切关注点。
 *
 * 支持的钩子类型：
 * - PreToolUse：工具调用前触发，可用于拦截危险操作
 * - PostToolUse：工具调用后触发，可用于审计执行结果
 * - UserPromptSubmit：用户提交提示词时触发，可用于输入过滤
 * - Stop：Agent 停止时触发，可用于清理资源或记录会话摘要
 *
 * 设计要点：
 * - 纯内存存储，钩子随进程生命周期存在；
 * - 每个钩子有唯一 UUID，方便精确移除；
 * - triggerHooks 按注册顺序执行，任一钩子返回 block 则短路。
 */
public class HookManager {

    /** 合法的钩子类型集合。 */
    private static final Set<String> VALID_TYPES = Set.of(
            "PreToolUse", "PostToolUse", "UserPromptSubmit", "Stop"
    );

    /** 合法的钩子动作集合。 */
    private static final Set<String> VALID_ACTIONS = Set.of(
            "log", "block", "audit"
    );

    private final List<Hook> hooks = new CopyOnWriteArrayList<>();

    /**
     * 钩子记录，描述一个已注册的生命周期钩子。
     *
     * @param id 钩子唯一标识（UUID）
     * @param type 钩子类型（PreToolUse / PostToolUse / UserPromptSubmit / Stop）
     * @param action 钩子动作描述（log / block / audit，或自定义描述）
     */
    public record Hook(String id, String type, String action) {
    }

    /**
     * 注册一个新的生命周期钩子。
     *
     * 注册时会验证 type 是否为支持的钩子类型。
     * action 可以是预定义动作（log/block/audit），也可以是自定义描述。
     *
     * @param type 钩子类型
     * @param action 钩子动作
     * @return 注册成功返回钩子 ID 和确认信息，失败返回 Error 前缀的错误信息
     */
    public synchronized String registerHook(String type, String action) {
        if (type == null || type.isBlank()) {
            return "Error: hook type is required";
        }
        if (!VALID_TYPES.contains(type)) {
            return "Error: invalid hook type '" + type + "'. Must be one of: " + VALID_TYPES;
        }
        if (action == null || action.isBlank()) {
            return "Error: hook action is required";
        }

        String id = UUID.randomUUID().toString().substring(0, 8);
        Hook hook = new Hook(id, type, action);
        hooks.add(hook);
        return "Hook registered: id=" + id + ", type=" + type + ", action=" + action;
    }

    /**
     * 列出所有已注册的钩子。
     *
     * @return 格式化的钩子列表文本
     */
    public String listHooks() {
        if (hooks.isEmpty()) {
            return "No hooks registered.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Registered Hooks (").append(hooks.size()).append("):\n");
        sb.append("-".repeat(55)).append("\n");
        for (int i = 0; i < hooks.size(); i++) {
            Hook hook = hooks.get(i);
            String actionIcon = switch (hook.action()) {
                case "log" -> "[LOG]";
                case "block" -> "[BLK]";
                case "audit" -> "[AUD]";
                default -> "[CST]";
            };
            sb.append(String.format("  %d. %s %s %-18s %s%n",
                    i + 1, hook.id(), actionIcon, hook.type(), hook.action()));
        }
        return sb.toString();
    }

    /**
     * 按 ID 移除一个已注册的钩子。
     *
     * @param hookId 钩子 ID
     * @return 移除成功返回确认信息，失败返回 Error 前缀的错误信息
     */
    public synchronized String removeHook(String hookId) {
        if (hookId == null || hookId.isBlank()) {
            return "Error: hook_id is required";
        }
        boolean removed = hooks.removeIf(h -> h.id().equals(hookId));
        if (removed) {
            return "Hook removed: " + hookId;
        }
        return "Error: no hook found with id '" + hookId + "'";
    }

    /**
     * 获取指定类型的所有钩子（供运行时调用）。
     *
     * @param type 钩子类型
     * @return 匹配的钩子列表（不可变视图）
     */
    public List<Hook> getHooksForType(String type) {
        List<Hook> matched = new ArrayList<>();
        for (Hook hook : hooks) {
            if (hook.type().equals(type)) {
                matched.add(hook);
            }
        }
        return List.copyOf(matched);
    }

    /**
     * 触发指定类型的所有钩子，按注册顺序执行。
     *
     * 执行逻辑：
     * - "log" 动作：输出到 stdout，返回 pass
     * - "block" 动作：返回 block 信号，短路后续钩子
     * - "audit" 动作：记录参数信息到 stdout，返回 pass
     * - 其他动作：视为自定义描述，默认 pass
     *
     * @param type 要触发的钩子类型
     * @param context 钩子执行上下文
     * @return "pass" 表示全部通过，"block: 原因" 表示被拦截
     */
    public String triggerHooks(String type, HookContext context) {
        List<Hook> matched = getHooksForType(type);
        if (matched.isEmpty()) {
            return "pass";
        }

        for (Hook hook : matched) {
            String result = executeHook(hook, context);
            // 任一钩子返回 block 则短路，不再执行后续钩子
            if (result.startsWith("block")) {
                return result;
            }
        }
        return "pass";
    }

    /**
     * 执行单个钩子的动作逻辑。
     *
     * @param hook 要执行的钩子
     * @param context 执行上下文
     * @return 动作执行结果
     */
    private String executeHook(Hook hook, HookContext context) {
        String action = hook.action().toLowerCase();
        return switch (action) {
            case "log" -> {
                System.out.printf("[HOOK:log] type=%s tool=%s args=%s%n",
                        hook.type(),
                        context.toolName() != null ? context.toolName() : "-",
                        context.toolArgs() != null ? context.toolArgs().toString() : "-");
                yield "pass";
            }
            case "block" -> {
                String reason = context.toolName() != null
                        ? "blocked by hook " + hook.id() + " for tool '" + context.toolName() + "'"
                        : "blocked by hook " + hook.id();
                System.out.printf("[HOOK:block] %s%n", reason);
                yield "block: " + reason;
            }
            case "audit" -> {
                System.out.printf("[HOOK:audit] type=%s tool=%s args=%s result=%s%n",
                        hook.type(),
                        context.toolName() != null ? context.toolName() : "-",
                        context.toolArgs() != null ? context.toolArgs().toString() : "-",
                        context.result() != null ? truncate(context.result(), 80) : "-");
                yield "pass";
            }
            default -> {
                // 自定义动作：仅输出日志，不阻塞
                System.out.printf("[HOOK:custom] type=%s action=%s tool=%s%n",
                        hook.type(), hook.action(),
                        context.toolName() != null ? context.toolName() : "-");
                yield "pass";
            }
        };
    }

    /**
     * 截断过长字符串，用于日志输出。
     *
     * @param text 原始文本
     * @param maxLen 最大长度
     * @return 截断后的文本
     */
    private String truncate(String text, int maxLen) {
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen) + "...";
    }
}
