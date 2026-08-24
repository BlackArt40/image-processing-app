package com.sjs.image.task;

import com.sjs.image.common.TaskStatus;
import com.sjs.image.common.TaskType;
import com.sjs.image.dto.ProcessOptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 任务状态机（TaskRecord）单元测试。
 * 覆盖 QUEUED → PROCESSING → PAUSED → QUEUED → SUCCESS 的关键流转，
 * 这是「暂停/继续」功能的正确性基础。
 */
class TaskRecordTest {

    private TaskRecord newRecord() {
        return new TaskRecord("id1", TaskType.ENHANCE, "a.jpg", "stored.jpg");
    }

    @Test
    void 初始状态为等待处理() {
        TaskRecord record = newRecord();
        assertEquals(TaskStatus.QUEUED, record.getStatus());
        assertEquals(0, record.getProgress());
    }

    @Test
    void 进度回调进入处理中状态() {
        TaskRecord record = newRecord();
        record.update(50, "放大");
        assertEquals(TaskStatus.PROCESSING, record.getStatus());
        assertEquals(50, record.getProgress());
        assertEquals("放大", record.getStage());
    }

    @Test
    void 成功时记录结果与元信息() {
        TaskRecord record = newRecord();
        record.update(80, "放大");
        record.succeed("out.png", "scale=2x");

        assertEquals(TaskStatus.SUCCESS, record.getStatus());
        assertEquals(100, record.getProgress());
        assertEquals("out.png", record.getResultStoreName());
        assertEquals("scale=2x", record.getSourceMeta());
    }

    @Test
    void 暂停请求标记可被进度回调检测() {
        TaskRecord record = newRecord();
        record.update(10, "开始");
        record.requestPause();

        assertTrue(record.isPauseRequested(), "请求暂停后标志应为 true");
        record.markPaused();
        assertEquals(TaskStatus.PAUSED, record.getStatus());
    }

    @Test
    void 继续后清除暂停标志并回到等待处理() {
        TaskRecord record = newRecord();
        record.update(10, "开始");
        record.requestPause();
        record.markPaused();
        assertEquals(TaskStatus.PAUSED, record.getStatus());

        record.resume();
        assertFalse(record.isPauseRequested(), "继续后暂停标志应清除");
        assertEquals(TaskStatus.QUEUED, record.getStatus());
        assertEquals("等待处理", record.getStage());
    }

    @Test
    void 已暂停或已结束后不再更新进度() {
        TaskRecord record = newRecord();
        record.markPaused();
        record.update(90, "不应推进");
        assertEquals(0, record.getProgress(), "暂停后不应再推进进度");

        record.succeed("out.png", null);
        record.update(99, "不应再变");
        assertEquals(TaskStatus.SUCCESS, record.getStatus());
        assertEquals(100, record.getProgress());
    }

    @Test
    void 失败记录错误信息() {
        TaskRecord record = newRecord();
        record.fail("处理失败: boom");
        assertEquals(TaskStatus.FAILED, record.getStatus());
        assertEquals("处理失败: boom", record.getError());
        assertEquals("处理失败", record.getStage());
    }

    @Test
    void 参数可供暂停后继续复用() {
        TaskRecord record = newRecord();
        ProcessOptions opts = new ProcessOptions();
        opts.setScale(4);
        record.setOptions(opts);
        assertEquals(4, record.getOptions().getScale());
    }

    @Test
    void 运行租约同一时刻仅一个worker可持有() {
        TaskRecord record = newRecord();
        assertTrue(record.tryBeginRun(), "首个 worker 应能获取租约");
        assertFalse(record.tryBeginRun(), "第二个 worker 应获取失败，防止并发双执行");
        record.endRun();
        assertTrue(record.tryBeginRun(), "释放后应可再次获取");
        record.endRun();
    }

    @Test
    void 终态判定与完成时间() {
        TaskRecord record = newRecord();
        assertFalse(record.isTerminal(), "初始排队态不是终态");
        assertNull(record.getFinishedAt(), "未完成时无完成时间");

        record.succeed("out.png", "meta");
        assertTrue(record.isTerminal());
        assertNotNull(record.getFinishedAt());

        TaskRecord failed = newRecord();
        failed.fail("boom");
        assertTrue(failed.isTerminal());
        assertNotNull(failed.getFinishedAt());
    }

    @Test
    void 结束态不可再回到队列() {
        TaskRecord record = newRecord();
        record.succeed("out.png", null);
        record.resume();
        // resume 仅改 paused 与状态字段，终态不应被绕过
        assertTrue(record.isTerminal(), "终态任务无法 resume 回队列");
    }
}