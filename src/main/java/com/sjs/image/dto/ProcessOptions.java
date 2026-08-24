package com.sjs.image.dto;

import com.sjs.image.common.TaskType;

/**
 * 每个模块的处理参数。
 * 经上传请求中的 "options" JSON 字段反序列化而来，各模块只读取自身相关的字段。
 */
public class ProcessOptions {

    /** 任务类型 */
    private TaskType type;

    /** 处理引擎：auto / classic / local（为空则按 app.ai.backend 全局配置） */
    private String backend;

    // ---------- 格式转换 ----------
    /** 输出宽度（px，可选，与高度同时指定时生效） */
    private Integer width;
    /** 输出高度（px，可选） */
    private Integer height;
    /** 压缩质量 1-100 */
    private Integer quality = 90;
    /** 输出格式 JPG / PNG / WEBP */
    private String format;
    /** 图片比例 1:1 / 4:3 / 16:9 / 3:4 / 9:16（与宽高互斥时优先生效） */
    private String ratio;

    // ---------- AI 智能精修 ----------
    /** 美白 */
    private boolean whitening;
    /** 瘦脸 */
    private boolean slimming;
    /** 拉腿 */
    private boolean legLengthening;
    /** 磨皮 */
    private boolean smoothSkin;

    /** 美白强度 0-100（默认 50，与旧版固定效果一致） */
    private Integer whiteningIntensity = 50;
    /** 磨皮强度 0-100（默认 50） */
    private Integer smoothSkinIntensity = 50;
    /** 瘦脸强度 0-100（默认 50） */
    private Integer slimmingIntensity = 50;
    /** 拉腿强度 0-100（默认 50） */
    private Integer legLengtheningIntensity = 50;

    // ---------- 高清增强 ----------
    /** 放大倍数，默认 2 */
    private Integer scale = 2;
    /** 超分算法：fsrcnn / espcn / lapsrn（local 后端时生效） */
    private String superResAlgorithm;
    /** 锐化增强强度 0-100（超分后追加 Unsharp 细节增强，默认 50） */
    private Integer sharpen = 50;

    // ---------- 马赛克消除 ----------
    /** auto=自动检测马赛克区域；manual=手动遮罩（前端传入坐标，略简化） */
    private String mode = "auto";

    // ---------- 滤镜 / 风格化 ----------
    /** 滤镜预设：mono / sepia / vintage / warm / cool / vivid */
    private String filter;
    /** 滤镜强度 0-100（默认 100，越接近 100 越接近纯滤镜效果） */
    private Integer intensity = 100;

    public TaskType getType() { return type; }
    public void setType(TaskType type) { this.type = type; }

    public String getBackend() { return backend; }
    public void setBackend(String backend) { this.backend = backend; }

    public Integer getWidth() { return width; }
    public void setWidth(Integer width) { this.width = width; }
    public Integer getHeight() { return height; }
    public void setHeight(Integer height) { this.height = height; }
    public Integer getQuality() { return quality == null ? 90 : quality; }
    public void setQuality(Integer quality) { this.quality = quality; }
    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
    public String getRatio() { return ratio; }
    public void setRatio(String ratio) { this.ratio = ratio; }

    public boolean isWhitening() { return whitening; }
    public void setWhitening(boolean whitening) { this.whitening = whitening; }
    public boolean isSlimming() { return slimming; }
    public void setSlimming(boolean slimming) { this.slimming = slimming; }
    public boolean isLegLengthening() { return legLengthening; }
    public void setLegLengthening(boolean legLengthening) { this.legLengthening = legLengthening; }
    public boolean isSmoothSkin() { return smoothSkin; }
    public void setSmoothSkin(boolean smoothSkin) { this.smoothSkin = smoothSkin; }

    public int getScale() { return scale == null ? 2 : Math.min(Math.max(scale, 1), 4); }
    public void setScale(Integer scale) { this.scale = scale; }

    public String getSuperResAlgorithm() { return superResAlgorithm; }
    public void setSuperResAlgorithm(String superResAlgorithm) { this.superResAlgorithm = superResAlgorithm; }

    /** 锐化强度 0-100，空值按 50 */
    public int getSharpen() { return sharpen == null ? 50 : Math.min(Math.max(sharpen, 0), 100); }
    public void setSharpen(Integer sharpen) { this.sharpen = sharpen; }

    public String getMode() { return mode == null ? "auto" : mode; }
    public void setMode(String mode) { this.mode = mode; }

    public String getFilter() { return filter; }
    public void setFilter(String filter) { this.filter = filter; }

    /** 强度 0-100，空值按 100 */
    public int getIntensity() { return intensity == null ? 100 : Math.min(Math.max(intensity, 0), 100); }
    public void setIntensity(Integer intensity) { this.intensity = intensity; }
}