package com.learnclaudecode.agents;

/**
 * 启动钩子系统示例。
 * 演示如何在主循环关键点注入扩展逻辑——"围绕循环挂钩子，而非重写循环"。
 *
 * @param args 命令行参数
 */
public class S04HookSystem {
    public static void main(String[] args) {
        Launcher.launch(StageConfig.s04());
    }
}
