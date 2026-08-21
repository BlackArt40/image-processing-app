package com.sjs.image.controller;

import com.sjs.image.common.ProcessingException;
import com.sjs.image.dto.ProcessOptions;
import com.sjs.image.dto.UploadResponse;
import com.sjs.image.service.TaskManagerService;
import com.sjs.image.task.TaskRecord;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 上传接口：接收图片 + 处理参数，派发异步任务。
 */
@RestController
@RequestMapping("/api")
public class UploadController {

    private final TaskManagerService taskManager;

    public UploadController(TaskManagerService taskManager) {
        this.taskManager = taskManager;
    }

    @PostMapping("/upload")
    public ResponseEntity<UploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "options", required = false) String options) {

        validate(file);

        TaskRecord record = taskManager.submit(file, options);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new UploadResponse(record.getId(), record.getType().name(), "已加入处理队列"));
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ProcessingException("请选择要上传的图片文件");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        boolean image = name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")
                || name.endsWith(".webp") || name.endsWith(".bmp") || name.endsWith(".gif")
                || (file.getContentType() != null && file.getContentType().startsWith("image/"));
        if (!image) {
            throw new ProcessingException("请上传图片文件（JPG/PNG/WEBP/BMP/GIF）");
        }
        if (file.getSize() > 50 * 1024 * 1024) {
            throw new ProcessingException("文件过大，最大支持 50MB");
        }
    }
}