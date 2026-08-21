package com.sjs.image.processor;

import com.sjs.image.common.TaskType;
import com.sjs.image.dto.ProcessOptions;
import com.sjs.image.service.ImageStorageService;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Rect;
import org.bytedeco.opencv.opencv_core.Size;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Locale;

import static org.bytedeco.opencv.global.opencv_imgproc.*;

/**
 * 图片格式转换：自定义输出尺寸、图片比例、压缩质量与输出格式（JPG/PNG/WEBP）。
 */
@Component
public class ConvertProcessor implements ImageProcessor {

    private final ImageStorageService storage;

    public ConvertProcessor(ImageStorageService storage) {
        this.storage = storage;
    }

    @Override
    public TaskType type() {
        return TaskType.CONVERT;
    }

    @Override
    public Outcome process(Path source, String sourceName, ProcessOptions opts, Progress progress) throws Exception {
        progress.onProgress(10, "读取原图");
        Mat src = OpenCVUtils.readBgr(source);
        try {
            int W = src.cols();
            int H = src.rows();

            String format = "jpg";
            if (opts.getFormat() != null && !opts.getFormat().isBlank()) {
                format = OpenCVUtils.normalizeFormat(opts.getFormat());
                if (!"jpg".equals(format) && !"jpeg".equals(format)
                        && !"png".equals(format) && !"webp".equals(format)) {
                    format = "jpg";
                }
            }

            Rect crop = computeCrop(W, H, opts);               // 裁剪区域（若需按比例）
            int outW = crop.width();
            int outH = crop.height();
            Integer tw = opts.getWidth();
            Integer th = opts.getHeight();
            if (tw != null && th != null) {
                outW = tw;
                outH = th;
            } else if (tw != null) {
                outW = tw;
                outH = (int) Math.round(tw * (double) crop.height() / crop.width());
            } else if (th != null) {
                outH = th;
                outW = (int) Math.round(th * (double) crop.width() / crop.height());
            }

            progress.onProgress(45, "裁剪与缩放");
            Mat working = src;
            boolean ownSource = false;
            if (crop.x() != 0 || crop.y() != 0 || crop.width() != W || crop.height() != H) {
                working = new Mat(src, crop);
                ownSource = true;
            }

            Mat resized = new Mat();
            int interpolation = (outW < working.cols() && outH < working.rows()) ? INTER_AREA : INTER_CUBIC;
            if (outW == working.cols() && outH == working.rows()) {
                resized = working;
            } else {
                resize(working, resized, new Size(outW, outH), 0, 0, interpolation);
            }

            progress.onProgress(75, "编码输出 " + format.toUpperCase(Locale.ROOT));
            String storeName = storage.resultStoreName(sourceName, "." + format);
            OpenCVUtils.write(resized, format, opts.getQuality(), storage.resultPath(storeName));

            if (resized != working) {
                resized.release();
            }
            if (ownSource) {
                working.release();
            }
            progress.onProgress(100, "处理完成");

            String meta = W + "x" + H + " → " + outW + "x" + outH + "；"
                    + format.toUpperCase(Locale.ROOT) + "；质量 " + opts.getQuality() + "%";
            return new Outcome(storeName, meta);
        } finally {
            src.release();
        }
    }

    /**
     * 计算裁剪区域：优先遵循 ratio 比例（cover 居中裁剪），否则保持原图。
     */
    private Rect computeCrop(int W, int H, ProcessOptions opts) {
        String ratio = opts.getRatio();
        if (ratio == null || ratio.isBlank()) {
            return new Rect(0, 0, W, H);
        }
        String[] parts = ratio.trim().toLowerCase(Locale.ROOT).split(":");
        try {
            double rw = Double.parseDouble(parts[0]);
            double rh = Double.parseDouble(parts[1]);
            if (rw <= 0 || rh <= 0) {
                return new Rect(0, 0, W, H);
            }
            double target = rw / rh;
            double src = (double) W / H;
            int cropW = W;
            int cropH = H;
            if (src > target) {
                cropW = (int) Math.round(H * target);
            } else {
                cropH = (int) Math.round(W / target);
            }
            cropW = Math.min(cropW, W);
            cropH = Math.min(cropH, H);
            int x = (W - cropW) / 2;
            int y = (H - cropH) / 2;
            return new Rect(x, y, cropW, cropH);
        } catch (Exception e) {
            return new Rect(0, 0, W, H);
        }
    }
}