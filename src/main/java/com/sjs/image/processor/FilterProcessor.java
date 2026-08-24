package com.sjs.image.processor;

import com.sjs.image.common.ProcessingException;
import com.sjs.image.common.TaskType;
import com.sjs.image.dto.ProcessOptions;
import com.sjs.image.service.ImageStorageService;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Locale;

import static org.bytedeco.opencv.global.opencv_core.addWeighted;
import static org.bytedeco.opencv.global.opencv_core.merge;
import static org.bytedeco.opencv.global.opencv_core.split;
import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGR2GRAY;
import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGR2HSV;
import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_GRAY2BGR;
import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_HSV2BGR;
import static org.bytedeco.opencv.global.opencv_imgproc.cvtColor;

/**
 * 滤镜 / 风格化：对整幅图应用预设的风格滤镜，并按强度与原图混合。
 *
 * 支持：mono 黑白 / sepia 复古 / vintage 胶卷 / warm 暖阳 / cool 清凉 / vivid 鲜艳。
 * 强度 0-100 控制滤镜效果与原图的比例，默认 100（纯滤镜效果）。
 */
@Component
public class FilterProcessor implements ImageProcessor {

    private final ImageStorageService storage;

    public FilterProcessor(ImageStorageService storage) {
        this.storage = storage;
    }

    @Override
    public TaskType type() {
        return TaskType.FILTER;
    }

    @Override
    public Outcome process(Path source, String sourceName, ProcessOptions opts, Progress progress) throws Exception {
        String filter = opts.getFilter();
        if (filter == null || filter.isBlank()) {
            throw new ProcessingException("请选择一种滤镜");
        }
        String name = filter.toLowerCase(Locale.ROOT);

        progress.onProgress(10, "读取原图");
        Mat src = OpenCVUtils.readBgr(source);
        try {
            progress.onProgress(35, "应用滤镜 · " + name);
            Mat filtered = applyFilter(src, name);

            int intensity = opts.getIntensity();
            progress.onProgress(75, "混合强度 " + intensity + "%");
            Mat out;
            float t = intensity / 100f;
            if (t >= 0.999f) {
                out = filtered; // 满强度：直接输出滤镜结果
            } else {
                out = new Mat();
                addWeighted(src, 1f - t, filtered, t, 0, out);
                filtered.release();
            }

            progress.onProgress(90, "输出结果");
            String storeName = storage.resultStoreName(sourceName, ".jpg");
            OpenCVUtils.write(out, "jpg", 95, storage.resultPath(storeName));

            out.release();
            progress.onProgress(100, "处理完成");
            return new Outcome(storeName, label(name) + " · 强度 " + intensity + "%");
        } finally {
            src.release();
        }
    }

    /** 根据滤镜名应用对应变换，返回滤镜结果图（由调用方释放）。 */
    private Mat applyFilter(Mat bgr, String filter) {
        switch (filter) {
            case "mono":
                return toMono(bgr);
            case "vintage":
                return toSepia(bgr, 0.55);
            case "sepia":
                return toSepia(bgr, 1.0);
            case "warm":
                return tempShift(bgr, true);
            case "cool":
                return tempShift(bgr, false);
            case "vivid":
                return toVivid(bgr);
            default:
                throw new ProcessingException("不支持的滤镜: " + filter);
        }
    }

    /** 黑白：转灰度再转回 BGR。 */
    private Mat toMono(Mat bgr) {
        Mat gray = new Mat();
        cvtColor(bgr, gray, COLOR_BGR2GRAY);
        Mat out = new Mat();
        cvtColor(gray, out, COLOR_GRAY2BGR);
        gray.release();
        return out;
    }

    /**
     * 复古（sepia）：经典棕色老照片效果。
     * 基于 BGR 三通道线性组合，k 控制浓郁度（1=标准，越小越淡）。
     */
    private Mat toSepia(Mat bgr, double k) {
        MatVector c = new MatVector(3);
        split(bgr, c);
        Mat b = c.get(0), g = c.get(1), r = c.get(2);

        Mat t = new Mat();
        Mat rN = new Mat();
        addWeighted(r, 0.393 * k, g, 0.769 * k, 0, t);
        addWeighted(t, 1.0, b, 0.189 * k, 0, rN);

        Mat gN = new Mat();
        addWeighted(r, 0.349 * k, g, 0.686 * k, 0, t);
        addWeighted(t, 1.0, b, 0.168 * k, 0, gN);

        Mat bN = new Mat();
        addWeighted(r, 0.272 * k, g, 0.534 * k, 0, t);
        addWeighted(t, 1.0, b, 0.131 * k, 0, bN);

        MatVector merged = new MatVector(3);
        merged.put(0, bN);
        merged.put(1, gN);
        merged.put(2, rN);
        Mat out = new Mat();
        merge(merged, out);

        t.release();
        return out;
    }

    /** 色温偏移：暖色增加红降蓝，冷色增加蓝降红。 */
    private Mat tempShift(Mat bgr, boolean warm) {
        MatVector c = new MatVector(3);
        split(bgr, c);
        Mat b = c.get(0), g = c.get(1), r = c.get(2);
        Mat bN = new Mat(), gN = new Mat(), rN = new Mat();
        if (warm) {
            b.convertTo(bN, -1, 0.92, -8);
            g.convertTo(gN, -1, 1.0, 0);
            r.convertTo(rN, -1, 1.10, 10);
        } else {
            b.convertTo(bN, -1, 1.10, 10);
            g.convertTo(gN, -1, 1.0, 0);
            r.convertTo(rN, -1, 0.92, -6);
        }
        MatVector merged = new MatVector(3);
        merged.put(0, bN);
        merged.put(1, gN);
        merged.put(2, rN);
        Mat out = new Mat();
        merge(merged, out);
        return out;
    }

    /** 鲜艳：增强 HSV 饱和度通道。 */
    private Mat toVivid(Mat bgr) {
        Mat hsv = new Mat();
        cvtColor(bgr, hsv, COLOR_BGR2HSV);
        MatVector c = new MatVector(3);
        split(hsv, c);
        Mat s = c.get(1);
        Mat sN = new Mat();
        s.convertTo(sN, -1, 1.35, 0);
        MatVector merged = new MatVector(3);
        merged.put(0, c.get(0));
        merged.put(1, sN);
        merged.put(2, c.get(2));
        Mat out = new Mat();
        merge(merged, out);
        cvtColor(out, out, COLOR_HSV2BGR);
        hsv.release();
        return out;
    }

    /** 滤镜中文标注，用于结果元数据。 */
    private String label(String filter) {
        switch (filter) {
            case "mono": return "黑白";
            case "sepia": return "复古";
            case "vintage": return "胶卷";
            case "warm": return "暖阳";
            case "cool": return "清凉";
            case "vivid": return "鲜艳";
            default: return filter;
        }
    }
}