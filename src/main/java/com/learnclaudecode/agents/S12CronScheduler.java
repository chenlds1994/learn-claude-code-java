package com.learnclaudecode.agents;

/**
 * 启动定时调度示例。
 * 演示持久化定时任务机制——"按计划触发，无需人工干预"。
 *
 * 入口类刻意保持极薄：只负责挑选 s12 阶段配置并交给统一 {@link Launcher} 启动，
 * 具体的调度逻辑在 {@code com.learnclaudecode.scheduler.CronScheduler} 中，
 * 由 AgentRuntime 依据 StageConfig 的开关接线，避免每个入口重复初始化代码。
 *
 * @param args 命令行参数
 */
public class S12CronScheduler {
    public static void main(String[] args) {
        Launcher.launch(StageConfig.s12());
    }
}
