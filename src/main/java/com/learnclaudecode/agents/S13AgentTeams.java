package com.learnclaudecode.agents;

/**
 * 启动多 Agent 协作示例。
 * 合并了原 S09(Agent Teams)、S10(Team Protocols)、S11(Autonomous Agents)、S12(Worktree Isolation)
 * 四个阶段的全部能力为一个完整的"Lead + 持久 Teammates"运行时。
 *
 * <p>教学要点：
 * <ul>
 *   <li>Lead 提议 → 用户确认 → spawn teammate</li>
 *   <li>文件型邮箱通信 (.team/inbox/)</li>
 *   <li>类型化控制消息 (shutdown/plan approval)</li>
 *   <li>自治任务认领 (claim_task + idle 循环)</li>
 *   <li>Worktree 目录隔离 (每个任务独立工作树)</li>
 * </ul>
 *
 * @param args 命令行参数
 */
public class S13AgentTeams {
    public static void main(String[] args) {
        Launcher.launch(StageConfig.s13());
    }
}
