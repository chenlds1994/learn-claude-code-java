package com.learnclaudecode.agents;

/**
 * 启动目标循环示例。
 * 演示独立评判者决定循环是否终止——“目标决定循环何时可以停止”。
 *
 * <p>教学要点：
 * <ul>
 *   <li>Goal 是会话级 Stop hook</li>
 *   <li>评判者与执行者分离（独立模型调用）</li>
 *   <li>对话即评判输入</li>
 *   <li>未完成工作回到同一循环（block → continue）</li>
 *   <li>安全出口：MAX_TURNS + 连续 block 上限</li>
 * </ul>
 *
 * @param args 命令行参数
 */
public class S17GoalLoop {
    public static void main(String[] args) {
        Launcher.launch(StageConfig.s17());
    }
}
