package com.sjs.image.task;

import com.sjs.image.common.TaskStatus;
import com.sjs.image.common.TaskType;

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
    /** 是否已处理原图尺寸变化（供对比展示） */
    private final AtomicReference<String> sourceMeta = new AtomicReference<>();

    public TaskRecord(String id, TaskType type, String sourceName, String sourceStoreName) {
        this.id = id;
        this.type = type;
        this.sourceName = sourceName;
        this.sourceStoreName = sourceStoreName;
    }

    public void update(int progress, String stage) {
        if (this.status.get() == TaskStatus.SUCCESS || this.status.get() == TaskStatus.FAILED) {
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
        if (sourceMeta != null) {
            this.sourceMeta.set(sourceMeta);
        }
    }

    public void fail(String error) {
        this.status.set(TaskStatus.FAILED);
        this.stage.set("处理失败");
        this.error.set(error);
    }

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