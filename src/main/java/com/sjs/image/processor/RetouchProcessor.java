package com.sjs.image.processor;

import com.sjs.image.common.TaskType;
import com.sjs.image.dto.ProcessOptions;
import com.sjs.image.service.ImageStorageService;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Rect;
import org.bytedeco.opencv.opencv_core.Size;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

import static org.bytedeco.opencv.global.opencv_core.convertScaleAbs;
import static org.bytedeco.opencv.global.opencv_imgproc.*;

/**
 * AI 智能精修：美白 / 磨皮 / 瘦脸 / 拉腿。
 * - 人脸检测基于 OpenCV Haar 级联；
 * - 磨皮 = 双边滤波保边平滑；
 * - 美白 = 提高 YUV 亮度通道；
 * - 瘦脸 = 对脸部区域做水平收窄并羽化接缝；
 * - 拉腿 = 对图像下半部分竖向拉伸。
 */
@Component
public class RetouchProcessor implements ImageProcessor {

    private final ImageStorageService storage;

    public RetouchProcessor(ImageStorageService storage) {
        this.storage = storage;
    }

    @Override
    public TaskType type() {
        return TaskType.RETOUCH;
    }

    @Override
    public Outcome process(Path source, String sourceName, ProcessOptions opts, Progress progress) throws Exception {
        progress.onProgress(5, "读取原图");
        Mat src = OpenCVUtils.readBgr(source);
        try {
            Mat work = src.clone();
            List<String> applied = new java.util.ArrayList<>();

            if (opts.isWhitening()) {
                progress.onProgress(25, "美白");
                Mat w = new Mat();
                whiten(work, w);
                work.release();
                work = w;
                applied.add("美白");
            }

            // 先做人脸检测（在尺寸未变化前）
            List<int[]> faces = FaceDetectorUtil.detect(work);

            if (opts.isSmoothSkin()) {
                progress.onProgress(45, "磨皮");
                Mat s = new Mat();
                bilateralFilter(work, s, 9, 60, 60);
                work.release();
                work = s;
                applied.add("磨皮");
            }

            if (opts.isSlimming()) {
                progress.onProgress(65, "瘦脸");
                slimFaces(work, faces);
                applied.add("瘦脸");
            }

            Mat finalWork = work;
            if (opts.isLegLengthening()) {
                progress.onProgress(80, "拉腿");
                Mat stretched = legLengthen(work);
                finalWork.release();
                finalWork = stretched;
                applied.add("拉腿");
            }

            progress.onProgress(92, "输出结果");
            String storeName = storage.resultStoreName(sourceName, ".png");
            OpenCVUtils.write(finalWork, "png", 100, storage.resultPath(storeName));

            finalWork.release();
            progress.onProgress(100, "处理完成");
            String meta = String.join(" + ", applied) + "；人脸" + faces.size() + "处";
            return new Outcome(storeName, meta);
        } finally {
            src.release();
        }
    }

    /** 美白：整体提亮（Beta 参数），配合肤色自然度 */
    private void whiten(Mat src, Mat dst) {
        convertScaleAbs(src, dst, 1.0, 16.0);
    }

    /** 瘦脸：对每张脸的水平区域做收窄并羽化接缝 */
    private void slimFaces(Mat work, List<int[]> faces) {
        int W = work.cols();
        int H = work.rows();
        for (int[] f : faces) {
            int x0 = Math.max(0, f[0] - f[2] / 4);
            int x1 = Math.min(W, f[0] + f[2] + f[2] / 4);
            int width = x1 - x0;
            if (width <= 10) continue;
            int y0 = Math.max(0, f[1] - f[3] / 8);
            int y1 = Math.min(H, f[1] + f[3] + f[3] / 3);
            int height = y1 - y0;

            Mat roi = new Mat(work, new Rect(x0, y0, width, height));
            Mat slim = new Mat();
            int slimW = Math.max(10, (int) (width * 0.92));
            resize(roi, slim, new Size(slimW, height), 0, 0, INTER_CUBIC);

            int off = (width - slimW) / 2;
            Mat region = new Mat(work, new Rect(x0 + off, y0, slimW, height));
            slim.copyTo(region);

            // 羽化左右接缝
            feather(work, x0 + off - 8, y0, 16, height);
            feather(work, x0 + off + slimW - 8, y0, 16, height);

            roi.release(); slim.release(); region.release();
        }
    }

    private void feather(Mat work, int x, int y, int w, int h) {
        int W = work.cols();
        int H = work.rows();
        int cx = Math.max(0, x);
        int cw = Math.min(w, W - cx);
        int cy = Math.max(0, y);
        int ch = Math.min(h, H - cy);
        if (cw <= 1 || ch <= 1) return;
        Mat strip = new Mat();
        Mat band = new Mat(work, new Rect(cx, cy, cw, ch));
        GaussianBlur(band, strip, new Size(15, 15), 6.0);
        strip.copyTo(band);
        band.release(); strip.release();
    }

    /** 拉腿：对下半部分竖向拉伸，返回新图 */
    private Mat legLengthen(Mat work) {
        int W = work.cols();
        int H = work.rows();
        int yTop = Math.min((int) (H * 0.55), H - 10);
        int legH = H - yTop;
        int extra = Math.max(4, (int) (legH * 0.10));
        int newH = H + extra;

        Mat out = new Mat(newH, W, work.type(), new org.bytedeco.opencv.opencv_core.Scalar(255.0));

        Mat topSrc = new Mat(work, new Rect(0, 0, W, yTop));
        Mat topDst = new Mat(out, new Rect(0, 0, W, yTop));
        topSrc.copyTo(topDst);

        Mat legs = new Mat(work, new Rect(0, yTop, W, legH));
        Mat legsNew = new Mat();
        resize(legs, legsNew, new Size(W, legH + extra), 0, 0, INTER_CUBIC);
        Mat legsDst = new Mat(out, new Rect(0, yTop, W, legH + extra));
        legsNew.copyTo(legsDst);

        topSrc.release(); topDst.release();
        legs.release(); legsNew.release(); legsDst.release();
        return out;
    }
}