package com.sjs.image.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sjs.image.common.ProcessingException;
import com.sjs.image.common.TaskStatus;
import com.sjs.image.common.TaskType;
import com.sjs.image.config.StorageProperties;
import com.sjs.image.dto.ProcessOptions;
import com.sjs.image.processor.ImageProcessor;
import com.sjs.image.processor.Outcome;
import com.sjs.image.processor.Progress;
import com.sjs.image.task.TaskRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 任务调度核心逻辑测试（基于临时目录的真实存储）。
 * 覆盖提交参数校验、任务类型校验、暂停/继续边界（终态不可恢复）等关键风险。
 */
class TaskManagerServiceTest {

    @TempDir
    Path tmp;

    private TaskManagerService service;

    /** 同步执行器：任务提交后在当前线程立即执行，便于确定性断言终态。 */
    private final java.util.concurrent.Executor direct = Runnable::run;

    @BeforeEach
    void setUp() {
        StorageProperties props = new StorageProperties();
        props.setRoot(tmp.toString());
        ImageStorageService storage = new ImageStorageService(props);
        storage.init();

        ImageProcessor fake = new ImageProcessor() {
            @Override
            public TaskType type() { return TaskType.RETOUCH; }
            @Override
            public Outcome process(Path source, String sourceName, ProcessOptions opts, Progress progress) {
                progress.onProgress(50, "处理中");
                return new Outcome("out.png", "meta");
            }
        };

        service = new TaskManagerService(storage, new ObjectMapper(), props, direct, List.of(fake));
    }

    private MultipartFile pic() {
        return new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1, 2, 3});
    }

    @Test
    void 拒绝缺失任务类型() {
        assertThrows(ProcessingException.class, () -> service.submit(pic(), "{}"));
    }

    @Test
    void 拒绝非法的参数JSON() {
        assertThrows(ProcessingException.class, () -> service.submit(pic(), "not-json"));
    }

    @Test
    void 拒绝不支持的任务类型() {
        assertThrows(ProcessingException.class,
                () -> service.submit(pic(), "{\"type\":\"NOPE\"}"));
    }

    @Test
    void 提交成功并完成() {
        TaskRecord record = service.submit(pic(), "{\"type\":\"RETOUCH\"}");
        assertEquals(TaskStatus.SUCCESS, record.getStatus());
        assertEquals("out.png", record.getResultStoreName());
    }

    @Test
    void 暂停不存在的任务报错() {
        assertThrows(ProcessingException.class, () -> service.pause("nope"));
    }

    @Test
    void 结束任务不可暂停() {
        TaskRecord record = service.submit(pic(), "{\"type\":\"RETOUCH\"}");
        assertEquals(TaskStatus.SUCCESS, record.getStatus());
        assertThrows(ProcessingException.class, () -> service.pause(record.getId()));
    }

    @Test
    void 结束任务不可继续() {
        TaskRecord record = service.submit(pic(), "{\"type\":\"RETOUCH\"}");
        assertThrows(ProcessingException.class, () -> service.resume(record.getId()));
    }
}