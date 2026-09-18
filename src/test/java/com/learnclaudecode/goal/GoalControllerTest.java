package com.learnclaudecode.goal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link GoalController} 单元测试。
 *
 * <p>使用 null 客户端让评判者进入启发式 mock 模式，从而无需真实 API 调用。
 * 覆盖目标设定/清除、状态查询、活跃判断、每轮评判（pass/block）
 * 以及连续 block 触发安全出口的行为。
 */
class GoalControllerTest {

    private GoalController controller;

    @BeforeEach
    void setUp() {
        // client=null -> GoalEvaluator 走 mock 启发式评判。
        controller = new GoalController(null);
    }

    /** 构造一条只含指定文本的用户消息历史。 */
    private static List<Map<String, Object>> messages(String text) {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(Map.of("role", "user", "content", text));
        return list;
    }

    /** setGoal 应创建活跃目标。 */
    @Test
    void testSetGoal_createsActiveGoal() {
        String result = controller.setGoal("All tests pass");

        assertTrue(result.startsWith("Goal set:"), result);
        assertTrue(controller.hasActiveGoal());
    }

    /** setGoal 应拒绝空条件。 */
    @Test
    void testSetGoal_rejectsEmpty() {
        String result = controller.setGoal("   ");

        assertTrue(result.startsWith("Error"), result);
        assertFalse(controller.hasActiveGoal());
    }

    /** getGoalStatus 应展示条件与已用时间。 */
    @Test
    void testGetGoalStatus_showsConditionAndElapsed() {
        controller.setGoal("Finish the report");

        String status = controller.getGoalStatus();

        assertTrue(status.contains("Active goal: Finish the report"), status);
        assertTrue(status.contains("elapsed:"), status);
    }

    /** 无目标时 getGoalStatus 返回提示语。 */
    @Test
    void testGetGoalStatus_noGoal() {
        String status = controller.getGoalStatus();

        assertTrue(status.contains("No active goal"), status);
    }

    /** clearGoal 应移除活跃目标。 */
    @Test
    void testClearGoal_removesGoal() {
        controller.setGoal("something");

        String result = controller.clearGoal();

        assertEquals("Goal cleared.", result);
        assertFalse(controller.hasActiveGoal());
    }

    /** 无目标时 clearGoal 返回相应提示。 */
    @Test
    void testClearGoal_noGoal() {
        assertEquals("No active goal.", controller.clearGoal());
    }

    /** hasActiveGoal 应正确反映目标存在与否。 */
    @Test
    void testHasActiveGoal() {
        assertFalse(controller.hasActiveGoal());
        controller.setGoal("goal");
        assertTrue(controller.hasActiveGoal());
        controller.clearGoal();
        assertFalse(controller.hasActiveGoal());
    }

    /** 无目标时 evaluateAfterTurn 直接返回 pass。 */
    @Test
    void testEvaluateAfterTurn_noGoalReturnsPass() {
        GoalDecision decision = controller.evaluateAfterTurn(messages("anything"));

        assertEquals("pass", decision.action());
        assertTrue(decision.reason().contains("No active goal"), decision.reason());
    }

    /** mock 模式下，最后消息含 "done" 应判定为 pass 并清除目标。 */
    @Test
    void testEvaluateAfterTurn_mockDoneReturnsPass() {
        controller.setGoal("complete the task");

        GoalDecision decision = controller.evaluateAfterTurn(messages("The work is done now"));

        assertEquals("pass", decision.action());
        assertFalse(controller.hasActiveGoal(), "达成目标后应清除活跃目标");
    }

    /** mock 模式下，最后消息不含完成信号应判定为 block，目标保留。 */
    @Test
    void testEvaluateAfterTurn_mockNotDoneReturnsBlock() {
        controller.setGoal("complete the task");

        GoalDecision decision = controller.evaluateAfterTurn(messages("still working on it"));

        assertEquals("block", decision.action());
        assertTrue(decision.shouldContinue());
        assertTrue(controller.hasActiveGoal(), "未达成时目标应保留");
    }

    /** 连续 block 达到安全上限后应强制返回 pass 并交还控制权。 */
    @Test
    void testEvaluateAfterTurn_safetyLimit() {
        controller.setGoal("impossible goal");

        GoalDecision last = null;
        // MAX_CONSECUTIVE_BLOCKS = 10，第 10 次 block 触发安全出口。
        for (int i = 0; i < 10; i++) {
            last = controller.evaluateAfterTurn(messages("progressing " + i));
        }

        assertEquals("pass", last.action());
        assertTrue(last.reason().contains("Safety limit reached"), last.reason());
        assertFalse(controller.hasActiveGoal(), "触发安全出口后应清除目标");
    }
}
