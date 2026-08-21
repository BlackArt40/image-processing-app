package com.sjs.image.processor;

/**
 * 处理进度回调。
 */
public interface Progress {
    void onProgress(int percent, String stage);
}