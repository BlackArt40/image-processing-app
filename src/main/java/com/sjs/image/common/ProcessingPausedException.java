package com.sjs.image.common;

/**
 * 任务被暂停：处理器在某阶段回调时检测到暂停请求而中断当前处理。
 * 由调度层捕获，将任务置为 PAUSED（而非失败）。
 */
public class ProcessingPausedException extends RuntimeException {
    public ProcessingPausedException() {
        super("任务已暂停");
    }
}