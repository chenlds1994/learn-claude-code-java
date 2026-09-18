package com.learnclaudecode.mcp;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 模拟 MCP Server 基类，用于教学演示。
 *
 * <p>真实 MCP 通过 JSON-RPC over stdio/SSE 与外部进程通信，
 * 这里为了降低学习门槛，把 server 实现为进程内的 Java 对象。
 * 核心概念完全一致：listTools() 发现能力，callTool() 调用能力。
 */
public abstract class MockMcpServer {

    /** 内置 server 注册表，key 为 server 名称。 */
    private static final Map<String, MockMcpServer> REGISTRY = new HashMap<>();

    static {
        // 注册两个教学用 mock server：
        // "docs" — 演示文档搜索能力
        // "deploy" — 演示部署操作能力
        REGISTRY.put("docs", new DocsServer());
        REGISTRY.put("deploy", new DeployServer());
    }

    /**
     * 返回当前 server 的名称标识。
     *
     * @return server 名称
     */
    public abstract String getName();

    /**
     * 列出该 server 提供的所有工具。
     *
     * @return 工具信息列表
     */
    public abstract List<ToolInfo> listTools();

    /**
     * 调用指定工具并返回结果字符串。
     *
     * @param toolName 工具原始名称（不含命名空间前缀）
     * @param args 调用参数
     * @return 工具执行结果（人类可读文本）
     */
    public abstract String callTool(String toolName, Map<String, Object> args);

    /**
     * 从注册表查找指定名称的 mock server。
     *
     * @param name server 名称
     * @return 对应 server 实例，不存在时返回 null
     */
    public static MockMcpServer getServer(String name) {
        return REGISTRY.get(name);
    }

    /**
     * 返回所有已注册的 server 名称列表。
     *
     * @return server 名称列表
     */
    public static List<String> getAvailableServerNames() {
        return List.copyOf(REGISTRY.keySet());
    }

    /**
     * 工具元信息记录。
     *
     * @param name 工具原始名称
     * @param description 工具用途描述
     * @param inputSchema JSON Schema 格式的输入参数定义
     */
    public record ToolInfo(String name, String description, Map<String, Object> inputSchema) {
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 内置 Mock Server 实现
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 文档搜索 server —— 模拟一个提供文档检索能力的 MCP 服务。
     */
    private static class DocsServer extends MockMcpServer {

        @Override
        public String getName() {
            return "docs";
        }

        @Override
        public List<ToolInfo> listTools() {
            return List.of(
                    new ToolInfo("search", "Search documentation by keyword query.",
                            Map.of("type", "object",
                                    "properties", Map.of("query", Map.of("type", "string", "description", "Search query")),
                                    "required", List.of("query"))),
                    new ToolInfo("get_version", "Get the current documentation version.",
                            Map.of("type", "object", "properties", Map.of()))
            );
        }

        @Override
        public String callTool(String toolName, Map<String, Object> args) {
            return switch (toolName) {
                case "search" -> handleSearch(args);
                case "get_version" -> "Documentation version: 1.0.0 (last updated: 2026-09-15)";
                default -> "Error: Unknown tool '" + toolName + "' on server 'docs'.";
            };
        }

        private String handleSearch(Map<String, Object> args) {
            String query = String.valueOf(args.getOrDefault("query", ""));
            if (query.isBlank()) {
                return "Error: 'query' parameter is required and must not be blank.";
            }
            // 返回模拟搜索结果，展示 MCP 工具调用的典型输出格式
            return """
                    Found 3 results for "%s":
                    
                    1. [Getting Started] Introduction to the framework
                       Path: /docs/getting-started.md
                       Relevance: 0.95
                    
                    2. [API Reference] %s endpoint documentation
                       Path: /docs/api/%s.md
                       Relevance: 0.87
                    
                    3. [FAQ] Common questions about %s
                       Path: /docs/faq.md#%s
                       Relevance: 0.72""".formatted(query, query, query.toLowerCase().replace(" ", "-"), query, query.toLowerCase().replace(" ", "-"));
        }
    }

    /**
     * 部署操作 server —— 模拟一个提供部署触发与状态查询的 MCP 服务。
     */
    private static class DeployServer extends MockMcpServer {

        @Override
        public String getName() {
            return "deploy";
        }

        @Override
        public List<ToolInfo> listTools() {
            return List.of(
                    new ToolInfo("trigger", "Trigger a deployment to the specified environment.",
                            Map.of("type", "object",
                                    "properties", Map.of(
                                            "environment", Map.of("type", "string", "description", "Target environment (staging/production)"),
                                            "version", Map.of("type", "string", "description", "Version tag to deploy")),
                                    "required", List.of("environment", "version"))),
                    new ToolInfo("status", "Check deployment status by deployment ID.",
                            Map.of("type", "object",
                                    "properties", Map.of("deployment_id", Map.of("type", "string", "description", "Deployment identifier")),
                                    "required", List.of("deployment_id")))
            );
        }

        @Override
        public String callTool(String toolName, Map<String, Object> args) {
            return switch (toolName) {
                case "trigger" -> handleTrigger(args);
                case "status" -> handleStatus(args);
                default -> "Error: Unknown tool '" + toolName + "' on server 'deploy'.";
            };
        }

        private String handleTrigger(Map<String, Object> args) {
            String env = String.valueOf(args.getOrDefault("environment", ""));
            String version = String.valueOf(args.getOrDefault("version", ""));
            if (env.isBlank() || version.isBlank()) {
                return "Error: Both 'environment' and 'version' parameters are required.";
            }
            // 模拟生成部署 ID
            String deploymentId = "dep-" + Math.abs((env + version).hashCode()) % 100000;
            return """
                    Deployment triggered successfully.
                    
                    Deployment ID: %s
                    Environment:   %s
                    Version:       %s
                    Status:        queued
                    Started at:    2026-09-18T10:30:00Z
                    
                    Use deploy.status with deployment_id="%s" to check progress.""".formatted(
                    deploymentId, env, version, deploymentId);
        }

        private String handleStatus(Map<String, Object> args) {
            String deploymentId = String.valueOf(args.getOrDefault("deployment_id", ""));
            if (deploymentId.isBlank()) {
                return "Error: 'deployment_id' parameter is required.";
            }
            // 模拟返回部署状态
            return """
                    Deployment Status Report
                    
                    ID:          %s
                    Status:      in_progress
                    Progress:    67%%
                    Environment: staging
                    Version:     v2.1.0
                    Started:     2026-09-18T10:30:00Z
                    Elapsed:     4m 32s
                    
                    Steps:
                      [✓] Build artifacts
                      [✓] Upload to registry
                      [>] Rolling update (3/5 pods)
                      [ ] Health check
                      [ ] Traffic shift""".formatted(deploymentId);
        }
    }
}
