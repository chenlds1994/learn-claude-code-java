package com.learnclaudecode.scheduler;

import com.learnclaudecode.common.WorkspacePaths;
import com.learnclaudecode.model.CronJob;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CronScheduler} 与 {@link CronJob} 单元测试。
 *
 * <p>覆盖登记（含表达式校验）、列举、取消、待处理队列，
 * 以及下次触发时间计算和守护线程到点触发的行为。
 */
class CronSchedulerTest {

    @TempDir
    Path tempDir;

    private WorkspacePaths paths;
    private CronScheduler scheduler;

    @BeforeEach
    void setUp() {
        paths = new WorkspacePaths(tempDir);
        scheduler = new CronScheduler(paths);
    }

    @AfterEach
    void tearDown() {
        // 确保守护线程被停止，避免影响其它测试。
        scheduler.stop();
    }

    /** 从 scheduleCron 返回值中解析任务 id（形如 "Scheduled cron <uuid> [...]"）。 */
    private static String extractId(String scheduleResult) {
        int start = "Scheduled cron ".length();
        int end = scheduleResult.indexOf(' ', start);
        return scheduleResult.substring(start, end);
    }

    /** 统计 .crons 目录下的 JSON 文件数量。 */
    private long countFiles() throws IOException {
        Path dir = paths.cronsDir();
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(p -> p.getFileName().toString().endsWith(".json")).count();
        }
    }

    /** scheduleCron 应创建任务并持久化。 */
    @Test
    void testScheduleCron_createsAndPersists() throws IOException {
        String result = scheduler.scheduleCron("every_5m", "check status", false);

        assertTrue(result.startsWith("Scheduled cron"), result);
        assertTrue(result.contains("every_5m"), result);
        assertEquals(1, countFiles(), "任务应落盘为一个 JSON 文件");
    }

    /** scheduleCron 应拒绝非法表达式。 */
    @Test
    void testScheduleCron_rejectsInvalidExpression() {
        String result = scheduler.scheduleCron("not_a_cron", "do something", false);

        assertTrue(result.startsWith("Error"), result);
        assertTrue(result.contains("Invalid cron expression"), result);
    }

    /** scheduleCron 应拒绝空 prompt。 */
    @Test
    void testScheduleCron_rejectsEmptyPrompt() {
        String result = scheduler.scheduleCron("every_5m", "  ", false);

        assertTrue(result.startsWith("Error"), result);
    }

    /** listCrons 应展示全部任务。 */
    @Test
    void testListCrons_showsAll() {
        scheduler.scheduleCron("every_5m", "job one", false);
        scheduler.scheduleCron("every_1h", "job two", true);

        String listing = scheduler.listCrons();

        assertTrue(listing.contains("Scheduled cron jobs (2 total)"), listing);
        assertTrue(listing.contains("job one"), listing);
        assertTrue(listing.contains("job two"), listing);
    }

    /** 无任务时 listCrons 返回提示语。 */
    @Test
    void testListCrons_empty() {
        assertEquals("No scheduled cron jobs.", scheduler.listCrons());
    }

    /** cancelCron 应把任务标记为失活。 */
    @Test
    void testCancelCron_deactivates() {
        String id = extractId(scheduler.scheduleCron("every_5m", "to cancel", false));

        String result = scheduler.cancelCron(id);

        assertTrue(result.startsWith("Cancelled cron"), result);
        assertTrue(scheduler.listCrons().contains("inactive"), scheduler.listCrons());
    }

    /** cancelCron 对未知 id 返回错误。 */
    @Test
    void testCancelCron_unknownId() {
        String result = scheduler.cancelCron("no-such-id");

        assertTrue(result.startsWith("Error"), result);
        assertTrue(result.contains("not found"), result);
    }

    /** 无触发时 drainPendingPrompts 返回空列表。 */
    @Test
    void testDrainPendingPrompts_emptyWhenNothingTriggered() {
        scheduler.scheduleCron("every_1h", "future job", false);

        List<String> drained = scheduler.drainPendingPrompts();

        assertTrue(drained.isEmpty(), "尚未到点的任务不应产生待处理 prompt");
    }

    /** calculateNextTrigger 应正确计算周期型表达式的下次触发时间。 */
    @Test
    void testCalculateNextTrigger_intervals() {
        long now = System.currentTimeMillis();

        CronJob every30s = new CronJob("id1", "every_30s", "p", false);
        long next30 = every30s.calculateNextTrigger();
        assertTrue(next30 > now, "every_30s 的下次触发应在未来");
        assertTrue(next30 <= now + 30_000L + 1_000L, "间隔应约为 30 秒");

        CronJob every5m = new CronJob("id2", "every_5m", "p", false);
        long next5m = every5m.calculateNextTrigger();
        assertTrue(next5m > now + 60_000L, "every_5m 的下次触发应远在 1 分钟之后");
        assertTrue(next5m <= now + 300_000L + 1_000L, "间隔应约为 5 分钟");
    }

    /** calculateNextTrigger 对 at_HH:MM 应返回未来时刻。 */
    @Test
    void testCalculateNextTrigger_atTime() {
        long now = System.currentTimeMillis();

        CronJob job = new CronJob("id3", "at_23:59", "p", false);
        long next = job.calculateNextTrigger();

        assertTrue(next > now, "at_ 表达式的下次触发应在未来");
    }

    /** isValidExpression 应正确区分合法与非法表达式。 */
    @Test
    void testIsValidExpression() {
        assertTrue(CronJob.isValidExpression("every_30s"));
        assertTrue(CronJob.isValidExpression("every_5m"));
        assertTrue(CronJob.isValidExpression("every_1h"));
        assertTrue(CronJob.isValidExpression("at_14:30"));

        assertFalse(CronJob.isValidExpression("every_0s"));
        assertFalse(CronJob.isValidExpression("every_abc"));
        assertFalse(CronJob.isValidExpression("at_99:99"));
        assertFalse(CronJob.isValidExpression("random"));
        assertFalse(CronJob.isValidExpression(null));
    }

    /** 启动守护线程后，短周期任务到点应把 prompt 压入队列。 */
    @Test
    void testStart_tickTriggersJob() throws InterruptedException {
        scheduler.scheduleCron("every_1s", "tick prompt", false);
        scheduler.start();

        // 等待超过一个周期，让守护线程有机会触发任务。
        Thread.sleep(2_500L);

        List<String> drained = scheduler.drainPendingPrompts();
        scheduler.stop();

        assertFalse(drained.isEmpty(), "every_1s 任务在 2.5 秒内应至少触发一次");
        assertTrue(drained.get(0).contains("tick prompt"), drained.get(0));
        assertTrue(drained.get(0).startsWith("[CRON:"), drained.get(0));
    }
}
