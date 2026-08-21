package com.sjs.image.ai;

import com.sjs.image.config.AiProperties;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_dnn_superres.DnnSuperResImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import static org.bytedeco.opencv.global.opencv_dnn.DNN_BACKEND_DEFAULT;
import static org.bytedeco.opencv.global.opencv_dnn.DNN_TARGET_CPU;

/**
 * 本机多算法深度超分引擎（OpenCV DNN SuperRes）。
 * 支持 FSRCNN / ESPCN / LapSRN 等算法，按「算法 + 倍率」加载并缓存；
 * 若某算法缺少请求倍率，会退而用其最近可用倍率再缩放，保证始终有输出。
 */
public class OpenCvDnnSuperResEngine {

    private static final Logger log = LoggerFactory.getLogger(OpenCvDnnSuperResEngine.class);

    private final AiProperties props;
    /** 缓存 Key = algorithm + "@" + modelScale */
    private final Map<String, DnnSuperResImpl> models = new ConcurrentHashMap<>();

    public OpenCvDnnSuperResEngine(AiProperties props) {
        this.props = props;
    }

    public List<String> algorithms() {
        return props.getSuperResAlgorithms();
    }

    /** 用指定算法将 BGR 图放大到 scale 倍；该算法不可用返回 null（上层回退经典）。 */
    public Mat upscale(Mat bgr, int scale, String algorithm) {
        if (algorithm == null || algorithm.isBlank()) {
            algorithm = firstAlgorithm();
        }
        DnnSuperResImpl[] holder = new DnnSuperResImpl[1];
        int modelScale = resolveScale(algorithm, scale, holder);
        if (holder[0] == null) {
            return null;
        }
        if (modelScale <= 0) {
            return null;
        }
        try {
            Mat out = new Mat();
            holder[0].upsample(bgr, out);
            Mat result = out;
            if (scale != modelScale) {
                Mat r = new Mat();
                org.bytedeco.opencv.global.opencv_imgproc.resize(out, r,
                        new org.bytedeco.opencv.opencv_core.Size(bgr.cols() * scale, bgr.rows() * scale),
                        0, 0, org.bytedeco.opencv.global.opencv_imgproc.INTER_CUBIC);
                out.release();
                result = r;
            }
            return result;
        } catch (Throwable t) {
            log.warn("超分推理失败({} x{}): {}", algorithm, scale, t.getMessage());
            return null;
        }
    }

    private String firstAlgorithm() {
        List<String> algs = algorithms();
        return algs.isEmpty() ? null : algs.get(0);
    }

    /** 选一个该算法可用的倍率模型；优先精确匹配，其次 4/2/3。 */
    private int resolveScale(String algorithm, int desiredScale, DnnSuperResImpl[] holder) {
        int[] candidates = cand(desiredScale);
        for (int ms : candidates) {
            DnnSuperResImpl sr = models.computeIfAbsent(algorithm + "@" + ms, k -> load(algorithm, ms));
            if (sr != null) {
                holder[0] = sr;
                return ms;
            }
        }
        return 0;
    }

    private int[] cand(int desired) {
        int d = Math.min(Math.max(desired, 2), 4);
        if (d == 3) {
            return new int[]{3, 4, 2};
        }
        return new int[]{d, 2, 4, 3};
    }

    private DnnSuperResImpl load(String algorithm, int scale) {
        try {
            Path pb = resolveModel(algorithm, scale);
            DnnSuperResImpl sr = new DnnSuperResImpl(algorithm, scale);
            sr.readModel(pb.toString());
            sr.setPreferableBackend(DNN_BACKEND_DEFAULT);
            sr.setPreferableTarget(DNN_TARGET_CPU);
            log.info("超分模型已加载: {} x{} ({})", algorithm, scale, pb.toAbsolutePath());
            return sr;
        } catch (Throwable t) {
            log.warn("超分模型加载失败({} x{}): {}", algorithm, scale, t.getMessage());
            return null;
        }
    }

    private Path resolveModel(String algorithm, int scale) throws Exception {
        String loc = props.getSuperResModelDir() + "/" + algorithm + "_x" + scale + ".pb";
        if (loc.startsWith("classpath:")) {
            String res = loc.substring("classpath:".length());
            Path tmp = Files.createTempFile(algorithm + "_x" + scale, ".pb");
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(res)) {
                if (in == null) {
                    throw new java.io.FileNotFoundException("模型缺失: " + res);
                }
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            }
            tmp.toFile().deleteOnExit();
            return tmp;
        }
        Path p = Path.of(loc);
        if (!Files.exists(p)) {
            throw new java.io.FileNotFoundException("模型缺失: " + loc);
        }
        return p;
    }
}