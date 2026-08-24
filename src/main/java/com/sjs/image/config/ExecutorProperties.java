package com.sjs.image.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * 图片任务线程池可配置项。
 * 暴露核心线程数 / 最大线程数 / 队列容量到 application.yml (app.executor.*)。
 * 值为 null 或空白时回退到「按可用核数自动推导」。
 */
@ConfigurationProperties(prefix = "app.executor")
public class ExecutorProperties {

    /** 核心线程数；空白则按可用核数自动推导 */
    private String corePoolSize;

    /** 最大线程数；空白则 = 核数 + 1 */
    private String maxPoolSize;

    /** 队列容量；空白则用默认 48 */
    private Integer queueCapacity;

    public String getCorePoolSize() { return corePoolSize; }
    public void setCorePoolSize(String corePoolSize) { this.corePoolSize = corePoolSize; }
    public String getMaxPoolSize() { return maxPoolSize; }
    public void setMaxPoolSize(String maxPoolSize) { this.maxPoolSize = maxPoolSize; }
    public Integer getQueueCapacity() { return queueCapacity; }
    public void setQueueCapacity(Integer queueCapacity) { this.queueCapacity = queueCapacity; }

    /** 已显式配置核心线程数？ */
    public boolean hasCore() {
        return StringUtils.hasText(corePoolSize);
    }

    /** 已显式配置最大线程数？ */
    public boolean hasMax() {
        return StringUtils.hasText(maxPoolSize);
    }
}