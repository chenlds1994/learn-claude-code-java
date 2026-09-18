package com.learnclaudecode.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link WorkspacePaths} 单元测试。
 *
 * <p>覆盖工作目录规范化、安全路径解析（防逃逸）、文本读写
 * 以及各子目录的自动创建行为。
 */
class WorkspacePathsTest {

    @TempDir
    Path tempDir;

    /** workdir 应返回规范化后的绝对路径。 */
    @Test
    void testWorkdir_normalized() {
        WorkspacePaths paths = new WorkspacePaths(tempDir);

        assertEquals(tempDir.toAbsolutePath().normalize(), paths.workdir());
    }

    /** safeResolve 应正确解析工作区内的相对路径。 */
    @Test
    void testSafeResolve_insideWorkspace() {
        WorkspacePaths paths = new WorkspacePaths(tempDir);

        Path resolved = paths.safeResolve("sub/dir/file.txt");

        assertTrue(resolved.startsWith(paths.workdir()));
        assertTrue(resolved.toString().endsWith("file.txt"));
    }

    /** safeResolve 对逃逸路径应抛异常。 */
    @Test
    void testSafeResolve_rejectsEscape() {
        WorkspacePaths paths = new WorkspacePaths(tempDir);

        assertThrows(IllegalArgumentException.class,
                () -> paths.safeResolve("../../etc/passwd"));
    }

    /** writeText / readText 应能往返读写文本并自动建目录。 */
    @Test
    void testWriteAndReadText() throws IOException {
        WorkspacePaths paths = new WorkspacePaths(tempDir);

        paths.writeText("nested/note.txt", "hello 世界");
        String content = paths.readText("nested/note.txt");

        assertEquals("hello 世界", content);
        assertTrue(Files.exists(paths.workdir().resolve("nested/note.txt")));
    }

    /** 记忆/定时/运行时/MCP 目录访问器应自动创建目录。 */
    @Test
    void testEnsureDirAccessors_createDirectories() {
        WorkspacePaths paths = new WorkspacePaths(tempDir);

        assertTrue(Files.isDirectory(paths.memoryDir()));
        assertTrue(Files.isDirectory(paths.cronsDir()));
        assertTrue(Files.isDirectory(paths.runtimeDir()));
        assertTrue(Files.isDirectory(paths.mcpDir()));
    }

    /** 各子目录路径应位于工作目录之下。 */
    @Test
    void testSubDirectories_underWorkdir() {
        WorkspacePaths paths = new WorkspacePaths(tempDir);

        assertTrue(paths.tasksDir().startsWith(paths.workdir()));
        assertTrue(paths.teamDir().startsWith(paths.workdir()));
        assertTrue(paths.inboxDir().startsWith(paths.teamDir()));
        assertTrue(paths.skillsDir().startsWith(paths.workdir()));
        assertTrue(paths.transcriptDir().startsWith(paths.workdir()));
        assertTrue(paths.worktreesDir().startsWith(paths.workdir()));
    }
}
