package com.learnclaudecode.agents;

/**
 * 启动 MCP 插件系统示例。
 * 演示通过 MCP 协议动态发现和调用外部工具——"能力不够？插上 MCP 扩展"。
 *
 * <p>教学要点：
 * <ul>
 *   <li>MCP 客户端连接与工具发现</li>
 *   <li>工具命名空间 (mcp__server__tool)</li>
 *   <li>动态工具池组装</li>
 *   <li>进程内 Mock Server 演示</li>
 * </ul>
 *
 * @param args 命令行参数
 */
public class S14McpPlugin {
    public static void main(String[] args) {
        Launcher.launch(StageConfig.s14());
    }
}
