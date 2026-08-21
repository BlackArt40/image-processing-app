package com.sjs.image.dto;

/**
 * 任务状态响应，供前端轮询。
 */
public record TaskStatusResponse(
        String taskId,
        String status,
        int progress,
        String stage,
        String error,
        String resultUrl,
        String sourceUrl,
        String sourceMeta
) {
    /**
     * 返回相对路径的预览 URL，兼容任意访问域名 / 代理。
     */
    public static TaskStatusResponse from(com.sjs.image.task.TaskRecord record) {
        String resultUrl = record.getResultStoreName() != null
                ? "/api/preview/" + record.getResultStoreName() + "?dir=processed"
                : null;
        String sourceUrl = "/api/preview/" + record.getSourceStoreName() + "?dir=uploads";
        return new TaskStatusResponse(
                record.getId(),
                record.getStatus().name(),
                record.getProgress(),
                record.getStage(),
                record.getError(),
                resultUrl,
                sourceUrl,
                record.getSourceMeta()
        );
    }
}