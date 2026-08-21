package com.sjs.image.processor;

import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.RectVector;
import org.bytedeco.opencv.opencv_objdetect.CascadeClassifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于 OpenCV Haar 级联的人脸检测工具。
 * 将内置的级联模型解压到临时文件后加载（CascadeClassifier 需要文件路径）。
 */
public final class FaceDetectorUtil {

    private static final Logger log = LoggerFactory.getLogger(FaceDetectorUtil.class);
    private static volatile String cascadePath;

    private FaceDetectorUtil() {}

    /** 检测灰度图(或任意 Mat 内部会转灰度)中的人脸，返回 [x, y, w, h] 列表 */
    public static List<int[]> detect(Mat img) {
        try {
            CascadeClassifier classifier = loadedClassifier();
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
        } catch (IOException e) {
            log.warn("人脸检测不可用，跳过脸部精修: {}", e.getMessage());
            return List.of();
        }
    }

    private static CascadeClassifier loadedClassifier() throws IOException {
        String path = cascadePath;
        if (path == null) {
            synchronized (FaceDetectorUtil.class) {
                if (cascadePath == null) {
                    Path tmp = Files.createTempFile("haarcascade_frontalface", ".xml");
                    try (InputStream in = FaceDetectorUtil.class.getClassLoader()
                            .getResourceAsStream("opencv/haarcascade_frontalface_default.xml")) {
                        if (in == null) {
                            throw new IOException("级联模型资源缺失");
                        }
                        Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
                    }
                    cascadePath = tmp.toAbsolutePath().toString();
                    tmp.toFile().deleteOnExit();
                }
                path = cascadePath;
            }
        }
        CascadeClassifier classifier = new CascadeClassifier(path);
        if (classifier.empty()) {
            throw new IOException("级联模型加载失败: " + path);
        }
        return classifier;
    }
}