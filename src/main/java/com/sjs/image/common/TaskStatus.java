package com.sjs.image.common;

/**
 * 任务状态。
 */
public enum TaskStatus {
    /** 等待处理 */
    QUEUED,
    /** 处理中 */
    PROCESSING,
    /** 处理完成 */
    SUCCESS,
    /** 处理失败 */
    FAILED
}