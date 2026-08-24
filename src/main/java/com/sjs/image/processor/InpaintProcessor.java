package com.sjs.image.processor;

import com.sjs.image.common.TaskType;
import com.sjs.image.dto.ProcessOptions;
import com.sjs.image.service.ImageStorageService;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.bytedeco.opencv.opencv_core.Size;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

import static org.bytedeco.opencv.global.opencv_core.CV_8UC1;
import static org.bytedeco.opencv.global.opencv_core.countNonZero;
import static org.bytedeco.opencv.global.opencv_imgproc.*;
import static org.bytedeco.opencv.global.opencv_photo.inpaint;

/**
 * 马赛克消除：基于边缘检测自动定位锯齿网格区域并填充遮罩，
 * 再用 OpenCV Inpainting(TELEA) 智能恢复被遮挡内容。
 */
@Component
public class InpaintProcessor implements ImageProcessor {

    private final ImageStorageService storage;

    public InpaintProcessor(ImageStorageService storage) {
        this.storage = storage;
    }

    @Override
    public TaskType type() {
        return TaskType.INPAINT;
    }

    @Override
    public Outcome process(Path source, String sourceName, ProcessOptions opts, Progress progress) throws Exception {
        progress.onProgress(5, "读取原图");
        Mat src = OpenCVUtils.readBgr(source);
        try {
            progress.onProgress(30, "自动定位马赛克区域");
            MosaicResult mosaic = detectMosaicMask(src);
            Mat mask = mosaic.mask();

            if (isNearlyEmpty(mask)) {
                progress.onProgress(100, "未检测到明显马赛克区域，原图已返回");
                String keepName = storage.resultStoreName(sourceName, ".png");
                OpenCVUtils.write(src, "png", 100, storage.resultPath(keepName));
                mask.release();
                return new Outcome(keepName, "未检测到马赛克");
            }

            progress.onProgress(65, "智能修复被遮挡区域");
            Mat restored = new Mat();
            inpaint(src, mask, restored, 6.0, 1 /* INPAINT_TELEA */);

            progress.onProgress(88, "输出结果");
            String ext = storage.extensionOf(sourceName);
            String storeName = storage.resultStoreName(sourceName, storage.extensionOf(sourceName));
            OpenCVUtils.write(restored, OpenCVUtils.normalizeFormat(ext), 96, storage.resultPath(storeName));

            long area = countNonZero(mask);
            mask.release();
            restored.release();
            progress.onProgress(100, "处理完成");

            double percent = (double) area / (src.rows() * src.cols() + 1) * 100;
            String meta = String.format("已覆盖 %d 处马赛克区域（约 %.1f%% 画面）", mosaic.regions(), percent);
            return new Outcome(storeName, meta);
        } finally {
            src.release();
        }
    }

    /**
     * 自动定位马赛克区域：
     * 1) 灰度 + Canny 边缘（锯齿网格会产生规则强边缘）；
     * 2) 闭运算连接网格线成块；
     * 3) 取连通区域作为修复遮罩。
     */
    private MosaicResult detectMosaicMask(Mat src) {
        Mat gray = new Mat();
        cvtColor(src, gray, COLOR_BGR2GRAY);

        Mat edges = new Mat();
        Canny(gray, edges, 50, 160, 3, false);

        Mat closed = new Mat();
        Mat kernel = getStructuringElement(MORPH_RECT, new Size(9, 9));
        morphologyEx(edges, closed, MORPH_CLOSE, kernel);
        Mat opened = new Mat();
        morphologyEx(closed, opened, MORPH_OPEN, kernel);

        Mat mask = new Mat(src.rows(), src.cols(), CV_8UC1,
                new org.bytedeco.opencv.opencv_core.Scalar(0.0));

        MatVector contours = new MatVector();
        Mat hierarchy = new Mat();
        findContours(opened, contours, hierarchy, RETR_EXTERNAL, CHAIN_APPROX_SIMPLE);

        int totalPixels = src.rows() * src.cols();
        int count = 0;
        for (long i = 0; i < contours.size(); i++) {
            double area = contourArea(contours.get(i));
            if (area > totalPixels * 0.005) {
                Mat hull = new Mat();
                convexHull(contours.get(i), hull);
                MatVector polys = new MatVector(1);
                polys.put(0, hull);
                fillPoly(mask, polys, new org.bytedeco.opencv.opencv_core.Scalar(255.0));
                hull.release();
                count++;
            }
        }

        hierarchy.release();
        kernel.release();
        gray.release(); edges.release(); closed.release(); opened.release();
        return new MosaicResult(mask, count);
    }

    /** 马赛克检测结果：遮罩 + 覆盖区域数量（两者同属一次检测，避免共享可变字段带来的并发竞争）。 */
    private record MosaicResult(Mat mask, int regions) {}

    private boolean isNearlyEmpty(Mat mask) {
        return countNonZero(mask) < 20;
    }
}