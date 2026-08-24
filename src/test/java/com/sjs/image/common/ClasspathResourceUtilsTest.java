package com.sjs.image.common;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ClasspathResourceUtils（classpath 资源 → 临时文件）单元测试。
 * 验证内置级联模型可落盘、缺失资源抛出可诊断异常。
 */
class ClasspathResourceUtilsTest {

    @Test
    void 存在的资源被复制到临时文件() throws IOException {
        Path tmp = ClasspathResourceUtils.toTempFile(
                "opencv/haarcascade_frontalface_default.xml", "cascade-test", ".xml");
        try {
            assertTrue(Files.exists(tmp), "临时文件应存在");
            assertTrue(Files.size(tmp) > 0, "临时文件不应为空");
            assertTrue(tmp.toString().endsWith(".xml"), "扩展名应保留");
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void 缺失资源抛出IOException() {
        assertThrows(IOException.class,
                () -> ClasspathResourceUtils.toTempFile("opencv/not-exist.xml", "missing", ".xml"));
    }
}