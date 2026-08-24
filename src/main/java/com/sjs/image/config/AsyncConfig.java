package com.sjs.image.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务池配置。
 * - 有界线程池 + 有界队列，避免无节制创建线程导致资源耗尽；
 * - 队列满时采用 AbortPolicy 直接拒绝（由 TaskManagerService 转成「系统繁忙」业务异常），
 *   绝不回退到调用线程执行 —— 保证 /api/upload 只做「入队」随即返回，不被重处理阻塞拉长尾延迟。
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "imageTaskExecutor")
    public Executor imageTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // CPU 密集的图片处理：线程数贴近可用核数，避免线程切换开销
        int cores = Runtime.getRuntime().availableProcessors();
        executor.setCorePoolSize(Math.max(2, cores));
        executor.setMaxPoolSize(Math.max(2, cores) + 1);
        executor.setQueueCapacity(48);
        executor.setThreadNamePrefix("image-task-");
        // 池满直接拒绝，交由调用方转成明确的业务错误，避免阻塞上传线程执行任务
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}