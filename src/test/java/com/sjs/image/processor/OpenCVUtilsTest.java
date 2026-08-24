package com.sjs.image.processor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OpenCVUtils 纯逻辑（编解码格式归一化）单元测试。
 * 不触发原生库加载，仅覆盖扩展名规范化规则。
 */
class OpenCVUtilsTest {

    @Test
    void jpeg归一化为jpg() {
        assertEquals("jpg", OpenCVUtils.normalizeFormat("jpeg"));
        assertEquals("jpg", OpenCVUtils.normalizeFormat("JPEG"));
    }

    @Test
    void 带点前缀被剥离() {
        assertEquals("png", OpenCVUtils.normalizeFormat(".png"));
        assertEquals("webp", OpenCVUtils.normalizeFormat(".WEBP"));
    }

    @Test
    void 常见格式保持不变() {
        assertEquals("jpg", OpenCVUtils.normalizeFormat("jpg"));
        assertEquals("png", OpenCVUtils.normalizeFormat("png"));
        assertEquals("webp", OpenCVUtils.normalizeFormat("webp"));
    }

    @Test
    void 空白被去除() {
        assertEquals("bmp", OpenCVUtils.normalizeFormat("  bmp "));
    }

    @Test
    void 常规尺寸通过校验() {
        assertTrue(OpenCVUtils.assertSafeImageSize(1920, 1080), "常规图应通过");
    }

    @Test
    void 单边超限被拒绝() {
        assertFalse(OpenCVUtils.assertSafeImageSize(20000, 100), "单边超限应拒绝");
        assertFalse(OpenCVUtils.assertSafeImageSize(100, 20000), "单边超限应拒绝");
    }

    @Test
    void 总像素超限被拒绝() {
        // 10000 × 10000 = 100MP > 50MP，虽各边在 12000 内，但总像素超限
        assertFalse(OpenCVUtils.assertSafeImageSize(10000, 10000), "极端宽高比但总像素超限应拒绝");
        // 宽高比极端的 10000 × 100（各边不超、总和 1MP）应通过
        assertTrue(OpenCVUtils.assertSafeImageSize(10000, 100), "极端但总像素在限内应通过");
    }

    @Test
    void 非法尺寸被拒绝() {
        assertFalse(OpenCVUtils.assertSafeImageSize(0, 100));
        assertFalse(OpenCVUtils.assertSafeImageSize(-5, 100));
    }
}