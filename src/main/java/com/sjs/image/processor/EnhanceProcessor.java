package com.sjs.image.processor;

import com.sjs.image.common.TaskType;
import com.sjs.image.dto.ProcessOptions;
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
import static org.bytedeco.opencv.global.opencv_photo.fastNlMeansDenoisingColored;

/**
 * 图片高清处理：Lanczos 放大 + 去噪 + 局部对比度增强(CLAHE) + 锐化(Unsharp Mask)。
 * 属于真实、可复现的增强管线，可显著提升清晰度与细节表现力。
 */
@Component
public class EnhanceProcessor implements ImageProcessor {

    private final ImageStorageService storage;

    public EnhanceProcessor(ImageStorageService storage) {
        this.storage = storage;
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

            progress.onProgress(20, "超分辨率放大 x" + scale);
            Mat upscaled = new Mat();
            resize(src, upscaled, new Size(srcW * scale, srcH * scale), 0, 0, INTER_LANCZOS4);

            progress.onProgress(45, "降噪处理");
            Mat denoised = new Mat();
            fastNlMeansDenoisingColored(upscaled, denoised, 4.0f, 4.0f, 7, 21);

            progress.onProgress(65, "局部对比度增强");
            Mat enhanced = claheEnhance(denoised);

            progress.onProgress(85, "细节锐化");
            Mat sharpened = new Mat();
            Mat blurred = new Mat();
            GaussianBlur(enhanced, blurred, new Size(0, 0), 3.0);
            addWeighted(enhanced, 1.6, blurred, -0.6, 0, sharpened);

            progress.onProgress(92, "输出结果");
            String storeName = storage.resultStoreName(sourceName, ".jpg");
            OpenCVUtils.write(sharpened, "jpg", 96, storage.resultPath(storeName));

            released(upscaled, denoised, enhanced, blurred, sharpened);
            progress.onProgress(100, "处理完成");
            String meta = "scale=" + scale + "x; " + srcW + "x" + srcH + " → " + (srcW * scale) + "x" + (srcH * scale);
            return new Outcome(storeName, meta);
        } finally {
            src.release();
        }
    }

    /** 基于 LAB 空间的 CLAHE：只增强亮度通道，避免色彩失真 */
    private Mat claheEnhance(Mat bgr) {
        Mat lab = new Mat();
        cvtColor(bgr, lab, COLOR_BGR2Lab);

        org.bytedeco.opencv.opencv_core.MatVector labChannels = new org.bytedeco.opencv.opencv_core.MatVector(3);
        split(lab, labChannels);

        Mat l = new Mat();
        CLAHE clahe = createCLAHE(2.0, new Size(8, 8));
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

    private void released(Mat... mats) {
        for (Mat m : mats) {
            if (m != null) {
                m.release();
            }
        }
    }
}