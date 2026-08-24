package com.sjs.image.processor;

import com.sjs.image.common.ProcessingException;
import com.sjs.image.dto.ProcessOptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * FilterProcessor 无原生依赖的校验逻辑测试。
 * 「缺省滤镜」在读取图片（触发 OpenCV 原生库）之前即被校验，因此可 headless 覆盖。
 */
class FilterProcessorTest {

    private static final String MISSING_FILTER = "请选择一种滤镜";

    /** storage 传 null 即可——缺省滤镜分支在用到 storage 之前抛出。 */
    private final FilterProcessor processor = new FilterProcessor(null);

    @Test
    void 缺省滤镜抛出提示() {
        ProcessOptions opts = new ProcessOptions();
        opts.setFilter(null);
        ProcessingException ex = assertThrows(ProcessingException.class,
                () -> processor.process(Path.of("x.jpg"), "x.jpg", opts, null));
        assertEquals(MISSING_FILTER, ex.getMessage());
    }

    @Test
    void 空白滤镜抛出提示() {
        ProcessOptions opts = new ProcessOptions();
        opts.setFilter("  ");
        ProcessingException ex = assertThrows(ProcessingException.class,
                () -> processor.process(Path.of("x.jpg"), "x.jpg", opts, null));
        assertEquals(MISSING_FILTER, ex.getMessage());
    }

    @Test
    void 提供滤镜时不会命中缺滤镜分支() {
        ProcessOptions opts = new ProcessOptions();
        opts.setFilter("sepia");
        // x.jpg 不存在：校验通过后会因读图失败抛出，但不应是「缺滤镜」的提示
        ProcessingException ex = assertThrows(ProcessingException.class,
                () -> processor.process(Path.of("x.jpg"), "x.jpg", opts, (pct, stage) -> {}));
        assertNotEquals(MISSING_FILTER, ex.getMessage());
    }
}