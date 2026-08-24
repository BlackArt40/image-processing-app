package com.sjs.image.task;

import com.sjs.image.common.TaskStatus;
import com.sjs.image.common.TaskType;
import com.sjs.image.dto.ProcessOptions;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 单个处理任务的运行时记录。
 * 使用原子变量保证线程安全，供状态轮询接口读取。
 */
public class TaskRecord {

    private final String id;
    private final TaskType type;
    /** 原始文件名 */
    private final String sourceName;
    /** 原始文件存储名（含目录） */
    private final String sourceStoreName;

    private final AtomicReference<TaskStatus> status = new AtomicReference<>(TaskStatus.QUEUED);
    private final AtomicInteger progress = new AtomicInteger(0);
    private final AtomicReference<String> stage = new AtomicReference<>("等待处理");
    /** 结果文件存储名 */
    private final AtomicReference<String> resultStoreName = new AtomicReference<>();
    /** 错误信息 */
    private final AtomicReference<String> error = new AtomicReference<>();
    /** 处理结果元信息（尺寸/算法等，供对比展示） */
    private final AtomicReference<String> sourceMeta = new AtomicReference<>();
    /** 处理参数（供暂停后继续使用） */
    private final AtomicReference<ProcessOptions> options = new AtomicReference<>();
    /** 是否请求暂停 */
    private final AtomicBoolean paused = new AtomicBoolean(false);
    /** 终态完成时间（成功/失败），用于终态任务的内存回收 */
    private final AtomicReference<Long> finishedAt = new AtomicReference<>();
    /** 运行租约：同一时间仅允许一个 worker 处理本任务，防止暂停/继续竞态下并发双执行 */
    private final AtomicBoolean running = new AtomicBoolean(false);

    public TaskRecord(String id, TaskType type, String sourceName, String sourceStoreName) {
        this.id = id;
        this.type = type;
        this.sourceName = sourceName;
        this.sourceStoreName = sourceStoreName;
    }

    public void update(int progress, String stage) {
        TaskStatus s = this.status.get();
        if (s == TaskStatus.SUCCESS || s == TaskStatus.FAILED || s == TaskStatus.PAUSED) {
            return;
        }
        this.status.set(TaskStatus.PROCESSING);
        this.progress.set(progress);
        this.stage.set(stage);
    }

    public void succeed(String resultStoreName, String sourceMeta) {
        this.progress.set(100);
        this.status.set(TaskStatus.SUCCESS);
        this.stage.set("处理完成");
        this.resultStoreName.set(resultStoreName);
        this.finishedAt.set(System.currentTimeMillis());
        if (sourceMeta != null) {
            this.sourceMeta.set(sourceMeta);
        }
    }

    public void fail(String error) {
        this.status.set(TaskStatus.FAILED);
        this.stage.set("处理失败");
        this.error.set(error);
        this.finishedAt.set(System.currentTimeMillis());
    }

    /** 终态完成时间；未完成返回 null。 */
    public Long getFinishedAt() { return finishedAt.get(); }

    /** 是否为不可回到队列的终态。 */
    public boolean isTerminal() {
        TaskStatus s = status.get();
        return s == TaskStatus.SUCCESS || s == TaskStatus.FAILED;
    }

    /** 尝试获取运行租约；成功返回 true（同一时刻仅一个 worker 可拿到）。 */
    public boolean tryBeginRun() {
        return running.compareAndSet(false, true);
    }

    /** 释放运行租约。 */
    public void endRun() {
        running.set(false);
    }

    /** 请求暂停：置暂停标志。 */
    public void requestPause() {
        this.paused.set(true);
    }

    /** 标记为已暂停（由调度层调用）。 */
    public void markPaused() {
        this.status.set(TaskStatus.PAUSED);
        this.stage.set("已暂停");
    }

    /** 当前是否请求暂停。 */
    public boolean isPauseRequested() {
        return this.paused.get();
    }

    /** 继续：清除暂停标志并回到等待处理。终态（成功/失败）不可再回到队列。 */
    public void resume() {
        if (isTerminal()) {
            return; // 终态任务不可被恢复
        }
        this.paused.set(false);
        this.status.set(TaskStatus.QUEUED);
        this.stage.set("等待处理");
    }

    public void setOptions(ProcessOptions options) { this.options.set(options); }
    public ProcessOptions getOptions() { return this.options.get(); }

    // ---- getters ----
    public String getId() { return id; }
    public TaskType getType() { return type; }
    public String getSourceName() { return sourceName; }
    public String getSourceStoreName() { return sourceStoreName; }
    public TaskStatus getStatus() { return status.get(); }
    public int getProgress() { return progress.get(); }
    public String getStage() { return stage.get(); }
    public String getResultStoreName() { return resultStoreName.get(); }
    public String getError() { return error.get(); }
    public String getSourceMeta() { return sourceMeta.get(); }
}