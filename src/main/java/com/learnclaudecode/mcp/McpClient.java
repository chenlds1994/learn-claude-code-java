package com.learnclaudecode.mcp;

import com.learnclaudecode.common.WorkspacePaths;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 客户端，负责连接 mock server、发现工具并代理调用。
 *
 * <p>设计思路：
 * <ul>
 *   <li>连接时自动调用 server.listTools() 发现工具，并以 mcp__{server}__{tool} 格式注册到本地工具池</li>
 *   <li>调用时解析命名空间，路由到对应 server 的 callTool()</li>
 *   <li>getToolDefinitions() 返回 Anthropic API 兼容的工具定义，供 ToolPoolAssembler 合并</li>
 * </ul>
 *
 * <p>线程安全：写操作（connect）使用 synchronized 保护；读操作无需加锁，
 * 因为 connectedServers/discoveredTools 只在 connect 时写入。
 */
public class McpClient {

    /** 工作区路径引用，预留用于持久化 MCP 连接配置。 */
    private final WorkspacePaths paths;

    /** 已连接的 server 实例映射：serverName → server。 */
    private final Map<String, MockMcpServer> connectedServers = new LinkedHashMap<>();

    /** 已发现的工具映射：namespacedName → ToolDefinition。 */
    private final Map<String, ToolDefinition> discoveredTools = new LinkedHashMap<>();

    /**
     * 使用工作区路径初始化 MCP 客户端。
     *
     * @param paths 工作区路径工具
     */
    public McpClient(WorkspacePaths paths) {
        this.paths = paths;
    }

    /**
     * 连接指定名称的 MCP server 并发现其工具。
     *
     * <p>连接流程：
     * <ol>
     *   <li>在 MockMcpServer 注册表中查找目标 server</li>
     *   <li>检查是否已连接（幂等保护）</li>
     *   <li>调用 server.listTools() 获取工具清单</li>
     *   <li>为每个工具生成命名空间名称并注册到 discoveredTools</li>
     * </ol>
     *
     * @param serverName 目标 server 名称（如 "docs"、"deploy"）
     * @return 人类可读的连接结果描述
     */
    public synchronized String connect(String serverName) {
        // 查找 server
        MockMcpServer server = MockMcpServer.getServer(serverName);
        if (server == null) {
            return "Error: Unknown MCP server: " + serverName
                    + ". Available: " + String.join(", ", MockMcpServer.getAvailableServerNames());
        }

        // 幂等检查：已连接则直接返回
        if (connectedServers.containsKey(serverName)) {
            return "Server '" + serverName + "' is already connected.";
        }

        // 发现工具
        List<MockMcpServer.ToolInfo> tools = server.listTools();
        connectedServers.put(serverName, server);

        StringBuilder result = new StringBuilder();
        result.append("Connected to MCP server '").append(serverName).append("'.\n");
        result.append("Discovered ").append(tools.size()).append(" tool(s):\n\n");

        for (MockMcpServer.ToolInfo tool : tools) {
            // 生成命名空间名称：mcp__{server}__{tool}
            String namespacedName = buildNamespacedName(serverName, tool.name());
            ToolDefinition definition = new ToolDefinition(
                    namespacedName, serverName, tool.name(), tool.description(), tool.inputSchema());
            discoveredTools.put(namespacedName, definition);

            result.append("  • ").append(namespacedName).append("\n");
            result.append("    ").append(tool.description()).append("\n");
        }

        return result.toString().stripTrailing();
    }

    /**
     * 调用已发现的 MCP 工具。
     *
     * <p>根据命名空间名称解析出 server 和原始工具名，然后委托给对应 server 执行。
     *
     * @param namespacedName 命名空间工具名（如 "mcp__docs__search"）
     * @param args 调用参数
     * @return 工具执行结果字符串，出错时返回 "Error: ..." 格式
     */
    public String callTool(String namespacedName, Map<String, Object> args) {
        ToolDefinition definition = discoveredTools.get(namespacedName);
        if (definition == null) {
            return "Error: Unknown tool '" + namespacedName + "'. Not discovered yet.";
        }

        MockMcpServer server = connectedServers.get(definition.serverName());
        if (server == null) {
            return "Error: Server not connected. Use connect_mcp first.";
        }

        try {
            return server.callTool(definition.originalName(), args);
        } catch (Exception e) {
            return "Error calling " + namespacedName + ": " + e.getMessage();
        }
    }

    /**
     * 返回所有已发现工具的 Anthropic API 兼容定义。
     *
     * <p>输出格式与 StageConfig.tool() 一致：{name, description, input_schema}。
     * 供 ToolPoolAssembler 合并到主工具池。
     *
     * @return 工具定义列表
     */
    public List<Map<String, Object>> getToolDefinitions() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ToolDefinition def : discoveredTools.values()) {
            Map<String, Object> toolMap = new HashMap<>();
            toolMap.put("name", def.namespacedName());
            toolMap.put("description", def.description());
            toolMap.put("input_schema", def.inputSchema());
            result.add(toolMap);
        }
        return result;
    }

    /**
     * 返回所有已连接 server 及其工具的状态摘要。
     *
     * @return 人类可读的连接状态文本
     */
    public String listConnected() {
        if (connectedServers.isEmpty()) {
            return "No MCP servers connected. Use connect_mcp to attach a server.\n"
                    + "Available servers: " + String.join(", ", MockMcpServer.getAvailableServerNames());
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Connected MCP Servers (").append(connectedServers.size()).append("):\n\n");

        for (Map.Entry<String, MockMcpServer> entry : connectedServers.entrySet()) {
            sb.append("  ┌─ ").append(entry.getKey()).append("\n");
            // 列出该 server 下的工具
            List<String> serverTools = discoveredTools.values().stream()
                    .filter(d -> d.serverName().equals(entry.getKey()))
                    .map(ToolDefinition::namespacedName)
                    .toList();
            for (int i = 0; i < serverTools.size(); i++) {
                String prefix = (i == serverTools.size() - 1) ? "  └─" : "  ├─";
                sb.append(prefix).append(" ").append(serverTools.get(i)).append("\n");
            }
        }

        return sb.toString().stripTrailing();
    }

    /**
     * 判断指定 server 是否已连接。
     *
     * @param serverName server 名称
     * @return 已连接返回 true
     */
    public boolean isConnected(String serverName) {
        return connectedServers.containsKey(serverName);
    }

    /**
     * 返回工作区路径引用。
     *
     * @return 工作区路径工具
     */
    public WorkspacePaths paths() {
        return paths;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 内部辅助
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 构建命名空间工具名：mcp__{normalizedServer}__{normalizedTool}。
     * 非字母数字字符替换为下划线，确保名称安全。
     */
    private static String buildNamespacedName(String serverName, String toolName) {
        return "mcp__" + normalize(serverName) + "__" + normalize(toolName);
    }

    /**
     * 名称规范化：只保留字母、数字和下划线，其余替换为下划线。
     */
    private static String normalize(String raw) {
        return raw.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    /**
     * 已发现工具的定义记录。
     *
     * @param namespacedName 带命名空间的完整工具名
     * @param serverName 来源 server 名称
     * @param originalName 工具在 server 上的原始名称
     * @param description 工具描述
     * @param inputSchema JSON Schema 输入定义
     */
    public record ToolDefinition(
            String namespacedName,
            String serverName,
            String originalName,
            String description,
            Map<String, Object> inputSchema
    ) {
    }
}
