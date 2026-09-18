package com.learnclaudecode.agents;

/**
 * 启动记忆系统示例。
 * 演示跨会话持久记忆机制——"记住重要的，遗忘无关的"。
 *
 * 入口类刻意保持极薄：只负责挑选 s09 阶段配置并交给统一 {@link Launcher} 启动，
 * 具体的记忆增删查逻辑在 {@code com.learnclaudecode.memory.MemoryStore} 中，
 * 由 AgentRuntime 依据 StageConfig 的开关接线，避免每个入口重复初始化代码。
 *
 * @param args 命令行参数
 */
public class S09MemorySystem {
    public static void main(String[] args) {
        Launcher.launch(StageConfig.s09());
    }
}
