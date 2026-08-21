package com.sjs.image.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sjs.image.common.ProcessingException;
import com.sjs.image.common.ProcessingPausedException;
import com.sjs.image.common.TaskStatus;
import com.sjs.image.common.TaskType;
import com.sjs.image.dto.ProcessOptions;
import com.sjs.image.processor.Progress;
import com.sjs.image.dto.TaskStatusResponse;
import com.sjs.image.processor.ImageProcessor;
import com.sjs.image.task.TaskRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 任务调度服务。
 * - 有界线程池 + 有限队列实现并发控制（满时由提交线程执行，平滑限流）。
 * - 提交后立即返回 taskId，前端轮询状态接口获取进度。
 */
@Service
public class TaskManagerService {

    private static final Logger log = LoggerFactory.getLogger(TaskManagerService.class);

    private final ImageStorageService storage;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Executor executor;
    private final Map<TaskType, ImageProcessor> processors;
    private final Map<String, TaskRecord> tasks = new ConcurrentHashMap<>();

    public TaskManagerService(ImageStorageService storage,
                              @Qualifier("imageTaskExecutor") Executor executor,
                              List<ImageProcessor> processorList) {
        this.storage = storage;
        this.executor = executor;
        this.processors = processorList.stream()
                .collect(Collectors.toMap(ImageProcessor::type, Function.identity()));
        storage.init();
    }

    /**
     * 提交一个上传任务。
     *
     * @return 任务记录（用于返回 taskId）
     */
    public TaskRecord submit(MultipartFile file, String optionsJson) {
        ProcessOptions opts;
        try {
            opts = optionsJson == null || optionsJson.isBlank()
                    ? new ProcessOptions()
                    : objectMapper.readValue(optionsJson, ProcessOptions.class);
        } catch (Exception e) {
            throw new ProcessingException("处理参数格式不正确");
        }
        if (opts.getType() == null) {
            throw new ProcessingException("缺少任务类型 type");
        }
        ImageProcessor processor = processors.get(opts.getType());
        if (processor == null) {
            throw new ProcessingException("不支持的任务类型: " + opts.getType());
        }

        String storeName;
        String original;
        try {
            storeName = storage.storeUpload(file);
            original = storage.cleanName(file.getOriginalFilename() == null ? "image.jpg" : file.getOriginalFilename());
        } catch (IOException e) {
            throw new ProcessingException("保存上传文件失败:" + e.getMessage());
        }

        String id = UUID.randomUUID().toString().replace("-", "");
        TaskRecord record = new TaskRecord(id, opts.getType(), original, storeName);
        record.setOptions(opts);
        tasks.put(id, record);

        executor.execute(() -> run(record));
        return record;
    }

    private void run(TaskRecord record) {
        ImageProcessor processor = processors.get(record.getType());
        ProcessOptions opts = record.getOptions();
        try {
            record.update(1, "开始处理");
            var outcome = processor.process(
                    storage.sourcePath(record.getSourceStoreName()),
                    record.getSourceName(),
                    opts,
                    reportProgress(record));
            record.succeed(outcome.storeName(), outcome.meta());
        } catch (ProcessingPausedException pe) {
            record.markPaused();
        } catch (Throwable t) {
            log.error("任务处理失败: {}", record.getId(), t);
            record.fail("处理失败: " + safeMessage(t));
        }
    }

    /** 进度回调：若任务被请求暂停则抛出中断信号。 */
    private Progress reportProgress(TaskRecord record) {
        return (pct, stage) -> {
            if (record.isPauseRequested()) {
                throw new ProcessingPausedException();
            }
            record.update(pct, stage);
        };
    }

    /**
     * 暂停任务：在下一阶段回调时中断；排队中直接置暂停。
     */
    public void pause(String id) {
        TaskRecord record = tasks.get(id);
        if (record == null) {
            throw new ProcessingException("任务不存在: " + id);
        }
        TaskStatus s = record.getStatus();
        if (s == TaskStatus.SUCCESS || s == TaskStatus.FAILED) {
            throw new ProcessingException("任务已结束，无法暂停");
        }
        record.requestPause();
        // 若尚未被 worker 拾取（仍在排队），直接置暂停状态即可
        if (s == TaskStatus.QUEUED) {
            record.markPaused();
        }
        // 正在处理时，由 reportProgress 在下一阶段抛出中断
    }

    /**
     * 继续任务：重置为等待处理并重新派发。
     */
    public void resume(String id) {
        TaskRecord record = tasks.get(id);
        if (record == null) {
            throw new ProcessingException("任务不存在: " + id);
        }
        if (record.getStatus() != TaskStatus.PAUSED) {
            throw new ProcessingException("任务当前不可继续");
        }
        record.resume();
        executor.execute(() -> run(record));
    }

    private String safeMessage(Throwable t) {
        String m = t.getMessage();
        return (m == null || m.isBlank()) ? t.getClass().getSimpleName() : m;
    }

    public TaskRecord get(String id) {
        TaskRecord record = tasks.get(id);
        if (record == null) {
            throw new ProcessingException("任务不存在: " + id);
        }
        return record;
    }
}