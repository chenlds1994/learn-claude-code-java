package com.learnclaudecode.permission;

import com.learnclaudecode.common.WorkspacePaths;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PermissionManager} 单元测试。
 *
 * <p>覆盖默认规则加载、路径沙箱、bash 黑名单、规则增删查以及通配符匹配。
 * 每个测试方法使用独立的临时工作目录，保证互不干扰。
 */
class PermissionManagerTest {

    @TempDir
    Path tempDir;

    private PermissionManager manager;

    @BeforeEach
    void setUp() {
        manager = new PermissionManager(new WorkspacePaths(tempDir));
    }

    /** 默认规则应包含 bash 确认策略与文件工具放行策略。 */
    @Test
    void testDefaultRules_exist() {
        String rules = manager.listRules();

        assertTrue(rules.contains("bash"), rules);
        assertTrue(rules.contains("read_file"), rules);
        assertTrue(rules.contains("write_file"), rules);
        assertTrue(rules.contains("edit_file"), rules);
        // 通配符兜底规则存在。
        assertTrue(rules.contains("*"), rules);
    }

    /** 安全操作（普通工具 / 工作区内文件）应返回 allow。 */
    @Test
    void testCheckPermission_allowsSafeOperations() {
        // 未知工具命中通配符兜底规则 -> allow
        assertEquals("allow", manager.checkPermission("some_random_tool", Map.of()));

        // 工作区内的相对路径文件读取 -> allow
        assertEquals("allow",
                manager.checkPermission("read_file", Map.of("path", "notes/todo.md")));
    }

    /** bash 危险命令（黑名单）应返回 deny。 */
    @Test
    void testCheckPermission_deniesBlacklistedBash() {
        String rmResult = manager.checkPermission("bash", Map.of("command", "rm -rf /important"));
        assertTrue(rmResult.startsWith("deny"), rmResult);
        assertTrue(rmResult.contains("rm -rf"), rmResult);

        String formatResult = manager.checkPermission("bash", Map.of("command", "format C:"));
        assertTrue(formatResult.startsWith("deny"), formatResult);
        assertTrue(formatResult.contains("format"), formatResult);
    }

    /** 普通 bash 命令不在黑名单，但命中 confirm 规则需要人工确认。 */
    @Test
    void testCheckPermission_confirmsSafeBash() {
        String result = manager.checkPermission("bash", Map.of("command", "ls -la"));

        assertTrue(result.startsWith("confirm"), result);
    }

    /** 文件工具操作工作区外路径应返回 deny（路径逃逸）。 */
    @Test
    void testCheckPermission_deniesFileOutsideWorkspace() {
        String result = manager.checkPermission("write_file", Map.of("path", "../escape.txt"));

        assertTrue(result.startsWith("deny"), result);
        assertTrue(result.contains("escapes workspace"), result);
    }

    /** setRule 应新增一条规则并在 listRules 中可见。 */
    @Test
    void testSetRule_addsNewRule() {
        String setResult = manager.setRule("network_fetch", "deny");

        assertTrue(setResult.startsWith("Rule added"), setResult);
        // 新规则立即生效。
        assertTrue(manager.checkPermission("network_fetch", Map.of()).startsWith("deny"));
        assertTrue(manager.listRules().contains("network_fetch"));
    }

    /** setRule 对已存在模式应执行更新而非重复追加。 */
    @Test
    void testSetRule_updatesExistingRule() {
        String result = manager.setRule("bash", "allow");

        assertTrue(result.startsWith("Rule updated"), result);
        assertEquals("allow", manager.checkPermission("bash", Map.of("command", "ls")));
    }

    /** setRule 应拒绝非法策略。 */
    @Test
    void testSetRule_rejectsInvalidPolicy() {
        String result = manager.setRule("tool_x", "maybe");

        assertTrue(result.startsWith("Error"), result);
    }

    /** listRules 应展示全部已注册规则。 */
    @Test
    void testListRules_showsAllRules() {
        manager.setRule("custom_tool", "confirm");

        String rules = manager.listRules();

        assertTrue(rules.contains("Permission Rules"), rules);
        assertTrue(rules.contains("custom_tool"), rules);
        assertTrue(rules.contains("confirm"), rules);
    }

    /** 通配符模式 mcp__* 应匹配 mcp__docs__search。 */
    @Test
    void testWildcardMatching_works() {
        manager.setRule("mcp__*", "deny");

        String result = manager.checkPermission("mcp__docs__search", Map.of());

        assertTrue(result.startsWith("deny"), result);
        assertTrue(result.contains("mcp__docs__search"), result);
    }

    /** removeRule 应移除指定规则，移除后恢复兜底放行。 */
    @Test
    void testRemoveRule_removesRule() {
        manager.setRule("temp_tool", "deny");
        assertTrue(manager.checkPermission("temp_tool", Map.of()).startsWith("deny"));

        String removed = manager.removeRule("temp_tool");

        assertTrue(removed.startsWith("Rule removed"), removed);
        assertEquals("allow", manager.checkPermission("temp_tool", Map.of()));
    }

    /** removeRule 对通配符兜底规则应拒绝删除。 */
    @Test
    void testRemoveRule_protectsWildcard() {
        String result = manager.removeRule("*");

        assertTrue(result.startsWith("Error"), result);
    }
}
