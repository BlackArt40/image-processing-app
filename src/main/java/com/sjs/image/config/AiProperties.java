package com.sjs.image.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * AI 能力配置（本地）。
 * - backend: local | classic（模型不可用时自动回退 classic）
 * - super-res-model-dir / super-res-algorithms: 本机多算法超分
 */
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    /** 默认后端：local / classic */
    private String backend = "local";

    /** 本机超分模型目录（classpath: 或文件系统路径，文件命名 {algo}_x{scale}.pb） */
    private String superResModelDir = "classpath:models";

    /** 可用超分算法（对应模型需存在） */
    private List<String> superResAlgorithms = java.util.List.of("fsrcnn", "espcn", "lapsrn");

    public String getBackend() { return backend; }
    public void setBackend(String backend) { this.backend = backend; }
    public String getSuperResModelDir() { return superResModelDir; }
    public void setSuperResModelDir(String superResModelDir) { this.superResModelDir = superResModelDir; }
    public List<String> getSuperResAlgorithms() { return superResAlgorithms; }
    public void setSuperResAlgorithms(List<String> superResAlgorithms) { this.superResAlgorithms = superResAlgorithms; }
}