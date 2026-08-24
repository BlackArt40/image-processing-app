package com.sjs.image.ai;

import com.sjs.image.common.ClasspathResourceUtils;
import com.sjs.image.config.AiProperties;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_dnn_superres.DnnSuperResImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
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
@Component
public class OpenCvDnnSuperResEngine {

    private static final Logger log = LoggerFactory.getLogger(OpenCvDnnSuperResEngine.class);

    /**
     * 每种算法官方支持的放大倍率（单点事实来源，前端倍率选项由后端此表生成）。
     * 例如 LapSRN 官方仅提供 x2 / x4 模型。
     */
    private static final Map<String, List<Integer>> SUPPORTED_SCALES = buildSupportedScales();

    private static Map<String, List<Integer>> buildSupportedScales() {
        Map<String, List<Integer>> map = new LinkedHashMap<>();
        map.put("edsr", List.of(2, 3, 4));
        map.put("fsrcnn", List.of(2, 3, 4));
        map.put("espcn", List.of(2, 3, 4));
        map.put("lapsrn", List.of(2, 4));
        return map;
    }

    private final AiProperties props;
    /** 缓存 Key = algorithm + "@" + modelScale */
    private final Map<String, DnnSuperResImpl> models = new ConcurrentHashMap<>();

    public OpenCvDnnSuperResEngine(AiProperties props) {
        this.props = props;
    }

    public List<String> algorithms() {
        return props.getSuperResAlgorithms();
    }

    /** 配置中启用的算法 → 官方支持倍率（供前端渲染倍率下拉框）。 */
    public Map<String, List<Integer>> supportedScales() {
        Map<String, List<Integer>> result = new LinkedHashMap<>();
        for (String algo : algorithms()) {
            result.put(algo, SUPPORTED_SCALES.getOrDefault(algo, List.of(2, 3, 4)));
        }
        return result;
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

    /** 选一个该算法可用的倍率模型；优先精确匹配，其次按该算法的支持倍率回退。 */
    private int resolveScale(String algorithm, int desiredScale, DnnSuperResImpl[] holder) {
        int[] candidates = cand(algorithm, desiredScale);
        for (int ms : candidates) {
            DnnSuperResImpl sr = models.computeIfAbsent(algorithm + "@" + ms, k -> load(algorithm, ms));
            if (sr != null) {
                holder[0] = sr;
                return ms;
            }
        }
        return 0;
    }

    /** 候选倍率顺序：精确匹配优先，其次按支持倍率（默认序 2/4/3）回退，且仅限该算法支持范围。 */
    private int[] cand(String algorithm, int desired) {
        int d = Math.min(Math.max(desired, 2), 4);
        List<Integer> supported = SUPPORTED_SCALES.getOrDefault(algorithm, List.of(2, 3, 4));
        int[] pref;
        if (d == 3) {
            pref = new int[]{3, 4, 2};
        } else {
            pref = new int[]{d, 2, 4, 3};
        }
        java.util.ArrayList<Integer> list = new java.util.ArrayList<>();
        for (int p : pref) {
            if (supported.contains(p) && !list.contains(p)) {
                list.add(p);
            }
        }
        return list.stream().mapToInt(Integer::intValue).toArray();
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
            return ClasspathResourceUtils.toTempFile(res, algorithm + "_x" + scale, ".pb");
        }
        Path p = Path.of(loc);
        if (!Files.exists(p)) {
            throw new java.io.FileNotFoundException("模型缺失: " + loc);
        }
        return p;
    }
}