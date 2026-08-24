package com.sjs.image.controller;

import com.sjs.image.common.ProcessingException;
import com.sjs.image.service.ImageStorageService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 图片预览与下载接口。
 * - preview：内联展示（在线预览）
 * - download：附件下载
 */
@RestController
@RequestMapping("/api")
public class DownloadController {

    private final ImageStorageService storage;

    public DownloadController(ImageStorageService storage) {
        this.storage = storage;
    }

    @GetMapping("/preview/{name}")
    public ResponseEntity<Resource> preview(@PathVariable("name") String storeName,
                                            @RequestParam(value = "dir", defaultValue = "processed") String dir) throws IOException {
        Path file = resolve(dir, storeName);
        String contentType = Files.probeContentType(file);
        if (contentType == null) {
            contentType = "application/octet-stream";
        }
        Resource resource = new UrlResource(file.toUri());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=0")
                .body(resource);
    }

    @GetMapping("/download")
    public ResponseEntity<Resource> download(@RequestParam("name") String storeName,
                                             @RequestParam(value = "dir", defaultValue = "processed") String dir,
                                             @RequestParam(value = "filename", required = false) String friendlyName) throws IOException {
        Path file = resolve(dir, storeName);
        Resource resource = new UrlResource(file.toUri());
        String name = (friendlyName == null || friendlyName.isBlank()) ? storeName : friendlyName;
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(Files.size(file)))
                .body(resource);
    }

    private Path resolve(String dir, String storeName) {
        if (dir == null || (!dir.equals("uploads") && !dir.equals("processed"))) {
            throw new ProcessingException("无效的目录参数");
        }
        // 存储名均为服务端生成的「32 位十六进制 ID + 扩展名」，不接受包含路径分隔符或父级引用的名称
        if (storeName == null || !isSafeStoreName(storeName)) {
            throw new ProcessingException("非法的文件名");
        }
        Path path = "uploads".equals(dir) ? storage.sourcePath(storeName) : storage.resultPath(storeName);
        // 双重保险：即便底层存储部解析出越界路径，也强制限制在 base 目录内
        Path base = ("uploads".equals(dir) ? Path.of(storage.uploadPath()) : Path.of(storage.processedPath())).normalize();
        if (!path.normalize().startsWith(base)) {
            throw new ProcessingException("非法的文件路径");
        }
        if (!Files.exists(path)) {
            throw new ProcessingException("文件不存在或已被清理");
        }
        return path;
    }

    /** 仅允许「32 位十六进制 + 可选的 . 扩展名」，彻底杜绝路径穿越。 */
    private boolean isSafeStoreName(String name) {
        if (name.startsWith(".") || name.contains("..") || name.contains("/") || name.contains("\\")) {
            return false;
        }
        int dot = name.lastIndexOf('.');
        String stem = dot < 0 ? name : name.substring(0, dot);
        if (!stem.matches("[0-9a-fA-F]{32}")) {
            return false;
        }
        String ext = dot < 0 ? "" : name.substring(dot + 1);
        return ext.isEmpty() || ext.matches("[A-Za-z0-9]{1,8}");
    }
}