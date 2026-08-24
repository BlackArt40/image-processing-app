package com.sjs.image.processor;

import com.sjs.image.common.TaskType;
import com.sjs.image.dto.ProcessOptions;
import com.sjs.image.service.ImageStorageService;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.bytedeco.opencv.global.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_imgproc.*;

/**
 * 去雾 / 低光增强：
 * - 去雾：基于暗通道先验（Dark Channel Prior, He et al.）的简化实现，估大气光与透射率恢复清晰度；
 * - 低光：LAB 亮度通道 gamma 校正（gamma<1 提亮）。
 * 两项均按 0-100 强度与原图混合（0=关闭，100=完全应用），可单独或叠加使用。
 */
@Component
public class DehazeProcessor implements ImageProcessor {

    private final ImageStorageService storage;

    public DehazeProcessor(ImageStorageService storage) {
        this.storage = storage;
    }

    @Override
    public TaskType type() {
        return TaskType.DEHAZE;
    }

    @Override
    public Outcome process(Path source, String sourceName, ProcessOptions opts, Progress progress) throws Exception {
        progress.onProgress(10, "读取原图");
        Mat src = OpenCVUtils.readBgr(source);
        try {
            Mat result = src;
            boolean own = false;
            List<String> applied = new ArrayList<>();

            int dehaze = opts.getDehaze();
            if (dehaze > 0) {
                progress.onProgress(35, "去雾 " + dehaze + "%");
                Mat full = dehazeFull(src);                         // 满强度去雾结果
                Mat blended = new Mat();
                float t = dehaze / 100f;
                if (t >= 0.999f) {
                    blended = full;
                } else {
                    addWeighted(src, 1f - t, full, t, 0, blended);
                    full.release();
                }
                if (own) result.release();
                result = blended;
                own = true;
                applied.add("去雾");
            }

            int lowLight = opts.getLowLight();
            if (lowLight > 0) {
                progress.onProgress(70, "低光增强 " + lowLight + "%");
                Mat full = lowLightEnhance(result, lowLight);
                Mat blended = new Mat();
                float t = lowLight / 100f;
                if (t >= 0.999f) {
                    blended = full;
                } else {
                    addWeighted(result, 1f - t, full, t, 0, blended);
                    full.release();
                }
                if (own) result.release();
                result = blended;
                applied.add("低光");
            }

            progress.onProgress(90, "输出结果");
            String storeName = storage.resultStoreName(sourceName, ".jpg");
            OpenCVUtils.write(result, "jpg", 95, storage.resultPath(storeName));
            result.release();

            progress.onProgress(100, "处理完成");
            String meta = applied.isEmpty() ? "未应用" : String.join(" + ", applied);
            if (dehaze > 0) meta += "；去雾" + dehaze;
            if (lowLight > 0) meta += "；低光" + lowLight;
            return new Outcome(storeName, meta + "；" + src.cols() + "x" + src.rows());
        } finally {
            src.release();
        }
    }

    /**
     * 暗通道先验去雾（简化）。
     * I. 像素级 RGB 最小值 = 暗通道；erode（局部最小值）得到大气估计的暗通道邻域；
     * II. 大气光 A = 暗通道最亮像素亮度（0-1 空间，此处用其邻近暗通道的最大值代表）；
     * III. 透射率 t = 1 - 0.95·dark/A，下限 0.1；
     * IV. 复原 J = (I - A)/max(t,0.1) + A。
     * 全程在 0-1 浮点空间逐通道运算。不释放传入 bgr。
     */
    private Mat dehazeFull(Mat bgr) {
        Mat src = new Mat();
        bgr.convertTo(src, CV_32F, 1.0 / 255.0, 0.0);

        MatVector channels = new MatVector(3);
        split(src, channels);
        Mat b = channels.get(0), g = channels.get(1), r = channels.get(2);

        Mat minBG = new Mat();
        min(b, g, minBG);
        Mat dark = new Mat();
        min(minBG, r, dark);          // 像素级暗通道（0-1）
        minBG.release();

        Mat darkMin = new Mat();
        Mat kernel = getStructuringElement(MORPH_RECT, new org.bytedeco.opencv.opencv_core.Size(15, 15));
        erode(dark, darkMin, kernel); // 局部最小值滤波
        kernel.release();
        dark.release();

        // 大气光 A = 暗通道最大亮度（reduce 求全局最大，读到 1x1 标量）
        Mat maxRow = new Mat();
        reduce(darkMin, maxRow, 0, REDUCE_MAX, CV_32F);
        Mat maxVal = new Mat();
        reduce(maxRow, maxVal, 1, REDUCE_MAX, CV_32F);
        maxRow.release();
        double A = Math.max(maxVal.ptr(0, 0).getFloat(), 1e-3);
        maxVal.release();

        // t = 1 - 0.95*darkMin/A，下限 0.1
        Mat t = new Mat();
        darkMin.convertTo(t, CV_32F, -0.95 / A, 1.0);
        darkMin.release();
        Mat t0 = new Mat(t.rows(), t.cols(), CV_32F, org.bytedeco.opencv.opencv_core.Scalar.all(0.1));
        max(t, t0, t);
        t0.release();

        Mat invT = new Mat();
        pow(t, -1.0, invT);           // 1/t
        t.release();

        MatVector restored = new MatVector(3);
        Mat[] bgrCh = {b, g, r};
        for (int i = 0; i < 3; i++) {
            Mat shifted = new Mat();
            bgrCh[i].convertTo(shifted, CV_32F, 1.0, -A);       // I - A
            Mat mul = new Mat();
            multiply(shifted, invT, mul);                       // (I-A)/t
            shifted.release();
            Mat out = new Mat();
            mul.convertTo(out, CV_32F, 1.0, A);                 // +A
            mul.release();
            restored.put(i, out);
        }
        invT.release();

        Mat outF = new Mat();
        merge(restored, outF);
        Mat out = new Mat();
        outF.convertTo(out, CV_8U, 255.0, 0.0);
        outF.release();
        src.release();
        b.release();
        g.release();
        r.release();
        for (int i = 0; i < 3; i++) {
            restored.get(i).release();
        }
        return out;
    }

    /** 低光增强：LAB 亮度通道 gamma(<1) 提亮。强度 0-100 → gamma 1.0~0.55。返回新 Mat。 */
    private Mat lowLightEnhance(Mat bgr, int strength) {
        double gamma = 1.0 - strength / 100.0 * 0.45;
        Mat lab = new Mat();
        cvtColor(bgr, lab, COLOR_BGR2Lab);
        MatVector channels = new MatVector(3);
        split(lab, channels);
        Mat l = channels.get(0);
        Mat lF = new Mat();
        l.convertTo(lF, CV_32F, 1.0 / 255.0, 0.0);   // 归一化到 0-1
        pow(lF, 1.0 / gamma, lF);                    // L^(1/gamma) 提亮
        Mat lOut = new Mat();
        lF.convertTo(lOut, CV_8U, 255.0, 0.0);

        MatVector merged = new MatVector(3);
        merged.put(0, lOut);
        merged.put(1, channels.get(1));
        merged.put(2, channels.get(2));
        Mat outLab = new Mat();
        merge(merged, outLab);
        Mat out = new Mat();
        cvtColor(outLab, out, COLOR_Lab2BGR);

        lab.release();
        lF.release();
        lOut.release();
        return out;
    }
}