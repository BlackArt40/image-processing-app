package com.sjs.image.service;

import com.sjs.image.common.ProcessingException;
import com.sjs.image.config.StorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

/**
 * 图片文件存储服务。
 * 统一管理原始 / 处理后的文件落盘，避免路径穿越，节省内存（流式写入）。
 */
@Service
public class ImageStorageService {

    private static final Logger log = LoggerFactory.getLogger(ImageStorageService.class);

    private final StorageProperties props;

    public ImageStorageService(StorageProperties props) {
        this.props = props;
    }

    /** 确保各目录存在 */
    public void init() {
        try {
            Files.createDirectories(Paths.get(props.uploadPath()));
            Files.createDirectories(Paths.get(props.processedPath()));
            Files.createDirectories(Paths.get(props.tmpPath()));
        } catch (IOException e) {
            throw new ProcessingException("初始化存储目录失败", e);
        }
    }

    /** 保存上传文件，返回相对存储名（使用 TaskType 无关接口）。 */
    public String storeUpload(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new ProcessingException("请选择要上传的图片文件");
        }
        String original = StringUtils.cleanPath(file.getOriginalFilename() == null ? "image.jpg" : file.getOriginalFilename());
        String ext = extensionOf(original);
        String storeName = UUID.randomUUID().toString().replace("-", "") + ext;
        Path target = resolveUnsafe(props.uploadPath()).resolve(storeName);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return storeName;
    }

    /** 原始文件名（去路径） */
    public String cleanName(String name) {
        return StringUtils.cleanPath(name);
    }

    /** 依据原始文件名 + 新扩展名生成结果存储名 */
    public String resultStoreName(String originalName, String newExt) {
        String base = UUID.randomUUID().toString().replace("-", "");
        return base + (newExt.startsWith(".") ? newExt : "." + newExt);
    }

    /** 解析存储名对应的绝对路径（含上传目录） */
    public Path sourcePath(String storeName) {
        return resolveUnsafe(props.uploadPath()).resolve(storeName).normalize();
    }

    /** 解析存储名对应的绝对路径（含结果目录） */
    public Path resultPath(String storeName) {
        return resolveUnsafe(props.processedPath()).resolve(storeName).normalize();
    }

    public String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return ".jpg";
        }
        return name.substring(dot).toLowerCase(Locale.ROOT);
    }

    public String baseName(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    /** 按大小限制（字节）读取 source 内容；返回缓冲字节 */
    public byte[] readBytes(Path path, int maxBytes) throws IOException {
        long size = Files.size(path);
        if (size > maxBytes) {
            throw new ProcessingException("图片过大，超出内存限制");
        }
        return Files.readAllBytes(path);
    }

    private Path resolveUnsafe(String dir) {
        return Paths.get(dir);
    }
}