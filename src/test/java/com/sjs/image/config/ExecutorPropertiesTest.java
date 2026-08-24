package com.sjs.image.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ExecutorProperties 的配置绑定与回退逻辑测试。
 * 验证 application.yml 中 core/max 留空时自动回退推导，以及显式值可覆盖。
 */
class ExecutorPropertiesTest {

    private ExecutorProperties bind(Map<String, String> map) {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(map);
        return new Binder(source).bind("app.executor", ExecutorProperties.class).orElseGet(ExecutorProperties::new);
    }

    @Test
    void 未配置线程数时回退自动推导() {
        ExecutorProperties props = bind(Map.of("app.executor.queue-capacity", "48"));
        assertFalse(props.hasCore(), "未配置 core 时应回退");
        assertFalse(props.hasMax(), "未配置 max 时应回退");
    }

    @Test
    void 空白字符串视为未配置() {
        ExecutorProperties props = bind(Map.of("app.executor.core-pool-size", ""));
        assertFalse(props.hasCore(), "空字符串应视为未配置");
        assertFalse(props.hasMax(), "未配置 max 时应回退");
    }

    @Test
    void 显式配置线程数被绑定() {
        ExecutorProperties props = bind(Map.of(
                "app.executor.core-pool-size", "4",
                "app.executor.max-pool-size", "8",
                "app.executor.queue-capacity", "100"));
        assertTrue(props.hasCore());
        assertTrue(props.hasMax());
        assertEquals("4", props.getCorePoolSize());
        assertEquals("8", props.getMaxPoolSize());
        assertEquals(100, props.getQueueCapacity());
    }

    @Test
    void 未配置队列容量时为null由AsyncConfig兜底() {
        ExecutorProperties props = bind(Map.of());
        assertEquals(null, props.getQueueCapacity(), "yaml 未给时绑定为 null，由 AsyncConfig 兜底 48");
    }
}