package com.learnclaudecode.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TodoManager} 单元测试。
 *
 * <p>覆盖初始空状态、写入 Todo 列表、读取渲染、更新条目状态，
 * 以及若干校验规则（数量上限、空文本、非法状态、多个进行中）。
 */
class TodoManagerTest {

    private TodoManager manager;

    @BeforeEach
    void setUp() {
        manager = new TodoManager();
    }

    /** 构造一个 Todo 项 Map。 */
    private static Map<String, Object> item(String text, String status) {
        Map<String, Object> map = new HashMap<>();
        map.put("text", text);
        map.put("status", status);
        return map;
    }

    /** 初始状态应为空。 */
    @Test
    void testInitialState_empty() {
        assertEquals("No todos.", manager.render());
        assertFalse(manager.hasOpenItems());
    }

    /** update 应创建 Todo 列表并可渲染。 */
    @Test
    void testUpdate_createsList() {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(item("write code", "in_progress"));
        items.add(item("write tests", "pending"));

        String rendered = manager.update(items);

        assertTrue(rendered.contains("write code"), rendered);
        assertTrue(rendered.contains("write tests"), rendered);
        assertTrue(rendered.contains("[>]"), rendered);
        assertTrue(rendered.contains("(0/2 completed)"), rendered);
    }

    /** render 应返回当前列表。 */
    @Test
    void testRender_returnsCurrentList() {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(item("task one", "pending"));
        manager.update(items);

        String rendered = manager.render();

        assertTrue(rendered.contains("task one"), rendered);
        assertTrue(rendered.contains("[ ] #"), rendered);
    }

    /** 更新已存在条目的状态应反映到渲染结果与 hasOpenItems。 */
    @Test
    void testUpdate_existingItems() {
        List<Map<String, Object>> first = new ArrayList<>();
        first.add(item("task one", "in_progress"));
        first.add(item("task two", "pending"));
        manager.update(first);
        assertTrue(manager.hasOpenItems());

        // 全部标记完成。
        List<Map<String, Object>> second = new ArrayList<>();
        second.add(item("task one", "completed"));
        second.add(item("task two", "completed"));
        String rendered = manager.update(second);

        assertTrue(rendered.contains("(2/2 completed)"), rendered);
        assertFalse(manager.hasOpenItems(), "全部完成后无未完成项");
    }

    /** 超过 20 条应抛异常。 */
    @Test
    void testUpdate_rejectsTooMany() {
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            items.add(item("task " + i, "pending"));
        }

        assertThrows(IllegalArgumentException.class, () -> manager.update(items));
    }

    /** 空文本应抛异常。 */
    @Test
    void testUpdate_rejectsBlankText() {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(item("  ", "pending"));

        assertThrows(IllegalArgumentException.class, () -> manager.update(items));
    }

    /** 非法状态应抛异常。 */
    @Test
    void testUpdate_rejectsInvalidStatus() {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(item("task", "unknown_status"));

        assertThrows(IllegalArgumentException.class, () -> manager.update(items));
    }

    /** 同时存在多个 in_progress 应抛异常。 */
    @Test
    void testUpdate_rejectsMultipleInProgress() {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(item("task one", "in_progress"));
        items.add(item("task two", "in_progress"));

        assertThrows(IllegalArgumentException.class, () -> manager.update(items));
    }

    /** 支持使用 content 字段作为文本来源。 */
    @Test
    void testUpdate_supportsContentField() {
        Map<String, Object> map = new HashMap<>();
        map.put("content", "from content field");
        map.put("status", "pending");
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(map);

        String rendered = manager.update(items);

        assertTrue(rendered.contains("from content field"), rendered);
    }
}
