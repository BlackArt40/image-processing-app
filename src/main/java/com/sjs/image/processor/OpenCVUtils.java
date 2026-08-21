package com.sjs.image.processor;

import com.sjs.image.common.ProcessingException;
import org.bytedeco.opencv.opencv_core.Mat;

import java.nio.file.Path;
import java.util.Locale;

import static org.bytedeco.opencv.global.opencv_imgcodecs.IMWRITE_JPEG_QUALITY;
import static org.bytedeco.opencv.global.opencv_imgcodecs.IMWRITE_PNG_COMPRESSION;
import static org.bytedeco.opencv.global.opencv_imgcodecs.IMWRITE_WEBP_QUALITY;
import static org.bytedeco.opencv.global.opencv_imgcodecs.imread;
import static org.bytedeco.opencv.global.opencv_imgcodecs.imwrite;
import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_BGRA2BGR;
import static org.bytedeco.opencv.global.opencv_imgproc.COLOR_GRAY2BGR;
import static org.bytedeco.opencv.global.opencv_imgproc.cvtColor;

/**
 * OpenCV 编解码工具：统一读取与写入 JPG / PNG / WEBP / BMP / GIF 等，
 * 用 int[] 传参控制 JPG/WebP 质量与 PNG 压缩级别，全程基于 Mat，节省内存切换开销。
 */
public final class OpenCVUtils {

    private OpenCVUtils() {}

    /** 读取任意图片为 3 通道 BGR Mat */
    public static Mat readBgr(Path path) {
        Mat mat = imread(path.toString());
        if (mat == null || mat.empty()) {
            throw new ProcessingException("无法解析图片文件: " + path.getFileName());
        }
        if (mat.channels() == 4) {
            Mat bgr = new Mat();
            cvtColor(mat, bgr, COLOR_BGRA2BGR);
            mat.release();
            return bgr;
        }
        if (mat.channels() == 1) {
            Mat bgr = new Mat();
            cvtColor(mat, bgr, COLOR_GRAY2BGR);
            mat.release();
            return bgr;
        }
        return mat;
    }

    /**
     * 写入 BGR Mat 到文件。
     *
     * @param bgr     BGR 三通道 Mat
     * @param ext     输出扩展名 jpg/png/webp（其余按 jpg 处理）
     * @param quality 1-100：jpg/webp 质量；png 映射为压缩级别（高=更清晰/更大）
     */
    public static void write(Mat bgr, String ext, int quality, Path target) {
        String fmt = normalizeFormat(ext);
        boolean ok;
        if ("jpg".equals(fmt)) {
            ok = imwrite(target.toString(), bgr, new int[]{IMWRITE_JPEG_QUALITY, clamp(quality)});
        } else if ("webp".equals(fmt)) {
            ok = imwrite(target.toString(), bgr, new int[]{IMWRITE_WEBP_QUALITY, clamp(quality)});
        } else if ("png".equals(fmt)) {
            int compression = 9 - (clamp(quality) * 8) / 100; // quality 越高，压缩越低（更清晰）
            ok = imwrite(target.toString(), bgr, new int[]{IMWRITE_PNG_COMPRESSION, compression});
        } else {
            ok = imwrite(target.toString(), bgr, new int[]{IMWRITE_JPEG_QUALITY, clamp(quality)});
        }
        if (!ok) {
            throw new ProcessingException("图片编码失败");
        }
    }

    public static String normalizeFormat(String ext) {
        String e = ext.toLowerCase(Locale.ROOT).trim();
        if (e.startsWith(".")) {
            e = e.substring(1);
        }
        if ("jpeg".equals(e)) {
            return "jpg";
        }
        return e;
    }

    private static int clamp(int quality) {
        return Math.max(1, Math.min(100, quality));
    }
}