package com.learnclaudecode.permission;

import com.learnclaudecode.common.WorkspacePaths;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 权限管理器，实现工具级别的访问控制。
 *
 * 教学理念："先设边界，再给自由"——在允许 Agent 使用工具之前，
 * 先建立明确的权限规则，确保危险操作被拦截或需要人工确认。
 *
 * 设计要点：
 * - 规则匹配支持 * 通配符，可灵活控制一组工具；
 * - 文件工具额外检查路径沙箱，防止路径逃逸；
 * - bash 工具内置危险命令黑名单（rm -rf、format、shutdown 等）；
 * - 纯内存存储，适合教学演示场景（无需持久化）。
 */
public class PermissionManager {
    private final Path workdir;
    private final List<PermissionRule> rules = new CopyOnWriteArrayList<>();

    /** 文件类工具名称集合，这些工具需要进行路径沙箱检查。 */
    private static final Set<String> FILE_TOOLS = Set.of("read_file", "write_file", "edit_file");

    /** bash 危险命令黑名单，匹配到这些关键词时自动 deny。 */
    private static final List<String> BASH_BLACKLIST = List.of(
            "rm -rf", "format", "shutdown", "reboot",
            "mkfs", "dd if=", ":(){ :|:& };:"
    );

    /**
     * 权限规则记录，描述一条工具访问策略。
     *
     * @param toolPattern 工具名模式，支持 * 通配符（如 "bash"、"file_*"）
     * @param policy 策略：allow / deny / confirm
     */
    public record PermissionRule(String toolPattern, String policy) {
    }

    /**
     * 使用工作区路径初始化权限管理器，并加载默认规则。
     *
     * 默认规则体现了"最小权限"原则：
     * - bash 需要人工确认（因为命令影响范围大）
     * - 文件工具限制在工作区内（通过路径沙箱检查）
     * - 其他工具默认允许
     *
     * @param paths 工作区路径工具
     */
    public PermissionManager(WorkspacePaths paths) {
        this.workdir = paths.workdir();
        // 加载默认规则：bash 需确认，防止模型随意执行危险命令
        rules.add(new PermissionRule("bash", "confirm"));
        // 文件工具默认允许（路径沙箱会在 checkPermission 中额外执行）
        rules.add(new PermissionRule("read_file", "allow"));
        rules.add(new PermissionRule("write_file", "allow"));
        rules.add(new PermissionRule("edit_file", "allow"));
        // 通配符兜底规则：未显式声明的工具默认允许
        rules.add(new PermissionRule("*", "allow"));
    }

    /**
     * 检查指定工具调用是否被允许。
     *
     * 检查流程：
     * 1. 匹配规则列表，找到第一个适用的策略；
     * 2. 如果是文件工具，额外检查路径是否在工作区内；
     * 3. 如果是 bash 工具，额外检查命令是否在黑名单中。
     *
     * @param toolName 工具名称
     * @param args 工具参数
     * @return "allow"、"deny: 原因" 或 "confirm: 原因"
     */
    public String checkPermission(String toolName, Map<String, Object> args) {
        // === 第一步：文件工具的路径沙箱检查 ===
        // 无论规则如何，文件路径逃逸都必须被拦截
        if (FILE_TOOLS.contains(toolName) && args != null) {
            Object pathArg = args.get("path");
            if (pathArg == null) {
                pathArg = args.get("file_path");
            }
            if (pathArg != null) {
                String filePath = String.valueOf(pathArg);
                if (!isPathInWorkspace(filePath)) {
                    return "deny: path '" + filePath + "' escapes workspace boundary";
                }
            }
        }

        // === 第二步：bash 工具的命令黑名单检查 ===
        if ("bash".equals(toolName) && args != null) {
            Object cmdArg = args.get("command");
            if (cmdArg != null) {
                String command = String.valueOf(cmdArg).toLowerCase();
                for (String blacklisted : BASH_BLACKLIST) {
                    if (command.contains(blacklisted)) {
                        return "deny: command contains blacklisted pattern '" + blacklisted + "'";
                    }
                }
            }
        }

        // === 第三步：匹配规则列表 ===
        // 按声明顺序匹配，第一个命中的规则即为最终策略
        for (PermissionRule rule : rules) {
            if (matchesPattern(rule.toolPattern(), toolName)) {
                return switch (rule.policy()) {
                    case "allow" -> "allow";
                    case "deny" -> "deny: tool '" + toolName + "' is denied by rule [" + rule.toolPattern() + "]";
                    case "confirm" -> "confirm: tool '" + toolName + "' requires user approval (rule: " + rule.toolPattern() + ")";
                    default -> "allow";
                };
            }
        }

        // 无规则命中时默认允许（兜底）
        return "allow";
    }

    /**
     * 列出所有权限规则。
     *
     * @return 格式化的规则列表文本
     */
    public String listRules() {
        if (rules.isEmpty()) {
            return "No permission rules configured.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Permission Rules (").append(rules.size()).append("):\n");
        sb.append("-".repeat(50)).append("\n");
        for (int i = 0; i < rules.size(); i++) {
            PermissionRule rule = rules.get(i);
            String icon = switch (rule.policy()) {
                case "allow" -> "[+]";
                case "deny" -> "[-]";
                case "confirm" -> "[?]";
                default -> "[ ]";
            };
            sb.append(String.format("  %d. %s %-20s -> %s%n", i + 1, icon, rule.toolPattern(), rule.policy()));
        }
        return sb.toString();
    }

    /**
     * 添加或更新一条权限规则。
     *
     * 如果 toolPattern 已存在，则更新其策略；否则追加新规则。
     * 新规则插入到通配符兜底规则之前，确保优先级正确。
     *
     * @param toolPattern 工具名模式（支持 * 通配符）
     * @param policy 策略：allow / deny / confirm
     * @return 操作结果确认文本
     */
    public synchronized String setRule(String toolPattern, String policy) {
        if (toolPattern == null || toolPattern.isBlank()) {
            return "Error: tool_pattern is required";
        }
        if (!Set.of("allow", "deny", "confirm").contains(policy)) {
            return "Error: invalid policy '" + policy + "'. Must be one of: allow, deny, confirm";
        }

        // 检查是否已有同模式规则，有则更新
        for (int i = 0; i < rules.size(); i++) {
            if (rules.get(i).toolPattern().equals(toolPattern)) {
                PermissionRule updated = new PermissionRule(toolPattern, policy);
                rules.set(i, updated);
                return "Rule updated: " + toolPattern + " -> " + policy;
            }
        }

        // 新规则插入到通配符 "*" 兜底规则之前
        int insertIndex = rules.size();
        for (int i = 0; i < rules.size(); i++) {
            if ("*".equals(rules.get(i).toolPattern())) {
                insertIndex = i;
                break;
            }
        }
        rules.add(insertIndex, new PermissionRule(toolPattern, policy));
        return "Rule added: " + toolPattern + " -> " + policy;
    }

    /**
     * 移除指定模式的权限规则。
     *
     * @param toolPattern 要移除的工具名模式
     * @return 操作结果确认文本
     */
    public synchronized String removeRule(String toolPattern) {
        if (toolPattern == null || toolPattern.isBlank()) {
            return "Error: tool_pattern is required";
        }
        // 保护通配符兜底规则不被误删
        if ("*".equals(toolPattern)) {
            return "Error: cannot remove the wildcard (*) fallback rule";
        }
        boolean removed = rules.removeIf(r -> r.toolPattern().equals(toolPattern));
        if (removed) {
            return "Rule removed: " + toolPattern;
        }
        return "Error: no rule found for pattern '" + toolPattern + "'";
    }

    /**
     * 判断文件路径是否在工作区范围内。
     * 将路径规范化后检查是否以 workdir 开头，防止 ../ 逃逸。
     *
     * @param filePath 待检查的文件路径（可以是相对路径或绝对路径）
     * @return 路径在工作区内返回 true
     */
    private boolean isPathInWorkspace(String filePath) {
        try {
            Path resolved;
            Path candidate = Path.of(filePath);
            if (candidate.isAbsolute()) {
                resolved = candidate.normalize();
            } else {
                resolved = workdir.resolve(candidate).normalize();
            }
            return resolved.startsWith(workdir);
        } catch (Exception e) {
            // 路径解析异常视为不安全
            return false;
        }
    }

    /**
     * 判断工具名是否匹配给定模式。
     * 支持 * 作为通配符，可出现在模式的任意位置。
     * 例如："file_*" 匹配 "file_read"，"*" 匹配一切。
     *
     * @param pattern 规则模式
     * @param toolName 实际工具名
     * @return 匹配返回 true
     */
    private boolean matchesPattern(String pattern, String toolName) {
        if ("*".equals(pattern)) {
            return true;
        }
        if (!pattern.contains("*")) {
            // 无通配符时直接精确匹配
            return pattern.equals(toolName);
        }
        // 将通配符模式转换为正则表达式
        String regex = pattern.replace(".", "\\.").replace("*", ".*");
        return toolName.matches(regex);
    }
}
