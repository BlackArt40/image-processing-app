package com.sjs.image.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ProcessOptions 参数钳制逻辑单元测试。
 * 覆盖 scale/sharpen/quality 的默认值与上下界边界。
 */
class ProcessOptionsTest {

    @Test
    void scale默认值为2() {
        assertEquals(2, new ProcessOptions().getScale());
    }

    @Test
    void scale被钳制到1到4之间() {
        ProcessOptions opts = new ProcessOptions();
        opts.setScale(0);
        assertEquals(1, opts.getScale(), "小于1应钳到1");

        opts.setScale(99);
        assertEquals(4, opts.getScale(), "大于4应钳到4");

        opts.setScale(3);
        assertEquals(3, opts.getScale(), "合法值保持不变");
    }

    @Test
    void sharpen默认值为50() {
        assertEquals(50, new ProcessOptions().getSharpen());
    }

    @Test
    void sharpen被钳制到0到100之间() {
        ProcessOptions opts = new ProcessOptions();
        opts.setSharpen(-1);
        assertEquals(0, opts.getSharpen());

        opts.setSharpen(200);
        assertEquals(100, opts.getSharpen());
    }

    @Test
    void quality默认值为90() {
        assertEquals(90, new ProcessOptions().getQuality());
    }
}