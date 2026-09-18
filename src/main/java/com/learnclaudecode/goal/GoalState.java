package com.learnclaudecode.goal;

/**
 * 目标状态，记录当前活跃的 Goal 信息。
 *
 * <p>s17 的核心是“目标决定循环何时可以停止”：
 * 用户/模型通过 goal_set 设定一个可验证的目标条件，
 * 运行时在每轮结束后调用独立评判者判断该条件是否达成，
 * GoalState 就是这一过程中被持续更新的会话级状态。
 *
 * @param condition 目标条件文本
 * @param evaluationCount 已评判次数
 * @param startTime 目标设定时刻（毫秒时间戳）
 * @param latestReason 最近一次评判给出的理由
 */
public record GoalState(
        String condition,
        int evaluationCount,
        long startTime,
        String latestReason
) {
    /**
     * 用目标条件创建初始状态。
     *
     * @param condition 目标条件文本
     */
    public GoalState(String condition) {
        this(condition, 0, System.currentTimeMillis(), null);
    }

    /**
     * 记录一次评判，返回评判次数 +1 的新状态。
     *
     * @param reason 本次评判理由
     * @return 更新后的目标状态
     */
    public GoalState withEvaluation(String reason) {
        return new GoalState(condition, evaluationCount + 1, startTime, reason);
    }

    /**
     * 返回目标已持续的时长，格式化为易读字符串。
     *
     * @return 形如 "42s" 或 "3m 12s" 的时长文本
     */
    public String elapsed() {
        long ms = System.currentTimeMillis() - startTime;
        long seconds = ms / 1000;
        if (seconds < 60) {
            return seconds + "s";
        }
        return (seconds / 60) + "m " + (seconds % 60) + "s";
    }
}
