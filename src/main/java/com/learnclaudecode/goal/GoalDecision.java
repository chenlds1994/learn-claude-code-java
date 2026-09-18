package com.learnclaudecode.goal;

/**
 * 目标评判结果。
 *
 * <p>评判者每轮给出一个决定，运行时据此决定循环是否继续：
 * <ul>
 *   <li>{@code pass}：目标条件已达成，可以停止；</li>
 *   <li>{@code block}：目标未达成，回到同一循环继续工作；</li>
 *   <li>{@code defer}：等待异步结果（例如后台任务），本轮不判定成败。</li>
 * </ul>
 * 另外用 {@code impossible} 标记“目标本身不可达”的特殊 pass，
 * 让运行时能区分“正常完成”与“放弃式终止”。
 *
 * @param action 判定动作：pass / block / defer
 * @param reason 判定理由
 * @param impossible 是否为“不可达而终止”
 */
public record GoalDecision(
        String action,
        String reason,
        boolean impossible
) {
    /**
     * 构造一个“目标达成”的判定。
     *
     * @param reason 判定理由
     * @return pass 决定
     */
    public static GoalDecision pass(String reason) {
        return new GoalDecision("pass", reason, false);
    }

    /**
     * 构造一个“目标未达成，继续循环”的判定。
     *
     * @param reason 判定理由
     * @return block 决定
     */
    public static GoalDecision block(String reason) {
        return new GoalDecision("block", reason, false);
    }

    /**
     * 构造一个“等待异步结果”的判定。
     *
     * @param reason 判定理由
     * @return defer 决定
     */
    public static GoalDecision defer(String reason) {
        return new GoalDecision("defer", reason, false);
    }

    /**
     * 构造一个“目标不可达，放弃式终止”的判定。
     *
     * <p>动作仍是 pass（停止循环），但 impossible=true 以便运行时区分语义。
     *
     * @param reason 判定理由
     * @return impossible 决定
     */
    public static GoalDecision impossible(String reason) {
        return new GoalDecision("pass", reason, true);
    }

    /**
     * 判断是否应继续循环。
     *
     * @return 仅当动作为 block 时返回 true
     */
    public boolean shouldContinue() {
        return "block".equals(action);
    }
}
