package com.sjs.image.dto;

/**
 * 上传成功响应。
 */
public record UploadResponse(String taskId, String type, String message) {
}