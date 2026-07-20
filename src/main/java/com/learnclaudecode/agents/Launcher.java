package com.learnclaudecode.agents;

/**
 * 统一入口启动器。
 *
 * 项目里每个 `S01` 到 `S12` 的 main 类都非常薄，
 * 它们只负责选择某一个 StageConfig，然后交给这个 Launcher 启动。
 *
 * 这样做的意义是避免每个入口类重复写初始化代码。
 */
public final class Launcher {
    /**
     * 禁止外部实例化工具型启动器。
     */
    private Launcher() {
    }

    /**
     * 使用指定阶段配置启动交互式 Agent 运行时。
     *
     * @param config 当前阶段的能力配置
     */
    public static void launch(StageConfig config) {
        // 先创建完整应用上下文，再把某个阶段配置交给统一运行时执行。
        new AppContext().runtime().runRepl(config);
    }
}
