package com.learnclaudecode.agents;

/**
 * 启动工作流运行时示例。
 * 演示固定编排形状的脚本化执行——“编排形状固定时，写进代码而非对话”。
 *
 * <p>教学要点：
 * <ul>
 *   <li>Workflow 注册表与元数据校验</li>
 *   <li>编排原语：agent / parallel / pipeline / phase / log</li>
 *   <li>Journal 持久化与 Resume（确定性语义 key）</li>
 *   <li>脚本决定编排，模型只提交名字和参数</li>
 * </ul>
 *
 * @param args 命令行参数
 */
public class S16WorkflowRuntime {
    public static void main(String[] args) {
        Launcher.launch(StageConfig.s16());
    }
}
