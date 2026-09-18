package com.learnclaudecode.workflow;

import java.util.List;
import java.util.Map;

/**
 * 工作流定义，描述一个可执行的编排脚本。
 *
 * <p>这里体现 s16 的核心教学思想：“编排形状固定时，写进代码而非对话”。
 * 也就是说，工作流的多步流程由 Host（本项目）用代码注册，
 * 模型只能通过名字来调用一个已经写好的工作流，而不能提交任意可执行代码。
 * 这样既保证了编排的确定性，也避免了“让模型每次现场推理整套流程”的不稳定与高成本。
 *
 * @param name 工作流名称，模型调用时的唯一标识
 * @param description 工作流描述，用于列表展示与模型选择
 * @param phases 阶段标题列表，描述这个工作流大致会经历哪几个阶段
 * @param script 编排脚本，真正定义“按什么顺序做哪些事”
 */
public record WorkflowDefinition(
        String name,
        String description,
        List<String> phases,
        WorkflowScript script
) {
    /**
     * 工作流脚本接口——定义编排逻辑。
     *
     * <p>用函数式接口而非字符串 DSL，是为了让编排逻辑享受编译期类型检查，
     * 并且可以直接使用 {@link ExecutionState} 提供的原语（agent/parallel/pipeline/phase/log）。
     */
    @FunctionalInterface
    public interface WorkflowScript {
        /**
         * 执行编排逻辑。
         *
         * @param state 执行状态，提供编排原语与 journal 记录能力
         * @param args 模型调用工作流时提交的参数
         * @return 工作流产出结果（会被序列化落盘）
         * @throws Exception 编排过程中的任意异常，由引擎统一捕获并记录
         */
        Object execute(ExecutionState state, Map<String, Object> args) throws Exception;
    }

    /**
     * 校验工作流元数据是否合法。
     *
     * <p>name 约束为 1-64 字符，且只允许字母/数字/._- ，
     * 目的是让工作流名可以安全地用于文件命名、注册表键与模型工具参数。
     *
     * @param name 工作流名称
     * @param description 工作流描述
     * @throws IllegalArgumentException 元数据不合法时抛出
     */
    public static void validateMeta(String name, String description) {
        if (name == null || name.isEmpty() || name.length() > 64) {
            throw new IllegalArgumentException("Workflow name must be 1-64 characters");
        }
        if (!name.matches("[a-zA-Z0-9._-]+")) {
            throw new IllegalArgumentException("Workflow name contains invalid characters. Only alphanumeric, dot, underscore, hyphen allowed.");
        }
        if (description == null || description.isEmpty()) {
            throw new IllegalArgumentException("Workflow description is required");
        }
    }
}
