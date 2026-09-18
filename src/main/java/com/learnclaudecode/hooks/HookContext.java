package com.learnclaudecode.hooks;

import java.util.Map;

/**
 * 钩子执行上下文，携带触发钩子时的相关信息。
 *
 * 这是一个不可变 record，为不同类型的钩子提供统一的上下文传递方式。
 * 通过静态工厂方法可以便捷地构造特定场景的上下文实例。
 *
 * @param toolName 触发的工具名称（UserPromptSubmit / Stop 时为 null）
 * @param toolArgs 工具参数或附加数据
 * @param result 工具执行结果（PreToolUse 时为 null）
 */
public record HookContext(
    String toolName,
    Map<String, Object> toolArgs,
    String result
) {
    /**
     * 构造 PreToolUse 场景的上下文。
     * 此时工具尚未执行，因此 result 为 null。
     *
     * @param toolName 即将调用的工具名
     * @param args 工具参数
     * @return PreToolUse 上下文
     */
    public static HookContext preToolUse(String toolName, Map<String, Object> args) {
        return new HookContext(toolName, args, null);
    }

    /**
     * 构造 PostToolUse 场景的上下文。
     * 此时工具已执行完毕，携带执行结果。
     *
     * @param toolName 已调用的工具名
     * @param args 工具参数
     * @param result 工具执行结果
     * @return PostToolUse 上下文
     */
    public static HookContext postToolUse(String toolName, Map<String, Object> args, String result) {
        return new HookContext(toolName, args, result);
    }

    /**
     * 构造 UserPromptSubmit 场景的上下文。
     * 用户提交新提示词时触发，prompt 内容放入 toolArgs。
     *
     * @param prompt 用户提交的提示词
     * @return UserPromptSubmit 上下文
     */
    public static HookContext userPrompt(String prompt) {
        return new HookContext(null, Map.of("prompt", prompt), null);
    }

    /**
     * 构造 Stop 场景的上下文。
     * Agent 停止运行时触发，停止原因放入 result。
     *
     * @param reason 停止原因
     * @return Stop 上下文
     */
    public static HookContext stop(String reason) {
        return new HookContext(null, null, reason);
    }
}
