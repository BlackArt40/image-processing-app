package com.sjs.image.common;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 将 classpath 资源落盘到临时文件的共享工具。
 * CascadeClassifier / DnnDnnSuperRes 等原生加载器通常只接受文件路径，故统一在此实现「资源 → 临时文件」。
 */
public final class ClasspathResourceUtils {

    private ClasspathResourceUtils() {}

    /** 将 classpath 资源复制到临时文件（进程退出时删除），返回该临时文件路径。 */
    public static Path toTempFile(String resourcePath, String prefix, String suffix) throws IOException {
        Path tmp = Files.createTempFile(prefix, suffix);
        try (InputStream in = ClasspathResourceUtils.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("资源缺失: " + resourcePath);
            }
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
        }
        tmp.toFile().deleteOnExit();
        return tmp;
    }
}