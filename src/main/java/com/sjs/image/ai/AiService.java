package com.sjs.image.ai;

import com.sjs.image.config.AiProperties;
import com.sjs.image.processor.Progress;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_imgproc.CLAHE;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI 能力入口（纯本地）。
 * - 高清增强：本机多算法深度超分（FSRCNN / ESPCN / LapSRN）
 * - classic：走经典 OpenCV 实现（模型不可用或显式选择时自动回退）
 * 已移除云端后端。
 */
@Service
public class AiService {

    private final AiProperties props;
    private final OpenCvDnnSuperResEngine localSuperRes;

    public AiService(AiProperties props, OpenCvDnnSuperResEngine localSuperRes) {
        this.props = props;
        this.localSuperRes = localSuperRes;
    }

    public String backend() {
        return props.getBackend() == null ? "local" : props.getBackend();
    }

    private String effective(String override) {
        return (override != null && !override.isBlank()) ? override : backend();
    }

    /** 高清增强：返回放大后的 BGR Mat；引擎选经典或模型不可用返回 null（调用方回退经典）。 */
    public Mat enhance(Mat src, int scale, String algorithm, int sharpen, int clarity, Progress progress, String override) {
        if ("classic".equals(effective(override))) {
            return null;
        }
        Mat result = localSuperRes.upscale(src, scale, algorithm);
        if (result == null) {
            return null;
        }
        // AI 超分后处理：先做 CLAHE 局部对比度增强（提升通透感与层次），再做 Unsharp 细节增强
        if (clarity > 0) {
            result = clarityEnhance(result, clarity);
        }
        if (sharpen > 0) {
            result = detailEnhance(result, sharpen);
        }
        return result;
    }

    /** 基于 LAB 亮度通道的 CLAHE：只增强亮度、不引入色彩失真。clarity 0-100 → clipLimit 1.0~2.5。 */
    private Mat clarityEnhance(Mat in, int clarity) {
        double clip = 1.0 + clarity / 100.0 * 1.5;
        Mat lab = new Mat();
        org.bytedeco.opencv.global.opencv_imgproc.cvtColor(in, lab, org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGR2Lab);
        org.bytedeco.opencv.opencv_core.MatVector labChannels = new org.bytedeco.opencv.opencv_core.MatVector(3);
        org.bytedeco.opencv.global.opencv_core.split(lab, labChannels);
        Mat l = new Mat();
        CLAHE clahe = org.bytedeco.opencv.global.opencv_imgproc.createCLAHE(clip, new org.bytedeco.opencv.opencv_core.Size(8, 8));
        clahe.apply(labChannels.get(0), l);
        org.bytedeco.opencv.opencv_core.MatVector merged = new org.bytedeco.opencv.opencv_core.MatVector(3);
        merged.put(0, l);
        merged.put(1, labChannels.get(1));
        merged.put(2, labChannels.get(2));
        Mat out = new Mat();
        org.bytedeco.opencv.global.opencv_core.merge(merged, out);
        org.bytedeco.opencv.global.opencv_imgproc.cvtColor(out, out, org.bytedeco.opencv.global.opencv_imgproc.COLOR_Lab2BGR);
        lab.release();
        l.release();
        in.release();
        return out;
    }

    /** 超分后的细节增强：Unsharp Mask（高斯背景 + 加权），锐化=强度/100 映射到 0.3~1.2。 */
    private Mat detailEnhance(Mat in, int sharpen) {
        double amount = 0.3 + (sharpen / 100.0) * 0.9; // 0.3 ~ 1.2
        org.bytedeco.opencv.opencv_core.Mat blurred = new org.bytedeco.opencv.opencv_core.Mat();
        org.bytedeco.opencv.global.opencv_imgproc.GaussianBlur(in, blurred,
                new org.bytedeco.opencv.opencv_core.Size(0, 0), 2.5);
        org.bytedeco.opencv.opencv_core.Mat out = new org.bytedeco.opencv.opencv_core.Mat();
        org.bytedeco.opencv.global.opencv_core.addWeighted(in, 1 + amount, blurred, -amount, 0, out);
        blurred.release();
        in.release();
        return out;
    }

    /** 本机超分可用算法列表（供前端展示）。 */
    public List<String> localAlgorithms() {
        return localSuperRes.algorithms();
    }

    /** 超分算法 → 官方支持倍率（供前端渲染倍率下拉框，与引擎回退逻辑共用同一份事实来源）。 */
    public java.util.Map<String, java.util.List<Integer>> superResScales() {
        return localSuperRes.supportedScales();
    }
}