package com.sjs.image.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 图片任务线程池配置，参数可通过 application.yml 的 app.executor.* 调整。
 * - 有界线程池 + 有界队列，避免无节制创建线程导致资源耗尽；
 * - 队列满时采用 AbortPolicy 直接拒绝（由 TaskManagerService 转成「系统繁忙」业务异常），
 *   绝不回退到调用线程执行 —— 保证 /api/upload 只做「入队」随即返回，不被重处理阻塞拉长尾延迟。
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "imageTaskExecutor")
    public Executor imageTaskExecutor(@Autowired(required = false) ExecutorProperties props) {
        int cores = Runtime.getRuntime().availableProcessors();

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize(props, cores));
        executor.setMaxPoolSize(maxSize(props, cores));
        executor.setQueueCapacity(queueCapacity(props));
        executor.setThreadNamePrefix("image-task-");
        // 池满直接拒绝，交由调用方转成明确的业务错误，避免阻塞上传线程执行任务
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    private int coreSize(ExecutorProperties props, int cores) {
        if (props != null && props.hasCore()) {
            return Math.max(1, parse(props.getCorePoolSize()));
        }
        return Math.max(2, cores);
    }

    private int maxSize(ExecutorProperties props, int cores) {
        if (props != null && props.hasMax()) {
            return Math.max(1, parse(props.getMaxPoolSize()));
        }
        return Math.max(2, cores) + 1;
    }

    private int queueCapacity(ExecutorProperties props) {
        Integer v = props != null ? props.getQueueCapacity() : null;
        return (v != null && v > 0) ? v : 48;
    }

    private int parse(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}