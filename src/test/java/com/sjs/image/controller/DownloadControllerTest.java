package com.sjs.image.controller;

import com.sjs.image.common.ProcessingException;
import com.sjs.image.config.StorageProperties;
import com.sjs.image.service.ImageStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 下载/预览接口的路径穿越防御测试。
 * 存储名必须是「32 位十六进制 + 可选扩展名」，任何 . / 路径穿越都被拒绝。
 */
class DownloadControllerTest {

    @TempDir
    Path tmp;

    private DownloadController controller;

    @BeforeEach
    void setUp() {
        StorageProperties props = new StorageProperties();
        props.setRoot(tmp.toString());
        // 校验在触碰存储前即命中，用真实存储（指向临时目录）即可
        controller = new DownloadController(new ImageStorageService(props));
    }

    @Test
    void 拒绝父目录穿越() {
        assertThrows(ProcessingException.class,
                () -> controller.preview("..", "processed"));
    }

    @Test
    void 拒绝更深层穿越() {
        assertThrows(ProcessingException.class,
                () -> controller.preview("../../etc/passwd", "processed"));
    }

    @Test
    void 拒绝非UUID存储名() {
        assertThrows(ProcessingException.class,
                () -> controller.preview("not-a-uuid.jpg", "processed"));
    }

    @Test
    void 拒绝错误目录参数() {
        assertThrows(ProcessingException.class,
                () -> controller.preview("a".repeat(32), "../etc"));
    }

    @Test
    void 拒绝下载接口的穿越() {
        assertThrows(ProcessingException.class,
                () -> controller.download("..", "processed", "friendly.txt"));
    }
}