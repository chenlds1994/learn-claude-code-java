package com.learnclaudecode.goal;

import com.learnclaudecode.common.AnthropicClient;
import com.learnclaudecode.common.JsonUtils;
import com.learnclaudecode.model.AnthropicResponse;

import java.util.List;
import java.util.Map;

/**
 * 目标评判者：一次独立的模型调用，判断目标条件是否已达成。
 *
 * <p>这里体现 s17 的关键设计——“评判者与执行者分离”：
 * 主循环里的 Agent 负责干活，而是否“干完了”由一个不带任何工具、
 * 只能读取对话历史的独立评判者来裁定。这样能避免执行者既当运动员又当裁判，
 * 让停止条件更客观。
 *
 * <p>为便于教学演示，当没有配置 {@link AnthropicClient}（例如缺少 API Key）时，
 * 会自动退化为启发式的 mock 评判，让本阶段无需真实模型也能跑通。
 */
public class GoalEvaluator {
    /** 评判者的 system prompt：明确其“只判断、不动手”的独立角色与输出格式。 */
    private static final String EVALUATOR_SYSTEM =
            "You are an independent evaluator. Judge whether the following goal condition has been met "
                    + "based solely on the conversation history provided. You have no tools and cannot act; "
                    + "you can only judge. Respond in JSON only: "
                    + "{\"ok\": bool, \"reason\": \"...\", \"impossible\": bool}";

    /** 评判时最多回看的消息条数，控制输入规模。 */
    private static final int MAX_MESSAGES = 10;
    /** 单条消息截断长度，避免超长内容撑爆评判请求。 */
    private static final int MAX_MESSAGE_CHARS = 500;

    private final AnthropicClient client;

    /**
     * 初始化评判者。
     *
     * @param client 模型客户端；传 null 时启用启发式 mock 评判
     */
    public GoalEvaluator(AnthropicClient client) {
        this.client = client;
    }

    /**
     * 评判目标条件是否达成。
     *
     * @param condition 目标条件文本
     * @param messages 当前对话历史
     * @return 评判决定；出错时以 block 兜底，保证“宁可继续也不误停”
     */
    public GoalDecision evaluate(String condition, List<Map<String, Object>> messages) {
        // 没有真实模型客户端时，走教学用启发式评判。
        if (client == null) {
            return mockEvaluate(condition, messages);
        }
        try {
            String prompt = buildEvaluationPrompt(condition, messages);
            // 评判者不携带任何工具（tools 传空列表），只能基于对话历史做判断。
            AnthropicResponse response = client.createMessage(
                    EVALUATOR_SYSTEM,
                    List.of(Map.of("role", "user", "content", prompt)),
                    List.of(),
                    500);
            return parseDecision(extractText(response));
        } catch (RuntimeException e) {
            // fail-safe：评判失败时不轻易终止循环，返回 block 让 Agent 继续尝试。
            return GoalDecision.block("Evaluation error: " + e.getMessage());
        }
    }

    /**
     * 构造评判请求的用户消息内容。
     *
     * <p>包含目标条件与截断后的最近对话：只取最后 {@value #MAX_MESSAGES} 条，
     * 每条最多 {@value #MAX_MESSAGE_CHARS} 字符，兼顾信息量与请求体积。
     *
     * @param condition 目标条件文本
     * @param messages 当前对话历史
     * @return 拼好的评判提示词
     */
    private String buildEvaluationPrompt(String condition, List<Map<String, Object>> messages) {
        StringBuilder sb = new StringBuilder();
        sb.append("Goal condition:\n").append(condition).append("\n\n");
        sb.append("Conversation history (most recent, truncated):\n");
        if (messages == null || messages.isEmpty()) {
            sb.append("(empty)\n");
        } else {
            int from = Math.max(0, messages.size() - MAX_MESSAGES);
            for (int i = from; i < messages.size(); i++) {
                Map<String, Object> message = messages.get(i);
                String role = String.valueOf(message.getOrDefault("role", "?"));
                String text = stringifyContent(message.get("content"));
                if (text.length() > MAX_MESSAGE_CHARS) {
                    text = text.substring(0, MAX_MESSAGE_CHARS) + "...";
                }
                sb.append(role).append(": ").append(text).append('\n');
            }
        }
        sb.append("\nReturn JSON only.");
        return sb.toString();
    }

    /**
     * 从模型响应中提取第一段文本内容。
     *
     * @param response 模型响应
     * @return 文本内容；无文本块时返回空串
     */
    private String extractText(AnthropicResponse response) {
        if (response == null || response.content() == null) {
            return "";
        }
        return response.content().stream()
                .filter(block -> block != null && block.containsKey("text"))
                .map(block -> String.valueOf(block.get("text")))
                .findFirst()
                .orElse("");
    }

    /**
     * 解析评判者返回的 JSON，并映射为 {@link GoalDecision}。
     *
     * @param text 评判者原始文本（可能带 markdown 代码块）
     * @return 评判决定；无法解析时以 block 兜底
     */
    @SuppressWarnings("unchecked")
    private GoalDecision parseDecision(String text) {
        try {
            String json = extractJsonObject(text);
            Map<String, Object> raw = JsonUtils.fromJson(json, Map.class);
            boolean ok = Boolean.TRUE.equals(raw.get("ok"));
            boolean impossible = Boolean.TRUE.equals(raw.get("impossible"));
            String reason = raw.get("reason") == null ? "(no reason)" : String.valueOf(raw.get("reason"));
            if (ok) {
                return impossible ? GoalDecision.impossible(reason) : GoalDecision.pass(reason);
            }
            return GoalDecision.block(reason);
        } catch (RuntimeException e) {
            return GoalDecision.block("Evaluation error: could not parse evaluator output: " + text);
        }
    }

    /**
     * 从可能带 markdown 代码块的文本中截取第一个完整 JSON 对象。
     *
     * @param text 原始文本
     * @return JSON 子串
     */
    private String extractJsonObject(String text) {
        if (text == null) {
            return "{}";
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text.strip();
    }

    /**
     * 教学用启发式评判：无 API Key 时也能演示目标循环。
     *
     * <p>规则很简单——看最后一条消息是否包含“完成”类信号词
     * （done / complete / pass），命中即视为达成，否则继续。
     *
     * @param condition 目标条件文本
     * @param messages 当前对话历史
     * @return 评判决定
     */
    private GoalDecision mockEvaluate(String condition, List<Map<String, Object>> messages) {
        String last = "";
        if (messages != null && !messages.isEmpty()) {
            last = stringifyContent(messages.get(messages.size() - 1).get("content")).toLowerCase();
        }
        boolean done = last.contains("done") || last.contains("complete") || last.contains("pass");
        if (done) {
            return GoalDecision.pass("[mock] goal appears satisfied based on last message");
        }
        return GoalDecision.block("[mock] goal not yet satisfied; keep working toward: " + condition);
    }

    /**
     * 把消息 content 统一转成字符串。
     *
     * <p>content 可能是纯文本，也可能是结构化内容块列表（含 tool_use/tool_result），
     * 这里统一序列化，方便截断与拼接。
     *
     * @param content 原始 content
     * @return 文本形式的内容
     */
    private String stringifyContent(Object content) {
        if (content == null) {
            return "";
        }
        if (content instanceof String text) {
            return text;
        }
        return JsonUtils.toJson(content);
    }
}
