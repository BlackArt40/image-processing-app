package com.sjs.image.processor;

import com.sjs.image.common.ClasspathResourceUtils;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.RectVector;
import org.bytedeco.opencv.opencv_objdetect.CascadeClassifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于 OpenCV Haar 级联的人脸检测工具。
 * 将内置的级联模型解压到临时文件后加载（CascadeClassifier 需要文件路径）。
 */
public final class FaceDetectorUtil {

    private static final Logger log = LoggerFactory.getLogger(FaceDetectorUtil.class);
    /** 级联模型临时文件路径（进程内固定，懒加载） */
    private static volatile String cascadePath;
    /**
     * 每个工作线程复用一个 CascadeClassifier，避免每次检测都重复加载昂贵的 Haar 级联。
     * 用 ThreadLocal 而非全局单例，规避多个 worker 线程同时访问同一原生对象的跨线程竞态。
     */
    private static final ThreadLocal<CascadeClassifier> CLASSIFIER =
            ThreadLocal.withInitial(() -> {
                try {
                    CascadeClassifier c = new CascadeClassifier(resolveCascadePath());
                    if (c.empty()) {
                        c.close();
                        throw new IllegalStateException("级联模型加载失败");
                    }
                    return c;
                } catch (IOException e) {
                    throw new IllegalStateException("级联模型加载失败", e);
                }
            });

    private FaceDetectorUtil() {}

    /** 解析级联模型临时文件路径。 */
    private static String resolveCascadePath() throws IOException {
        String path = cascadePath;
        if (path == null) {
            synchronized (FaceDetectorUtil.class) {
                if (cascadePath == null) {
                    Path tmp = ClasspathResourceUtils.toTempFile(
                            "opencv/haarcascade_frontalface_default.xml", "haarcascade_frontalface", ".xml");
                    cascadePath = tmp.toAbsolutePath().toString();
                }
                path = cascadePath;
            }
        }
        return path;
    }

    /** 检测灰度图(或任意 Mat 内部会转灰度)中的人脸，返回 [x, y, w, h] 列表 */
    public static List<int[]> detect(Mat img) {
        try {
            CascadeClassifier classifier = CLASSIFIER.get();
            RectVector faces = new RectVector();
            classifier.detectMultiScale(img, faces, 1.1, 3,
                    org.bytedeco.opencv.global.opencv_objdetect.CASCADE_SCALE_IMAGE,
                    new org.bytedeco.opencv.opencv_core.Size(60, 60),
                    new org.bytedeco.opencv.opencv_core.Size(0, 0));
            List<int[]> result = new ArrayList<>();
            for (long i = 0; i < faces.size(); i++) {
                org.bytedeco.opencv.opencv_core.Rect r = faces.get(i);
                result.add(new int[]{ r.x(), r.y(), r.width(), r.height() });
            }
            faces.close();
            return result;
        } catch (IllegalStateException e) {
            // 级联初始化失败：仅首次加载时报错，后续不走此路径
            log.warn("人脸检测不可用，跳过脸部精修: {}", e.getMessage());
            return List.of();
        }
    }
}