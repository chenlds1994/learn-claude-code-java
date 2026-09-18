package com.learnclaudecode.agents;

/**
 * 启动集成运行时示例。
 * 不引入新机制，将前面所有章节的能力连成一个完整可运行系统——“众多机制，一个循环”。
 *
 * <p>本阶段是全部能力的汇聚点：
 * <ul>
 *   <li>S01-S02: Agent Loop + Tool Use</li>
 *   <li>S03-S04: Permission + Hooks</li>
 *   <li>S05-S08: Todo + Subagent + Skills + Compression</li>
 *   <li>S09: Memory</li>
 *   <li>S10-S12: Tasks + Background + Cron</li>
 *   <li>S13-S14: Teams + MCP</li>
 * </ul>
 *
 * @param args 命令行参数
 */
public class S15IntegratedHarness {
    public static void main(String[] args) {
        Launcher.launch(StageConfig.s15());
    }
}
