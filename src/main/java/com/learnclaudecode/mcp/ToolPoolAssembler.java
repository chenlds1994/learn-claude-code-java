package com.learnclaudecode.mcp;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工具池组装器，负责将内置工具与 MCP 发现的工具合并为统一列表。
 *
 * <p>设计思路：
 * <ul>
 *   <li>内置工具优先——同名时内置工具胜出，MCP 工具被跳过并打印警告</li>
 *   <li>工具名长度限制 64 字符（Anthropic API 约束），超长工具名会被截断并警告</li>
 *   <li>名称规范化后再做碰撞检测，避免大小写或特殊字符导致的隐性冲突</li>
 * </ul>
 *
 * <p>典型用法：
 * <pre>{@code
 * ToolPoolAssembler assembler = new ToolPoolAssembler(builtinTools, mcpClient);
 * List<Map<String, Object>> allTools = assembler.assemble();
 * }</pre>
 */
public class ToolPoolAssembler {

    /** Anthropic API 工具名最大长度。 */
    private static final int MAX_TOOL_NAME_LENGTH = 64;

    /** 内置工具定义列表。 */
    private final List<Map<String, Object>> builtinTools;

    /** MCP 客户端引用，用于获取动态发现的工具。 */
    private final McpClient mcpClient;

    /**
     * 使用内置工具和 MCP 客户端初始化组装器。
     *
     * @param builtinTools 编译期确定的内置工具列表
     * @param mcpClient MCP 客户端实例（可为 null，表示不启用 MCP）
     */
    public ToolPoolAssembler(List<Map<String, Object>> builtinTools, McpClient mcpClient) {
        this.builtinTools = builtinTools != null ? builtinTools : List.of();
        this.mcpClient = mcpClient;
    }

    /**
     * 组装最终的工具池：内置工具 + MCP 发现工具（去重、限长）。
     *
     * <p>流程：
     * <ol>
     *   <li>将所有内置工具加入结果集，记录规范化名称用于碰撞检测</li>
     *   <li>若 MCP 客户端存在已发现的工具，逐一追加</li>
     *   <li>跳过与内置工具同名的 MCP 工具（内置优先），输出警告</li>
     *   <li>跳过超过 64 字符名称的工具，输出警告</li>
     * </ol>
     *
     * @return 合并后的工具定义列表，可直接传给 Anthropic messages API
     */
    public List<Map<String, Object>> assemble() {
        List<Map<String, Object>> result = new ArrayList<>(builtinTools);
        Set<String> registeredNames = new HashSet<>();

        // 注册所有内置工具名称
        for (Map<String, Object> tool : builtinTools) {
            String name = String.valueOf(tool.getOrDefault("name", ""));
            registeredNames.add(normalizeToolName(name));
        }

        // 如果没有 MCP 客户端或没有已连接 server，直接返回内置工具
        if (mcpClient == null) {
            return result;
        }

        List<Map<String, Object>> mcpTools = mcpClient.getToolDefinitions();
        if (mcpTools.isEmpty()) {
            return result;
        }

        // 逐一追加 MCP 发现的工具
        for (Map<String, Object> mcpTool : mcpTools) {
            String rawName = String.valueOf(mcpTool.getOrDefault("name", ""));
            String normalized = normalizeToolName(rawName);

            // 碰撞检测：内置工具优先
            if (registeredNames.contains(normalized)) {
                System.err.println("[ToolPoolAssembler] WARNING: MCP tool '" + rawName
                        + "' collides with builtin tool (normalized: '" + normalized + "'). Skipped.");
                continue;
            }

            // 名称长度检测
            if (rawName.length() > MAX_TOOL_NAME_LENGTH) {
                System.err.println("[ToolPoolAssembler] WARNING: MCP tool name '" + rawName
                        + "' exceeds " + MAX_TOOL_NAME_LENGTH + " chars. Skipped.");
                continue;
            }

            registeredNames.add(normalized);
            result.add(mcpTool);
        }

        return result;
    }

    /**
     * 规范化工具名：只保留字母、数字、下划线，全部转小写。
     *
     * <p>用于碰撞检测时消除大小写和特殊字符差异。
     * 例如 "mcp__docs__search" 规范化后仍为 "mcp__docs__search"。
     *
     * @param name 原始工具名
     * @return 规范化后的名称
     */
    public static String normalizeToolName(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        return name.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
    }

    /**
     * 检测两组工具名之间是否存在碰撞。
     *
     * @param namesA 第一组工具名
     * @param namesB 第二组工具名
     * @return 碰撞的名称集合（规范化后）
     */
    public static Set<String> detectCollisions(List<String> namesA, List<String> namesB) {
        Set<String> normalizedA = new HashSet<>();
        for (String name : namesA) {
            normalizedA.add(normalizeToolName(name));
        }
        Set<String> collisions = new HashSet<>();
        for (String name : namesB) {
            String normalized = normalizeToolName(name);
            if (normalizedA.contains(normalized)) {
                collisions.add(normalized);
            }
        }
        return collisions;
    }
}
