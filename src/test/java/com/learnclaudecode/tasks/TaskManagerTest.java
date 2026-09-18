package com.learnclaudecode.tasks;

import com.learnclaudecode.common.JsonUtils;
import com.learnclaudecode.common.WorkspacePaths;
import com.learnclaudecode.model.TaskRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TaskManager} 单元测试。
 *
 * <p>覆盖创建、列举、读取、状态更新、依赖设置与文件持久化。
 * 每个测试使用独立临时目录，任务文件落在 .tasks/ 下。
 */
class TaskManagerTest {

    @TempDir
    Path tempDir;

    private WorkspacePaths paths;
    private TaskManager manager;

    @BeforeEach
    void setUp() {
        paths = new WorkspacePaths(tempDir);
        manager = new TaskManager(paths);
    }

    /** create 应持久化任务并返回其 JSON。 */
    @Test
    void testCreate_persistsTask() {
        String json = manager.create("Build feature", "Implement the widget");

        TaskRecord record = JsonUtils.fromJson(json, TaskRecord.class);
        assertEquals(1, record.id);
        assertEquals("Build feature", record.subject);
        assertEquals("pending", record.status);

        Path file = paths.tasksDir().resolve("task_1.json");
        assertTrue(Files.exists(file), "任务文件应落盘: " + file);
    }

    /** listAll 应展示全部任务。 */
    @Test
    void testListAll_showsAllTasks() {
        manager.create("Task A", "desc A");
        manager.create("Task B", "desc B");

        String listing = manager.listAll();

        assertTrue(listing.contains("Task A"), listing);
        assertTrue(listing.contains("Task B"), listing);
        assertTrue(listing.contains("#1"), listing);
        assertTrue(listing.contains("#2"), listing);
    }

    /** 无任务时 listAll 返回提示语。 */
    @Test
    void testListAll_empty() {
        assertEquals("No tasks.", manager.listAll());
    }

    /** get 应返回单个任务详情。 */
    @Test
    void testGet_returnsSingleTask() {
        manager.create("Single task", "detail");

        String json = manager.get(1);
        TaskRecord record = JsonUtils.fromJson(json, TaskRecord.class);

        assertEquals(1, record.id);
        assertEquals("Single task", record.subject);
    }

    /** update 应变更任务状态。 */
    @Test
    void testUpdate_changesStatus() {
        manager.create("Task", "desc");

        String json = manager.update(1, "in_progress", null, null);
        TaskRecord record = JsonUtils.fromJson(json, TaskRecord.class);

        assertEquals("in_progress", record.status);
    }

    /** update 应能追加 blockedBy 依赖。 */
    @Test
    void testUpdate_withBlockedBy() {
        manager.create("First", "d");
        manager.create("Second", "d");

        String json = manager.update(2, null, List.of(1), null);
        TaskRecord record = JsonUtils.fromJson(json, TaskRecord.class);

        assertTrue(record.blockedBy.contains(1), "任务 2 应被任务 1 阻塞");
    }

    /** 完成任务后，其它任务对它的 blockedBy 依赖应被自动清除。 */
    @Test
    void testUpdate_completedClearsDependency() {
        manager.create("First", "d");
        manager.create("Second", "d");
        manager.update(2, null, List.of(1), null);

        // 完成任务 1。
        manager.update(1, "completed", null, null);

        TaskRecord second = JsonUtils.fromJson(manager.get(2), TaskRecord.class);
        assertTrue(second.blockedBy.isEmpty(), "前置任务完成后依赖应被清除");
    }

    /** update 对 deleted 状态应删除任务文件。 */
    @Test
    void testUpdate_deletedRemovesFile() {
        manager.create("To delete", "d");
        Path file = paths.tasksDir().resolve("task_1.json");
        assertTrue(Files.exists(file));

        String result = manager.update(1, "deleted", null, null);

        assertEquals("Task 1 deleted", result);
        assertTrue(!Files.exists(file), "deleted 后任务文件应被移除");
    }

    /** claim 应设置 owner 并把状态推进为 in_progress。 */
    @Test
    void testClaim_setsOwner() {
        manager.create("Claimable", "d");

        String result = manager.claim(1, "alice");

        assertTrue(result.contains("Claimed task #1 for alice"), result);
        TaskRecord record = JsonUtils.fromJson(manager.get(1), TaskRecord.class);
        assertEquals("alice", record.owner);
        assertEquals("in_progress", record.status);
    }

    /** 任务状态应跨实例持久化。 */
    @Test
    void testPersistence_acrossInstances() {
        manager.create("Persisted task", "detail");
        manager.update(1, "in_progress", null, null);

        // 用同一目录构造新实例，模拟重新加载。
        TaskManager reloaded = new TaskManager(new WorkspacePaths(tempDir));
        TaskRecord record = JsonUtils.fromJson(reloaded.get(1), TaskRecord.class);

        assertEquals("Persisted task", record.subject);
        assertEquals("in_progress", record.status);
    }
}
