package com.learnclaudecode.common;

import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link JsonUtils} 单元测试。
 *
 * <p>覆盖序列化、格式化序列化与反序列化三条主路径，
 * 验证工具类对普通 Map / 嵌套结构 / 泛型类型的处理能力。
 */
class JsonUtilsTest {

    /** toJson 应把对象序列化为紧凑 JSON 字符串。 */
    @Test
    void testToJson_serializesObjects() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("a", 1);
        value.put("b", "text");

        String json = JsonUtils.toJson(value);

        assertNotNull(json);
        // 紧凑格式：不含换行，键值直接相邻。
        assertTrue(json.contains("\"a\":1"), json);
        assertTrue(json.contains("\"b\":\"text\""), json);
        assertTrue(!json.contains("\n"), "紧凑 JSON 不应包含换行");
    }

    /** toPrettyJson 应输出带缩进和换行的格式化 JSON。 */
    @Test
    void testToPrettyJson_formatsWithIndentation() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("name", "learn");
        value.put("count", 3);

        String pretty = JsonUtils.toPrettyJson(value);

        assertNotNull(pretty);
        assertTrue(pretty.contains("\n"), "格式化 JSON 应包含换行");
        // Jackson 默认缩进为两个空格。
        assertTrue(pretty.contains("  \"name\""), pretty);
    }

    /** fromJson(Class) 应把 JSON 反序列化为指定类型。 */
    @Test
    @SuppressWarnings("unchecked")
    void testFromJson_deserializesToMap() {
        String json = "{\"a\":1,\"b\":\"text\"}";

        Map<String, Object> result = JsonUtils.fromJson(json, Map.class);

        assertNotNull(result);
        assertEquals(1, ((Number) result.get("a")).intValue());
        assertEquals("text", result.get("b"));
    }

    /** fromJson(TypeReference) 应支持泛型类型反序列化。 */
    @Test
    void testFromJson_deserializesGenericType() {
        String json = "[{\"id\":1},{\"id\":2}]";

        List<Map<String, Object>> result =
                JsonUtils.fromJson(json, new TypeReference<List<Map<String, Object>>>() {
                });

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(1, ((Number) result.get(0).get("id")).intValue());
        assertEquals(2, ((Number) result.get(1).get("id")).intValue());
    }

    /** 序列化与反序列化应可往返（round-trip）保持数据一致。 */
    @Test
    @SuppressWarnings("unchecked")
    void testRoundTrip_preservesData() {
        Map<String, Object> original = new LinkedHashMap<>();
        original.put("key", "value");
        original.put("nested", Map.of("x", 10));

        String json = JsonUtils.toJson(original);
        Map<String, Object> restored = JsonUtils.fromJson(json, Map.class);

        assertEquals("value", restored.get("key"));
        Map<String, Object> nested = (Map<String, Object>) restored.get("nested");
        assertEquals(10, ((Number) nested.get("x")).intValue());
    }
}
