package com.sjs.image.common;

/**
 * 任务类型枚举，对应四个功能模块。
 */
public enum TaskType {
    /** 图片高清处理 */
    ENHANCE,
    /** AI 智能精修 */
    RETOUCH,
    /** 马赛克消除 */
    INPAINT,
    /** 图片格式转换 */
    CONVERT,
    /** 滤镜 / 风格化 */
    FILTER,
    /** 去雾 / 低光增强 */
    DEHAZE
}