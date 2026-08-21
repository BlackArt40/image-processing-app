package com.sjs.image.service;

import com.sjs.image.config.StorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.stream.Stream;

/**
 * 定时清理 ./data 下的过期文件。
 * - uploads / processed：超过 app.storage.ttl-hours 保留时长的文件被删除（默认 24h）
 * - tmp：临时文件一律按较短 TTL 清理
 * 通过 app.cleanup.enabled 可整体关闭，cron 可自定义执行时间。
 */
@Component
@ConditionalOnProperty(name = "app.cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class FileCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(FileCleanupScheduler.class);

    private final StorageProperties props;

    public FileCleanupScheduler(StorageProperties props) {
        this.props = props;
    }

    @Scheduled(cron = "${app.cleanup.cron:0 0 4 * * *}")
    public void cleanup() {
        long ttl = Duration.ofHours(Math.max(1, props.getTtlHours())).toMillis();
        long now = System.currentTimeMillis();
        int removed = 0;

        removed += cleanupDir(Path.of(props.uploadPath()), now - ttl);
        removed += cleanupDir(Path.of(props.processedPath()), now - ttl);
        // 临时目录保留时间更短（半小时）
        removed += cleanupDir(Path.of(props.tmpPath()), now - Duration.ofMinutes(30).toMillis());

        log.info("过期文件清理完成，共删除 {} 个文件", removed);
    }

    private int cleanupDir(Path dir, long cutoff) {
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        int removed = 0;
        try (Stream<Path> stream = Files.walk(dir)) {
            for (Path p : (Iterable<Path>) stream.filter(Files::isRegularFile)::iterator) {
                try {
                    FileTime modified = Files.getLastModifiedTime(p);
                    if (modified.toMillis() < cutoff) {
                        Files.deleteIfExists(p);
                        removed++;
                    }
                } catch (IOException e) {
                    log.warn("清理文件失败: {}", p, e);
                }
            }
        } catch (IOException e) {
            log.warn("遍历清理目录失败: {}", dir, e);
        }
        return removed;
    }
}