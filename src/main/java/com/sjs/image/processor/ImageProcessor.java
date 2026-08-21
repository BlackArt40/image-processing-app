package com.sjs.image.processor;

import com.sjs.image.common.TaskType;
import com.sjs.image.dto.ProcessOptions;

import java.nio.file.Path;

/**
 * 图片处理器接口。
 */
public interface ImageProcessor {

    TaskType type();

    /**
     * 处理图片，结果写入结果目录，返回 Outcome(storeName, meta)。
     *
     * @param source     原图绝对路径
     * @param sourceName 原始文件名
     * @param opts       处理参数
     * @param progress   进度回调
     */
    Outcome process(Path source, String sourceName, ProcessOptions opts, Progress progress) throws Exception;
}