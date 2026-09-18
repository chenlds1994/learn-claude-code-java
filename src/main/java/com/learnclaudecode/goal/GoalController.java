package com.learnclaudecode.goal;

import com.learnclaudecode.common.AnthropicClient;

import java.util.List;
import java.util.Map;

/**
 * 目标循环控制器：管理会话级的活跃目标，并在每轮结束后驱动评判。
 *
 * <p>GoalController 是 s17 的“会话级 Stop hook”：
 * 主循环每完成一轮，就调用 {@link #evaluateAfterTurn(List)} 让独立评判者裁定目标是否达成；
 * 未达成（block）则回到同一循环继续工作，达成（pass）则清除目标、允许停止。
 *
 * <p>为防止“评判者一直说没完成”导致的死循环，这里维护 consecutiveBlocks 计数器，
 * 连续 block 达到 {@value #MAX_CONSECUTIVE_BLOCKS} 次时触发安全出口，把控制权交还用户。
 */
public class GoalController {
    /** 连续 block 的安全上限，超过即强制终止循环。 */
    private static final int MAX_CONSECUTIVE_BLOCKS = 10;

    private final GoalEvaluator evaluator;
    /** 当前活跃目标；为 null 表示没有设定目标。 */
    private GoalState activeGoal;
    /** 连续 block 计数，用于安全出口判断。 */
    private int consecutiveBlocks;

    /**
     * 初始化目标控制器，并创建内部评判者。
     *
     * @param client 模型客户端；传 null 时评判者退化为启发式 mock 模式
     */
    public GoalController(AnthropicClient client) {
        this.evaluator = new GoalEvaluator(client);
    }

    /**
     * 设定一个新的目标条件。
     *
     * @param condition 目标条件文本
     * @return 人类可读的确认信息
     */
    public synchronized String setGoal(String condition) {
        if (condition == null || condition.isBlank()) {
            return "Error: goal condition must not be empty.";
        }
        // 设定新目标即开启一段新的循环，重置连续 block 计数。
        this.activeGoal = new GoalState(condition.strip());
        this.consecutiveBlocks = 0;
        return "Goal set: " + activeGoal.condition()
                + ". The agent will continue working until this condition is met.";
    }

    /**
     * 清除当前目标，停止循环。
     *
     * @return 人类可读的确认信息
     */
    public synchronized String clearGoal() {
        if (activeGoal == null) {
            return "No active goal.";
        }
        activeGoal = null;
        consecutiveBlocks = 0;
        return "Goal cleared.";
    }

    /**
     * 返回当前目标与评判状态的可读摘要。
     *
     * @return 状态文本
     */
    public synchronized String getGoalStatus() {
        if (activeGoal == null) {
            return "No active goal. Use goal_set to define one.";
        }
        return "Active goal: " + activeGoal.condition() + "\n"
                + "  elapsed: " + activeGoal.elapsed() + "\n"
                + "  evaluations: " + activeGoal.evaluationCount() + "\n"
                + "  consecutive blocks: " + consecutiveBlocks + "/" + MAX_CONSECUTIVE_BLOCKS + "\n"
                + "  latest reason: " + (activeGoal.latestReason() == null ? "(none yet)" : activeGoal.latestReason());
    }

    /**
     * 每轮结束后评判目标是否达成，并据此更新循环状态。
     *
     * <p>处理规则：
     * <ul>
     *   <li>无活跃目标：直接返回 pass（无需循环）；</li>
     *   <li>block：累加连续计数，达到安全上限则强制 pass 并清除目标；</li>
     *   <li>pass：清除目标，允许循环停止；</li>
     *   <li>defer：保留目标，但不计入连续 block（重置计数）。</li>
     * </ul>
     *
     * @param messages 当前对话历史，作为评判输入
     * @return 评判决定
     */
    public synchronized GoalDecision evaluateAfterTurn(List<Map<String, Object>> messages) {
        if (activeGoal == null) {
            return GoalDecision.pass("No active goal");
        }

        GoalDecision decision = evaluator.evaluate(activeGoal.condition(), messages);
        // 无论结果如何，都记录一次评判，更新计数与最近理由。
        activeGoal = activeGoal.withEvaluation(decision.reason());

        if (decision.shouldContinue()) {
            consecutiveBlocks++;
            if (consecutiveBlocks >= MAX_CONSECUTIVE_BLOCKS) {
                // 安全出口：连续多轮仍判定未完成，强制把控制权交还用户，避免死循环。
                activeGoal = null;
                consecutiveBlocks = 0;
                return GoalDecision.pass("Safety limit reached (" + MAX_CONSECUTIVE_BLOCKS
                        + " consecutive blocks). Returning control to user.");
            }
            return decision;
        }

        // 非 block：中断连续 block 计数。
        consecutiveBlocks = 0;
        if ("pass".equals(decision.action())) {
            // 目标达成（或不可达而终止），清除目标让循环可以停止。
            activeGoal = null;
        }
        return decision;
    }

    /**
     * 判断是否存在活跃目标。
     *
     * @return 有目标时返回 true
     */
    public synchronized boolean hasActiveGoal() {
        return activeGoal != null;
    }
}
