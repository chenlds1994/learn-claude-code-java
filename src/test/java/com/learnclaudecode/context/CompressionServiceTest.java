package com.learnclaudecode.context;

import com.learnclaudecode.common.WorkspacePaths;
import com.learnclaudecode.model.ChatMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CompressionService} 单元测试。
 *
 * <p>只测试不依赖真实模型调用的部分：token 估算、micro compact 对旧工具结果的清理，
 * 以及是否需要自动压缩的阈值判断。autoCompact 需要真实客户端，故不在此覆盖。
 */
class CompressionServiceTest {

    @TempDir
    Path tempDir;

    /** 构造一个不含真实客户端的压缩服务。 */
    private CompressionService service(int threshold, int keepRecent) {
        return new CompressionService(new WorkspacePaths(tempDir), null, threshold, keepRecent);
    }

    /** 构造一条包含 tool_result 的 user 消息，content 可变以便被清理。 */
    private static ChatMessage toolResultMessage(String content) {
        Map<String, Object> part = new HashMap<>();
        part.put("type", "tool_result");
        part.put("content", content);
        List<Object> parts = new ArrayList<>();
        parts.add(part);
        return new ChatMessage("user", parts);
    }

    /** 取出消息中 tool_result 的 content 值。 */
    @SuppressWarnings("unchecked")
    private static Object firstToolResultContent(ChatMessage message) {
        List<Object> parts = (List<Object>) message.content();
        Map<String, Object> part = (Map<String, Object>) parts.get(0);
        return part.get("content");
    }

    /** estimateTokens 应对非空历史给出正数估算。 */
    @Test
    void testEstimateTokens_positive() {
        CompressionService svc = service(1000, 2);
        List<ChatMessage> messages = List.of(new ChatMessage("user", "hello world"));

        int tokens = svc.estimateTokens(messages);

        assertTrue(tokens > 0, "非空历史估算 token 应大于 0");
    }

    /** 工具结果数量不超过 keepRecent 时，micro compact 不做清理。 */
    @Test
    void testMicroCompact_fewResultsUnchanged() {
        CompressionService svc = service(1000, 5);
        String longText = "x".repeat(200);
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(toolResultMessage(longText));
        messages.add(toolResultMessage(longText));

        svc.microCompact(messages);

        // 只有 2 条结果，keepRecent=5，全部保留原文。
        assertEquals(longText, firstToolResultContent(messages.get(0)));
        assertEquals(longText, firstToolResultContent(messages.get(1)));
    }

    /** 内容较短（<=100）的工具结果即便较旧也保持原样。 */
    @Test
    void testMicroCompact_shortContentUnchanged() {
        CompressionService svc = service(1000, 1);
        String shortText = "short result";
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(toolResultMessage(shortText));
        messages.add(toolResultMessage(shortText));
        messages.add(toolResultMessage(shortText));

        svc.microCompact(messages);

        assertEquals(shortText, firstToolResultContent(messages.get(0)));
        assertEquals(shortText, firstToolResultContent(messages.get(1)));
    }

    /** 超出 keepRecent 的较旧长工具结果应被替换为占位符。 */
    @Test
    void testMicroCompact_truncatesLongToolResults() {
        CompressionService svc = service(1000, 1);
        String longText = "y".repeat(300);
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(toolResultMessage(longText));
        messages.add(toolResultMessage(longText));
        messages.add(toolResultMessage(longText));

        svc.microCompact(messages);

        // keepRecent=1：最后一条保留，前两条被清理。
        assertEquals("[cleared]", firstToolResultContent(messages.get(0)));
        assertEquals("[cleared]", firstToolResultContent(messages.get(1)));
        assertEquals(longText, firstToolResultContent(messages.get(2)));
    }

    /** needsAutoCompact 应在超过阈值时返回 true。 */
    @Test
    void testNeedsAutoCompact_overThreshold() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("user", "z".repeat(2000)));

        CompressionService low = service(10, 2);
        assertTrue(low.needsAutoCompact(messages), "阈值很低时应触发压缩");

        CompressionService high = service(10_000_000, 2);
        assertFalse(high.needsAutoCompact(messages), "阈值极高时不应触发压缩");
    }
}
