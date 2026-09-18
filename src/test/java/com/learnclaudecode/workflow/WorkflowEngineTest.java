package com.learnclaudecode.workflow;

import com.learnclaudecode.common.WorkspacePaths;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link WorkflowEngine} 与 {@link WorkflowDefinition} 单元测试。
 *
 * <p>覆盖内置工作流列举、注册（含非法名拒绝）、执行（成功 / 未知）、
 * journal 落盘、Resume 命中缓存跳过重复 agent，以及元数据校验。
 */
class WorkflowEngineTest {

    @TempDir
    Path tempDir;

    private WorkspacePaths paths;
    private WorkflowEngine engine;

    @BeforeEach
    void setUp() {
        paths = new WorkspacePaths(tempDir);
        engine = new WorkflowEngine(paths);
    }

    /** 从 executeWorkflow 摘要中解析 runId。 */
    private static String extractRunId(String summary) {
        int start = summary.indexOf("runId: ") + "runId: ".length();
        int end = summary.indexOf('\n', start);
        return summary.substring(start, end).trim();
    }

    /** listWorkflows 应包含预注册的 review-changes。 */
    @Test
    void testListWorkflows_showsPreRegistered() {
        String listing = engine.listWorkflows();

        assertTrue(listing.contains("Registered workflows"), listing);
        assertTrue(listing.contains("review-changes"), listing);
    }

    /** registerWorkflow 应新增工作流。 */
    @Test
    void testRegisterWorkflow_addsNew() {
        String result = engine.registerWorkflow("my-flow", "A custom flow",
                List.of("Step1"),
                (state, args) -> {
                    state.phase("Step1");
                    return Map.of("ok", true);
                });

        assertTrue(result.contains("Registered workflow 'my-flow'"), result);
        assertTrue(engine.listWorkflows().contains("my-flow"));
    }

    /** registerWorkflow 应拒绝含非法字符的名称。 */
    @Test
    void testRegisterWorkflow_rejectsInvalidName() {
        assertThrows(IllegalArgumentException.class, () ->
                engine.registerWorkflow("bad name!", "desc", List.of("P"),
                        (state, args) -> null));
    }

    /** executeWorkflow 对已注册工作流应成功执行。 */
    @Test
    void testExecuteWorkflow_knownSucceeds() {
        String summary = engine.executeWorkflow("review-changes", Map.of(), null);

        assertTrue(summary.contains("Workflow 'review-changes' completed"), summary);
        assertTrue(summary.contains("agents run: 6"), summary);
        assertTrue(summary.contains("phases completed: 2"), summary);
    }

    /** executeWorkflow 对未知工作流返回错误。 */
    @Test
    void testExecuteWorkflow_unknownReturnsError() {
        String summary = engine.executeWorkflow("does-not-exist", Map.of(), null);

        assertTrue(summary.startsWith("Error: Unknown workflow"), summary);
    }

    /** journal 与 output 应落盘到 .runtime 目录。 */
    @Test
    void testJournal_persistedToDisk() {
        String summary = engine.executeWorkflow("review-changes", Map.of(), null);
        String runId = extractRunId(summary);

        Path journal = paths.runtimeDir().resolve(runId + ".journal.jsonl");
        Path output = paths.runtimeDir().resolve(runId + ".output.json");

        assertTrue(Files.exists(journal), "journal 文件应存在: " + journal);
        assertTrue(Files.exists(output), "output 文件应存在: " + output);
    }

    /** Resume 应命中缓存并跳过已执行的 agent。 */
    @Test
    void testResume_skipsCachedAgents() {
        String first = engine.executeWorkflow("review-changes", Map.of(), null);
        String runId = extractRunId(first);
        assertTrue(first.contains("cached hits: 0"), first);

        String resumed = engine.executeWorkflow("review-changes", Map.of(), runId);

        assertTrue(resumed.contains("resumedFrom: " + runId), resumed);
        // 全部 6 个 agent 命中缓存，无需真实执行。
        assertTrue(resumed.contains("cached hits: 6"), resumed);
        assertTrue(resumed.contains("agents run: 0"), resumed);
    }

    /** getRunStatus 应基于落盘 journal 返回统计信息。 */
    @Test
    void testGetRunStatus_reportsJournal() {
        String summary = engine.executeWorkflow("review-changes", Map.of(), null);
        String runId = extractRunId(summary);

        String status = engine.getRunStatus(runId);

        assertTrue(status.contains("Run " + runId), status);
        assertTrue(status.contains("journal entries:"), status);
        assertTrue(status.contains("agents: 6"), status);
    }

    /** getRunStatus 对未知 runId 返回未找到提示。 */
    @Test
    void testGetRunStatus_unknownRun() {
        String status = engine.getRunStatus("no-such-run");

        assertTrue(status.contains("No journal found"), status);
    }

    /** validateMeta 对合法元数据不抛异常。 */
    @Test
    void testValidateMeta_valid() {
        assertDoesNotThrow(() -> WorkflowDefinition.validateMeta("good_name-1.0", "desc"));
    }

    /** validateMeta 对非法名称/描述抛出异常。 */
    @Test
    void testValidateMeta_invalid() {
        assertThrows(IllegalArgumentException.class,
                () -> WorkflowDefinition.validateMeta("", "desc"));
        assertThrows(IllegalArgumentException.class,
                () -> WorkflowDefinition.validateMeta(null, "desc"));
        assertThrows(IllegalArgumentException.class,
                () -> WorkflowDefinition.validateMeta("has space", "desc"));
        assertThrows(IllegalArgumentException.class,
                () -> WorkflowDefinition.validateMeta("ok", ""));
        assertThrows(IllegalArgumentException.class,
                () -> WorkflowDefinition.validateMeta("a".repeat(65), "desc"));
    }
}
