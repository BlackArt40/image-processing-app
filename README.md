# 映画工坊 · 在线图片处理

基于 **Spring Boot + OpenCV** 的 Web 图片处理应用，提供五个功能模块，每个模块均支持**上传 → 异步处理 → 前后对比预览 → 一键下载**：

| 模块 | 说明 | 实现要点 |
| --- | --- | --- |
| 🖼️ **高清增强** | 超分辨率放大并锐化细节，**多算法可选** | 本机深度超分 **EDSR / FSRCNN / ESPCN / LapSRN**（2/3/4×）+ CLAHE + 锐化；可切换经典 |
| ✨ **AI 智能精修** | 自动识别人像，选择性美白 / 磨皮 / 瘦脸 / 拉腿 | OpenCV Haar 级联人脸检测 + 双边滤波 + 脸部收窄 + 下半部拉伸 |
| 🧩 **马赛克消除** | 自动定位马赛克区域并智能恢复被遮挡内容 | Canny 边缘 + 形态学闭运算定位遮罩，`cv::inpaint`(TELEA) 修复 |
| 🔄 **格式转换** | 自定义输出尺寸、比例、压缩质量与格式 | 支持 JPG / PNG / WebP，居中裁剪至目标比例 |
| 🎨 **滤镜** | 整图套用风格化滤镜并可调强度 | 预设 6 种滤镜：黑白 / 复古 / 胶卷 / 暖阳 / 清凉 / 鲜艳；强度按 `addWeighted` 与原图混合 |

## ✨ AI 能力（纯本地）

高清增强支持多算法深度超分，全部在本机推理、离线免密钥；不可用时自动回退经典 OpenCV：

| 算法 | 说明 |
| --- | --- |
| `EDSR` | **高质量**，细节与纹理还原最佳（每个 scale 约 38MB） |
| `FSRCNN` | 快速、体积小（约 40KB） |
| `ESPCN` | 轻量高效（约 90KB） |
| `LapSRN` | 质量均衡（约 1–2.7MB） |
| `经典` | 纯 OpenCV（Lanczos + CLAHE + 锐化），不调用模型 |

## ✨ 技术亮点

- **异步任务 + 并发控制**：有界线程池(2~4) + 有界队列 + `CallerRunsPolicy` 平滑限流；提交即返回 `taskId`，前端轮询进度。
- **内存 / 存储优化**：全程基于 `Mat`（`imread`/`imwrite`），避免 BufferedImage 往返拷贝；流式写入、及时 `release`。
- **统一文件管理**：`./data/{uploads,processed,tmp}` 目录隔离，下载接口防路径穿越，预览与附件下载复用同一入口。
- **定时清理**：`@Scheduled` 定期删除超过保留时长的过期文件，可配置 TTL / 开关 / cron。
- **前端**：深色暖调「暗房」美学，支持拖拽上传、实时进度条、前后对比滑块（键盘可操作）、全屏灯箱、响应式。

## 🛠️ 技术栈

- **后端**：Java 17+ · Spring Boot 3.4 · JavaCV(OpenCV 4.9) · Maven
- **前端**：原生 HTML / CSS / JavaScript（无框架，随 Spring 静态资源托管）
- **图像处理**：OpenCV 官方算法（增强、人脸检测、inpainting、WebP 编解码）

## 🚀 快速开始

### 环境要求

- JDK 17 或更高
- Maven 3.9+
- （联网下载 Maven 依赖；首次构建需拉取 OpenCV 原生库）

### 下载超分模型（首次必做）

模型未随仓库提交（体积较大），先运行下载脚本（约 130MB）：

```bash
bash scripts/download-models.sh   # 拉取 EDSR / FSRCNN / ESPCN / LapSRN
```

### 构建与运行

```bash
# 编译
mvn -DskipTests compile

# 运行
mvn -DskipTests spring-boot:run
# 或在 IDE 中直接运行 ImageProcessingApplication
```

启动后访问 **http://localhost:8080** 即可使用。运行产生的数据位于项目根目录 `./data/`。若未下载模型，AI 超分会自动回退为经典增强。

## ⚙️ 配置说明（`application.yml`）

```yaml
app:
  storage:
    root: ./data            # 数据根目录
    upload-dir: uploads     # 原图目录
    processed-dir: processed# 结果目录
    tmp-dir: tmp            # 临时目录
    ttl-hours: 24           # 上传/结果文件保留时长（小时）
  cleanup:
    enabled: true           # 是否定时清理过期文件
    cron: "0 0 4 * * *"     # 每天 04:00 执行（Spring 6 位 cron）
  ai:
    backend: local          # 本地默认引擎：local | classic
    super-res-model-dir: classpath:models   # 超分模型目录（{algo}_x{scale}.pb）
    super-res-algorithms: [edsr, fsrcnn, espcn, lapsrn]
```

- 关闭清理：`app.cleanup.enabled: false`
- 调整保留时长：修改 `app.storage.ttl-hours`
- 修改清理时间：修改 `app.cleanup.cron`
- 切换本机超分后端：`app.ai.backend`（local / classic）

## 🔌 主要 API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/upload` | 上传图片 + 处理参数（`multipart`: `file`, `options`），返回 `taskId` |
| `GET` | `/api/tasks/{id}` | 轮询任务状态 / 进度 / 结果地址 |
| `GET` | `/api/preview/{name}?dir={uploads\|processed}` | 在线预览图片 |
| `GET` | `/api/download?name=&dir=&filename=` | 附件下载 |

**处理参数示例**（`options` JSON，各模块读取对应字段）：

```json
// 高清增强（本机 AI 超分，可选算法）
{"type":"ENHANCE","scale":2,"superResAlgorithm":"lapsrn"}
{"type":"ENHANCE","scale":2,"backend":"classic"}
// AI 精修（可多选）
{"type":"RETOUCH","whitening":true,"smoothSkin":true,"slimming":true,"legLengthening":true}
// 马赛克消除
{"type":"INPAINT","mode":"auto"}
// 格式转换（宽度/高度/比例可选）
{"type":"CONVERT","format":"webp","quality":70,"ratio":"16:9","width":320,"height":180}
// 滤镜（预设 + 可选强度 0-100，默认 100）
{"type":"FILTER","filter":"sepia","intensity":80}
```

## 📁 目录结构

```
src/main/java/com/sjs/image/
├── ai/            # AI 能力：本地多算法深度超分(FSRCNN/ESPCN/LapSRN)
├── config/        # 异步线程池、CORS、存储属性、AI 属性
├── controller/    # 上传 / 任务 / 预览下载 / 全局异常
├── dto/           # 请求与响应对象
├── processor/     # 六大处理器（增强、精修、去马赛克、转换、滤镜）+ OpenCV 工具 + 人脸检测
├── service/       # 任务调度、文件存储、定时清理
└── task/          # 任务运行时记录
src/main/resources/
├── static/        # 前端页面（index.html / css / js）
├── opencv/        # Haar 级联人脸检测模型
└── models/        # 超分模型（EDSR/FSRCNN/ESPCN/LapSRN；不入库，脚本拉取）
scripts/
    └── download-models.sh   # 一键下载超分模型
samples/           # 真人脸部测试样张
```

## 📝 说明

- 人脸检测基于 OpenCV 提供的开源 Haar 级联模型（[OpenCV 许可](https://opencv.org/license/)）。
- 超分模型（EDSR/FSRCNN/ESPCN/LapSRN）为开源导出权重，**不入库**，用 `scripts/download-models.sh` 拉取；新增算法只需放入 `{算法}_x{倍率}.pb` 并在配置中登记。
- 「AI 精修」「马赛克消除」以本机 OpenCV 算法实现，全部本地处理、不需外部服务。