package com.sjs.image.processor;

import com.sjs.image.common.TaskType;
import com.sjs.image.dto.ProcessOptions;
import com.sjs.image.ai.AiService;
import com.sjs.image.service.ImageStorageService;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Size;
import org.bytedeco.opencv.opencv_imgproc.CLAHE;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

import static org.bytedeco.opencv.global.opencv_core.addWeighted;
import static org.bytedeco.opencv.global.opencv_core.merge;
import static org.bytedeco.opencv.global.opencv_core.split;
import static org.bytedeco.opencv.global.opencv_imgproc.*;

/**
 * 图片高清处理：优先 AI 后端（本机 ONNX 超分 / 云端 API），
 * 不可用时回退经典管线（Lanczos 放大 + 去噪 + CLAHE + 锐化）。
 */
@Component
public class EnhanceProcessor implements ImageProcessor {

    private final ImageStorageService storage;
    private final AiService aiService;

    public EnhanceProcessor(ImageStorageService storage, AiService aiService) {
        this.storage = storage;
        this.aiService = aiService;
    }

    @Override
    public TaskType type() {
        return TaskType.ENHANCE;
    }

    @Override
    public Outcome process(Path source, String sourceName, ProcessOptions opts, Progress progress) throws Exception {
        progress.onProgress(5, "读取原图");
        Mat src = OpenCVUtils.readBgr(source);
        try {
            int srcW = src.cols();
            int srcH = src.rows();
            int scale = opts.getScale();

            // AI 增强优先（本机模型 / 云端）
            progress.onProgress(10, "AI 超分辨率增强 x" + scale);
            Mat ai = aiService.enhance(src, scale, opts.getSuperResAlgorithm(), opts.getSharpen(), opts.getClarity(), progress, opts.getBackend());
            if (ai != null) {
                String algo = opts.getSuperResAlgorithm() == null ? "auto" : opts.getSuperResAlgorithm();
                String sharper = opts.getSharpen() > 0 ? "；锐化" + opts.getSharpen() : "";
                String clarityTxt = opts.getClarity() > 0 ? "；对比度" + opts.getClarity() : "";
                return saveAiResult(ai, sourceName, progress, algo + " scale=" + scale + "x" + sharper + clarityTxt + "; " + srcW + "x" + srcH + " → " + (srcW * scale) + "x" + (srcH * scale));
            }
            progress.onProgress(12, "使用经典增强管线");

            progress.onProgress(20, "超分辨率放大 x" + scale);
            Mat upscaled = new Mat();
            resize(src, upscaled, new Size(srcW * scale, srcH * scale), 0, 0, INTER_LANCZOS4);

            // 经典管线三个阶段参数可调（0-100），0 表示跳过该阶段；
            // 全程只维护一个 current，每次被替换时释放旧 Mat，保证无泄漏且恰好释放一次。
            Mat current = upscaled;

            int denoise = opts.getDenoise();
            if (denoise > 0) {
                progress.onProgress(45, "保边去噪 " + denoise + "%");
                double sigma = 30 + denoise / 100.0 * 60; // 30~90
                Mat denoised = new Mat();
                bilateralFilter(current, denoised, 9, sigma, sigma);
                current.release();
                current = denoised;
            }

            int clarity = opts.getClarity();
            if (clarity > 0) {
                progress.onProgress(65, "局部对比度 " + clarity + "%");
                double clip = 1.0 + clarity / 100.0 * 3.0; // 1.0~4.0
                Mat enhanced = claheEnhance(current, clip);
                current.release();
                current = enhanced;
            }

            progress.onProgress(85, "细节锐化");
            Mat sharpened;
            if (opts.getSharpen() > 0) {
                sharpened = sharpen(current, opts.getSharpen());
                current.release();
            } else {
                sharpened = current; // 关闭锐化时直接输出，所有权移交给 sharpened
            }

            progress.onProgress(92, "输出结果");
            String storeName = storage.resultStoreName(sourceName, ".jpg");
            OpenCVUtils.write(sharpened, "jpg", 96, storage.resultPath(storeName));
            sharpened.release();

            progress.onProgress(100, "处理完成");
            String meta = "经典管线 scale=" + scale + "x; 去噪" + denoise + "/对比度" + clarity
                    + "/锐化" + opts.getSharpen() + "; " + srcW + "x" + srcH + " → " + (srcW * scale) + "x" + (srcH * scale);
            return new Outcome(storeName, meta);
        } finally {
            src.release();
        }
    }

    /** 保存 AI 增强结果并返回 Outcome。 */
    private Outcome saveAiResult(Mat ai, String sourceName, Progress progress, String meta) throws Exception {
        progress.onProgress(92, "AI 超分输出");
        String storeName = storage.resultStoreName(sourceName, ".png");
        OpenCVUtils.write(ai, "png", 100, storage.resultPath(storeName));
        ai.release();
        progress.onProgress(100, "处理完成");
        return new Outcome(storeName, "AI " + meta);
    }

    /** 基于 LAB 空间的 CLAHE：只增强亮度通道，避免色彩失真。clipLimit 为对比度阈值。 */
    private Mat claheEnhance(Mat bgr, double clipLimit) {
        Mat lab = new Mat();
        cvtColor(bgr, lab, COLOR_BGR2Lab);

        org.bytedeco.opencv.opencv_core.MatVector labChannels = new org.bytedeco.opencv.opencv_core.MatVector(3);
        split(lab, labChannels);

        Mat l = new Mat();
        CLAHE clahe = createCLAHE(clipLimit, new Size(8, 8));
        clahe.apply(labChannels.get(0), l);

        org.bytedeco.opencv.opencv_core.MatVector merged = new org.bytedeco.opencv.opencv_core.MatVector(3);
        merged.put(0, l);
        merged.put(1, labChannels.get(1));
        merged.put(2, labChannels.get(2));
        Mat out = new Mat();
        merge(merged, out);
        cvtColor(out, out, COLOR_Lab2BGR);

        lab.release();
        l.release();
        return out;
    }

    /** Unsharp Mask 锐化：amount 随强度 0.3~1.2，与 AI 路径一致。 */
    private Mat sharpen(Mat in, int sharpen) {
        double amount = 0.3 + sharpen / 100.0 * 0.9;
        Mat blurred = new Mat();
        GaussianBlur(in, blurred, new Size(0, 0), 3.0);
        Mat out = new Mat();
        addWeighted(in, 1 + amount, blurred, -amount, 0, out);
        blurred.release();
        return out;
    }
}