package com.sjs.image.ai;

import com.sjs.image.config.AiProperties;
import com.sjs.image.processor.Progress;
import org.bytedeco.opencv.opencv_core.Mat;
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

    public AiService(AiProperties props) {
        this.props = props;
        this.localSuperRes = new OpenCvDnnSuperResEngine(props);
    }

    public String backend() {
        return props.getBackend() == null ? "local" : props.getBackend();
    }

    private String effective(String override) {
        return (override != null && !override.isBlank()) ? override : backend();
    }

    /** 高清增强：返回放大后的 BGR Mat；引擎选经典或模型不可用返回 null（调用方回退经典）。 */
    public Mat enhance(Mat src, int scale, String algorithm, int sharpen, Progress progress, String override) {
        if ("classic".equals(effective(override))) {
            return null;
        }
        Mat result = localSuperRes.upscale(src, scale, algorithm);
        if (result != null && sharpen > 0) {
            result = detailEnhance(result, sharpen);
        }
        return result;
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
}