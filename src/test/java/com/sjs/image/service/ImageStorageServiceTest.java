package com.sjs.image.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 图片存储服务（流式落盘 + 路径解析）的单元测试，基于临时目录运行。
 */
class ImageStorageServiceTest {

    @TempDir
    Path tmp;

    private ImageStorageService storage;
    private Path root;

    @BeforeEach
    void setUp() {
        root = tmp;
        com.sjs.image.config.StorageProperties props = new com.sjs.image.config.StorageProperties();
        props.setRoot(root.toString());
        storage = new ImageStorageService(props);
        storage.init();
    }

    private MultipartFile pic(String name, byte[] body) {
        return new org.springframework.mock.web.MockMultipartFile("file", name, "image/jpeg", body);
    }

    @Test
    void 上传与源路径指向同一文件() throws Exception {
        String storeName = storage.storeUpload(pic("a.jpg", new byte[]{1, 2, 3}));
        // 存储名是 32 位十六进制 + .jpg
        assertEquals(36, storeName.length());
        assertEquals(".jpg", storeName.substring(32));
        assertEquals(true, java.nio.file.Files.exists(storage.sourcePath(storeName)));
    }

    @Test
    void 结果名使用新扩展名() {
        String name = storage.resultStoreName("a.jpg", ".png");
        assertEquals(".png", name.substring(name.length() - 4));
    }

    @Test
    void 无扩展名回退jpg() {
        assertEquals(".jpg", storage.extensionOf("pic"));
        assertEquals(".jpg", storage.extensionOf("pic."));
    }

    @Test
    void 结果路径归属结果目录() {
        String name = "a.jpg";
        assertEquals(root.resolve(storage.processedPath()).resolve(name), storage.resultPath(name));
    }
}