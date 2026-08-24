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

    @Test
    void 去噪与对比度默认值为50() {
        ProcessOptions opts = new ProcessOptions();
        assertEquals(50, opts.getDenoise());
        assertEquals(50, opts.getClarity());
        assertEquals(50, opts.getSharpen());
    }

    @Test
    void 去噪与对比度被钳制到0到100之间() {
        ProcessOptions opts = new ProcessOptions();
        opts.setDenoise(-3);
        assertEquals(0, opts.getDenoise());
        opts.setDenoise(250);
        assertEquals(100, opts.getDenoise());

        opts.setClarity(-3);
        assertEquals(0, opts.getClarity());
        opts.setClarity(250);
        assertEquals(100, opts.getClarity());
    }

    @Test
    void 滤镜强度默认值为100且被钳制() {
        ProcessOptions opts = new ProcessOptions();
        assertEquals(100, opts.getIntensity());
        opts.setIntensity(-1);
        assertEquals(0, opts.getIntensity());
        opts.setIntensity(500);
        assertEquals(100, opts.getIntensity());
    }

    @Test
    void 滤镜预设默认空() {
        assertEquals(null, new ProcessOptions().getFilter());
    }

    @Test
    void 去雾与低光默认值为50且被钳制() {
        ProcessOptions opts = new ProcessOptions();
        assertEquals(50, opts.getDehaze());
        assertEquals(50, opts.getLowLight());

        opts.setDehaze(-1);
        assertEquals(0, opts.getDehaze());
        opts.setLowLight(200);
        assertEquals(100, opts.getLowLight());
    }
}