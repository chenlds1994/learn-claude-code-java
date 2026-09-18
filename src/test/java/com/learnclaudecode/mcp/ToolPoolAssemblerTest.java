package com.learnclaudecode.mcp;

import com.learnclaudecode.common.WorkspacePaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ToolPoolAssembler} 单元测试。
 *
 * <p>覆盖无 MCP 连接时的纯内置工具组装、有 MCP 连接时的合并、
 * 工具名规范化以及碰撞检测。
 */
class ToolPoolAssemblerTest {

    @TempDir
    Path tempDir;

    /** 构造一个简单的内置工具定义。 */
    private static Map<String, Object> builtin(String name) {
        return Map.of("name", name, "description", "builtin " + name,
                "input_schema", Map.of("type", "object"));
    }

    /** 无 MCP 客户端时 assemble 只返回内置工具。 */
    @Test
    void testAssemble_noMcp_returnsBuiltinOnly() {
        List<Map<String, Object>> builtins = List.of(builtin("read_file"), builtin("bash"));
        ToolPoolAssembler assembler = new ToolPoolAssembler(builtins, null);

        List<Map<String, Object>> result = assembler.assemble();

        assertEquals(2, result.size());
        assertEquals("read_file", result.get(0).get("name"));
    }

    /** 有 MCP 连接时 assemble 返回内置 + MCP 工具。 */
    @Test
    void testAssemble_withMcp_returnsBuiltinPlusMcp() {
        McpClient client = new McpClient(new WorkspacePaths(tempDir));
        client.connect("docs"); // 发现 2 个工具

        List<Map<String, Object>> builtins = new ArrayList<>();
        builtins.add(builtin("read_file"));
        ToolPoolAssembler assembler = new ToolPoolAssembler(builtins, client);

        List<Map<String, Object>> result = assembler.assemble();

        // 1 个内置 + 2 个 MCP 工具。
        assertEquals(3, result.size());
        assertTrue(result.stream().anyMatch(t -> "read_file".equals(t.get("name"))));
        assertTrue(result.stream().anyMatch(t -> "mcp__docs__search".equals(t.get("name"))));
        assertTrue(result.stream().anyMatch(t -> "mcp__docs__get_version".equals(t.get("name"))));
    }

    /** 内置工具与 MCP 工具同名时内置优先，MCP 工具被跳过。 */
    @Test
    void testAssemble_collision_builtinWins() {
        McpClient client = new McpClient(new WorkspacePaths(tempDir));
        client.connect("docs");

        // 故意放一个与 MCP 工具规范化后同名的内置工具。
        List<Map<String, Object>> builtins = new ArrayList<>();
        builtins.add(builtin("mcp__docs__search"));
        ToolPoolAssembler assembler = new ToolPoolAssembler(builtins, client);

        List<Map<String, Object>> result = assembler.assemble();

        // search 因碰撞被跳过，只剩内置 search + MCP get_version。
        assertEquals(2, result.size());
        long searchCount = result.stream()
                .filter(t -> "mcp__docs__search".equals(t.get("name"))).count();
        assertEquals(1, searchCount, "碰撞时只保留内置工具");
    }

    /** normalizeToolName 应把特殊字符替换为下划线并转小写。 */
    @Test
    void testNormalizeToolName_replacesSpecialChars() {
        assertEquals("mcp_docs_search", ToolPoolAssembler.normalizeToolName("MCP-Docs.Search"));
        assertEquals("mcp__docs__search", ToolPoolAssembler.normalizeToolName("mcp__docs__search"));
        assertEquals("abc_123", ToolPoolAssembler.normalizeToolName("abc 123"));
        assertEquals("", ToolPoolAssembler.normalizeToolName(null));
        assertEquals("", ToolPoolAssembler.normalizeToolName("   "));
    }

    /** detectCollisions 应在规范化后检测出同名冲突。 */
    @Test
    void testDetectCollisions_works() {
        Set<String> collisions = ToolPoolAssembler.detectCollisions(
                List.of("read_file", "Bash"),
                List.of("bash", "write_file"));

        // "Bash" 与 "bash" 规范化后同为 "bash"，应检测出碰撞。
        assertTrue(collisions.contains("bash"), collisions.toString());
        assertEquals(1, collisions.size());
    }

    /** 无碰撞时 detectCollisions 返回空集。 */
    @Test
    void testDetectCollisions_none() {
        Set<String> collisions = ToolPoolAssembler.detectCollisions(
                List.of("a", "b"), List.of("c", "d"));

        assertTrue(collisions.isEmpty());
    }
}
