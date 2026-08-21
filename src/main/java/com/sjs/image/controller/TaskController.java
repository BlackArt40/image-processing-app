package com.sjs.image.controller;

import com.sjs.image.common.ProcessingException;
import com.sjs.image.dto.TaskStatusResponse;
import com.sjs.image.service.TaskManagerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 任务状态接口：前端轮询处理进度。
 */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskManagerService taskManager;

    public TaskController(TaskManagerService taskManager) {
        this.taskManager = taskManager;
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskStatusResponse> status(@PathVariable("id") String id) {
        return ResponseEntity.ok(TaskStatusResponse.from(taskManager.get(id)));
    }

    @PostMapping("/{id}/pause")
    public ResponseEntity<Map<String, Object>> pause(@PathVariable("id") String id) {
        taskManager.pause(id);
        return ResponseEntity.ok(Map.of("ok", true, "status", taskManager.get(id).getStatus().name()));
    }

    @PostMapping("/{id}/resume")
    public ResponseEntity<Map<String, Object>> resume(@PathVariable("id") String id) {
        taskManager.resume(id);
        return ResponseEntity.ok(Map.of("ok", true, "status", taskManager.get(id).getStatus().name()));
    }
}