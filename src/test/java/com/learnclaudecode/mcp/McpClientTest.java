package com.learnclaudecode.mcp;

import com.learnclaudecode.common.WorkspacePaths;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link McpClient} 单元测试。
 *
 * <p>覆盖连接（成功 / 未知 server / 重复连接）、工具发现、命名空间调用，
 * 以及 Anthropic 格式工具定义导出与连接状态列举。全部基于内置 mock server，
 * 不涉及任何真实网络调用。
 */
class McpClientTest {

    @TempDir
    Path tempDir;

    private McpClient client;

    @BeforeEach
    void setUp() {
        client = new McpClient(new WorkspacePaths(tempDir));
    }

    /** connect("docs") 应发现 docs server 的工具。 */
    @Test
    void testConnect_docs_discoversTools() {
        String result = client.connect("docs");

        assertTrue(result.contains("Connected to MCP server 'docs'"), result);
        assertTrue(result.contains("Discovered 2 tool(s)"), result);
        assertTrue(result.contains("mcp__docs__search"), result);
        assertTrue(result.contains("mcp__docs__get_version"), result);
        assertTrue(client.isConnected("docs"));
    }

    /** connect("deploy") 应发现 deploy server 的工具。 */
    @Test
    void testConnect_deploy_discoversTools() {
        String result = client.connect("deploy");

        assertTrue(result.contains("Connected to MCP server 'deploy'"), result);
        assertTrue(result.contains("mcp__deploy__trigger"), result);
        assertTrue(result.contains("mcp__deploy__status"), result);
    }

    /** connect 未知 server 返回错误。 */
    @Test
    void testConnect_unknown_returnsError() {
        String result = client.connect("nonexistent");

        assertTrue(result.startsWith("Error: Unknown MCP server"), result);
        assertFalse(client.isConnected("nonexistent"));
    }

    /** 重复连接同一 server 返回已连接提示。 */
    @Test
    void testConnect_twice_returnsAlreadyConnected() {
        client.connect("docs");

        String result = client.connect("docs");

        assertTrue(result.contains("already connected"), result);
    }

    /** callTool 使用合法命名空间名应成功路由到对应 server。 */
    @Test
    void testCallTool_validNamespacedName() {
        client.connect("docs");

        String result = client.callTool("mcp__docs__search", Map.of("query", "getting started"));

        assertTrue(result.contains("Found 3 results"), result);
        assertTrue(result.contains("getting started"), result);
    }

    /** callTool 对未知工具返回错误。 */
    @Test
    void testCallTool_unknownTool() {
        client.connect("docs");

        String result = client.callTool("mcp__unknown__tool", Map.of());

        assertTrue(result.startsWith("Error: Unknown tool"), result);
    }

    /** getToolDefinitions 应返回 Anthropic 格式的工具定义列表。 */
    @Test
    void testGetToolDefinitions_anthropicFormat() {
        client.connect("docs");

        List<Map<String, Object>> definitions = client.getToolDefinitions();

        assertEquals(2, definitions.size());
        Map<String, Object> first = definitions.get(0);
        assertTrue(first.containsKey("name"));
        assertTrue(first.containsKey("description"));
        assertTrue(first.containsKey("input_schema"));
        assertTrue(String.valueOf(first.get("name")).startsWith("mcp__docs__"));
    }

    /** 未连接任何 server 时 getToolDefinitions 返回空列表。 */
    @Test
    void testGetToolDefinitions_emptyWhenNotConnected() {
        assertTrue(client.getToolDefinitions().isEmpty());
    }

    /** listConnected 应展示已连接 server 及其工具。 */
    @Test
    void testListConnected_showsStatus() {
        client.connect("docs");

        String status = client.listConnected();

        assertTrue(status.contains("Connected MCP Servers (1)"), status);
        assertTrue(status.contains("docs"), status);
        assertTrue(status.contains("mcp__docs__search"), status);
    }

    /** 未连接时 listConnected 返回提示与可用 server 列表。 */
    @Test
    void testListConnected_noneConnected() {
        String status = client.listConnected();

        assertTrue(status.contains("No MCP servers connected"), status);
        assertTrue(status.contains("Available servers"), status);
    }
}
