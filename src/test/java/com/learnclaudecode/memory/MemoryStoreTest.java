package com.learnclaudecode.memory;

import com.learnclaudecode.common.WorkspacePaths;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MemoryStore} 单元测试。
 *
 * <p>覆盖新增（含分类/内容校验）、检索、列举、删除、整合以及跨实例持久化。
 * 所有落盘操作都发生在 JUnit 提供的临时目录中，测试之间相互隔离。
 */
class MemoryStoreTest {

    @TempDir
    Path tempDir;

    private WorkspacePaths paths;
    private MemoryStore store;

    @BeforeEach
    void setUp() {
        paths = new WorkspacePaths(tempDir);
        store = new MemoryStore(paths);
    }

    /** 从 addMemory 返回值中解析记忆 id（形如 "Stored fact memory <uuid>: ..."）。 */
    private static String extractId(String addResult) {
        int start = addResult.indexOf(" memory ") + " memory ".length();
        int end = addResult.indexOf(": ", start);
        return addResult.substring(start, end);
    }

    /** 统计 .memory 目录下的 JSON 文件数量。 */
    private long countFiles() throws IOException {
        Path dir = paths.memoryDir();
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(p -> p.getFileName().toString().endsWith(".json")).count();
        }
    }

    /** addMemory 应存储记忆并落盘为文件。 */
    @Test
    void testAddMemory_storesAndPersists() throws IOException {
        String result = store.addMemory("The project uses Java 17.", "fact");

        assertTrue(result.startsWith("Stored fact memory"), result);
        assertEquals(1, countFiles(), "记忆应落盘为一个 JSON 文件");
    }

    /** addMemory 应拒绝非法分类。 */
    @Test
    void testAddMemory_rejectsInvalidCategory() {
        String result = store.addMemory("some content", "unknown_category");

        assertTrue(result.startsWith("Error"), result);
        assertTrue(result.contains("Invalid category"), result);
    }

    /** addMemory 应拒绝空内容。 */
    @Test
    void testAddMemory_rejectsEmptyContent() {
        String result = store.addMemory("   ", "fact");

        assertTrue(result.startsWith("Error"), result);
        assertTrue(result.contains("cannot be empty"), result);
    }

    /** searchMemories 应按关键词命中记忆。 */
    @Test
    void testSearchMemories_findsByKeyword() {
        store.addMemory("The deployment pipeline runs on Kubernetes.", "fact");

        String result = store.searchMemories("Kubernetes deployment");

        assertTrue(result.startsWith("Found 1 memory"), result);
        assertTrue(result.contains("Kubernetes"), result);
    }

    /** searchMemories 无匹配时返回提示语。 */
    @Test
    void testSearchMemories_noMatch() {
        store.addMemory("The sky is blue.", "fact");

        String result = store.searchMemories("zebra elephant");

        assertTrue(result.startsWith("No memories found matching"), result);
    }

    /** listMemories 应按分类分组展示全部记忆。 */
    @Test
    void testListMemories_groupedByCategory() {
        store.addMemory("Java 17 is used.", "fact");
        store.addMemory("Prefers Chinese comments.", "preference");

        String listing = store.listMemories();

        assertTrue(listing.contains("Stored memories (2 total)"), listing);
        assertTrue(listing.contains("[fact]"), listing);
        assertTrue(listing.contains("[preference]"), listing);
    }

    /** 空库时 listMemories 返回提示语。 */
    @Test
    void testListMemories_empty() {
        assertEquals("No memories stored yet.", store.listMemories());
    }

    /** deleteMemory 应移除内存记录与磁盘文件。 */
    @Test
    void testDeleteMemory_removesRecordAndFile() throws IOException {
        String id = extractId(store.addMemory("temporary note", "fact"));
        assertEquals(1, countFiles());

        String result = store.deleteMemory(id);

        assertTrue(result.startsWith("Deleted memory"), result);
        assertEquals(0, countFiles(), "删除后磁盘文件应被清理");
        assertTrue(store.listMemories().contains("No memories stored yet"));
    }

    /** deleteMemory 对未知 id 返回错误。 */
    @Test
    void testDeleteMemory_unknownId() {
        String result = store.deleteMemory("no-such-id");

        assertTrue(result.startsWith("Error"), result);
        assertTrue(result.contains("not found"), result);
    }

    /** consolidateMemories 应合并高度相似的重复记忆。 */
    @Test
    void testConsolidateMemories_mergesSimilar() throws IOException {
        // 两条内容完全相同的记忆，Jaccard 相似度为 1.0，应被整合为一条。
        store.addMemory("the quick brown fox jumps over the lazy dog", "fact");
        store.addMemory("the quick brown fox jumps over the lazy dog", "fact");
        assertEquals(2, countFiles());

        String result = store.consolidateMemories();

        assertTrue(result.startsWith("Consolidated 1 duplicate"), result);
        assertEquals(1, countFiles(), "整合后只保留最新的一条");
    }

    /** 记忆不足两条时无可整合。 */
    @Test
    void testConsolidateMemories_tooFew() {
        store.addMemory("only one", "fact");

        String result = store.consolidateMemories();

        assertTrue(result.contains("Nothing to consolidate"), result);
    }

    /** 持久化：新实例应加载此前保存的记忆。 */
    @Test
    void testPersistence_newInstanceLoadsSaved() {
        store.addMemory("Persisted across restarts.", "fact");

        // 用同一工作目录构造新实例，模拟重启后重新加载。
        MemoryStore reloaded = new MemoryStore(new WorkspacePaths(tempDir));

        String listing = reloaded.listMemories();
        assertTrue(listing.contains("Persisted across restarts"), listing);
        assertTrue(reloaded.searchMemories("Persisted restarts").startsWith("Found 1 memory"));
        assertFalse(reloaded.listMemories().contains("No memories stored yet"));
    }
}
