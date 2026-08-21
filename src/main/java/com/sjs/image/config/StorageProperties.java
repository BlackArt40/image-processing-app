package com.sjs.image.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 存储目录配置。
 */
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    /** 根目录 */
    private String root = "./data";

    /** 原始图片目录 */
    private String uploadDir = "uploads";

    /** 处理后结果目录 */
    private String processedDir = "processed";

    /** 临时目录（可选） */
    private String tmpDir = "tmp";

    /** 上传/结果文件保留时长（小时），超过则被定时清理任务删除 */
    private long ttlHours = 24;

    public String getRoot() { return root; }
    public void setRoot(String root) { this.root = root; }

    public String getUploadDir() { return uploadDir; }
    public void setUploadDir(String uploadDir) { this.uploadDir = uploadDir; }

    public String getProcessedDir() { return processedDir; }
    public void setProcessedDir(String processedDir) { this.processedDir = processedDir; }

    public String getTmpDir() { return tmpDir; }
    public void setTmpDir(String tmpDir) { this.tmpDir = tmpDir; }

    public long getTtlHours() { return ttlHours; }
    public void setTtlHours(long ttlHours) { this.ttlHours = ttlHours; }

    /** 原始图片绝对目录 */
    public String uploadPath() { return root + "/" + uploadDir; }
    /** 结果图片绝对目录 */
    public String processedPath() { return root + "/" + processedDir; }
    /** 临时绝对目录 */
    public String tmpPath() { return root + "/" + tmpDir; }
}