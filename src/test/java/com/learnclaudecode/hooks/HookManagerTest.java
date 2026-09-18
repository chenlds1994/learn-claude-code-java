package com.learnclaudecode.hooks;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link HookManager} 单元测试。
 *
 * <p>覆盖钩子注册（含非法类型拒绝）、列举、移除、按类型过滤，
 * 以及 log / block 动作触发时的返回值语义。
 */
class HookManagerTest {

    private HookManager manager;

    @BeforeEach
    void setUp() {
        manager = new HookManager();
    }

    /** 从 registerHook 返回值中解析出钩子 id。 */
    private static String extractId(String registerResult) {
        // 形如 "Hook registered: id=abcd1234, type=..., action=..."
        int start = registerResult.indexOf("id=") + 3;
        int end = registerResult.indexOf(',', start);
        return registerResult.substring(start, end);
    }

    /** 合法类型应成功注册钩子。 */
    @Test
    void testRegisterHook_validType() {
        String result = manager.registerHook("PreToolUse", "log");

        assertTrue(result.startsWith("Hook registered"), result);
        assertTrue(result.contains("type=PreToolUse"), result);
    }

    /** 非法类型应被拒绝。 */
    @Test
    void testRegisterHook_rejectsInvalidType() {
        String result = manager.registerHook("NotARealType", "log");

        assertTrue(result.startsWith("Error"), result);
        assertTrue(result.contains("invalid hook type"), result);
    }

    /** 空 action 应被拒绝。 */
    @Test
    void testRegisterHook_rejectsBlankAction() {
        String result = manager.registerHook("PreToolUse", "");

        assertTrue(result.startsWith("Error"), result);
    }

    /** listHooks 应展示已注册钩子。 */
    @Test
    void testListHooks_showsRegistered() {
        manager.registerHook("PreToolUse", "log");
        manager.registerHook("Stop", "audit");

        String listing = manager.listHooks();

        assertTrue(listing.contains("Registered Hooks (2)"), listing);
        assertTrue(listing.contains("PreToolUse"), listing);
        assertTrue(listing.contains("Stop"), listing);
    }

    /** 无钩子时 listHooks 返回提示语。 */
    @Test
    void testListHooks_empty() {
        assertEquals("No hooks registered.", manager.listHooks());
    }

    /** removeHook 应按 id 删除钩子。 */
    @Test
    void testRemoveHook_byId() {
        String id = extractId(manager.registerHook("PreToolUse", "log"));

        String result = manager.removeHook(id);

        assertTrue(result.startsWith("Hook removed"), result);
        assertEquals("No hooks registered.", manager.listHooks());
    }

    /** removeHook 对未知 id 返回错误。 */
    @Test
    void testRemoveHook_unknownId() {
        String result = manager.removeHook("does-not-exist");

        assertTrue(result.startsWith("Error"), result);
        assertTrue(result.contains("no hook found"), result);
    }

    /** triggerHooks 对 log 动作返回 pass。 */
    @Test
    void testTriggerHooks_logReturnsPass() {
        manager.registerHook("PreToolUse", "log");

        String result = manager.triggerHooks("PreToolUse",
                HookContext.preToolUse("bash", Map.of("command", "ls")));

        assertEquals("pass", result);
    }

    /** triggerHooks 对 block 动作返回 block: 前缀并短路。 */
    @Test
    void testTriggerHooks_blockReturnsBlock() {
        manager.registerHook("PreToolUse", "block");

        String result = manager.triggerHooks("PreToolUse",
                HookContext.preToolUse("dangerous_tool", Map.of()));

        assertTrue(result.startsWith("block:"), result);
        assertTrue(result.contains("dangerous_tool"), result);
    }

    /** 无匹配钩子时 triggerHooks 返回 pass。 */
    @Test
    void testTriggerHooks_noHooksReturnsPass() {
        assertEquals("pass", manager.triggerHooks("Stop", HookContext.stop("done")));
    }

    /** getHooksForType 应只返回匹配类型的钩子。 */
    @Test
    void testGetHooksForType_filters() {
        manager.registerHook("PreToolUse", "log");
        manager.registerHook("PreToolUse", "block");
        manager.registerHook("Stop", "audit");

        List<HookManager.Hook> preHooks = manager.getHooksForType("PreToolUse");
        List<HookManager.Hook> stopHooks = manager.getHooksForType("Stop");

        assertEquals(2, preHooks.size());
        assertEquals(1, stopHooks.size());
        assertEquals("Stop", stopHooks.get(0).type());
    }
}
