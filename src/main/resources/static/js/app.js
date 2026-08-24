/* ============================================================
   映画工坊 · 前端交互逻辑
   模块化：每个功能模块独立上传 / 处理 / 轮询进度 / 对比 / 下载
   ============================================================ */
(function () {
  "use strict";

  const $  = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => Array.from(r.querySelectorAll(s));

  /* ---------- 工具 ---------- */
  function fmtSize(bytes) {
    if (bytes === 0) return "0 B";
    const k = 1024;
    const u = ["B", "KB", "MB", "GB"];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return (bytes / Math.pow(k, i)).toFixed(i === 0 ? 0 : 1) + " " + u[i];
  }

  /* ---------- Tab 切换 ---------- */
  function wireTabs() {
    $$(".tab").forEach((tab) => {
      tab.addEventListener("click", () => {
        const mod = tab.dataset.mod;
        $$(".tab").forEach((t) => t.classList.toggle("is-active", t === tab));
        $$(".mod").forEach((m) => m.classList.toggle("is-active", m.dataset.mod === mod));
        document.body.dataset.mod = mod;
      });
    });
  }

  /* ---------- 前后对比滑块 ---------- */
  function wireCompare(compare) {
    const divider = $(".compare-divider", compare);
    let dragging = false;

    function setPos(px) {
      const r = compare.getBoundingClientRect();
      if (r.width === 0) return;
      let pct = ((px - r.left) / r.width) * 100;
      pct = Math.max(2, Math.min(98, pct));
      compare.style.setProperty("--pos", pct + "%");
      divider.setAttribute("aria-valuenow", Math.round(pct));
    }

    divider.addEventListener("pointerdown", (e) => {
      dragging = true;
      divider.setPointerCapture(e.pointerId);
      setPos(e.clientX);
    });
    divider.addEventListener("pointermove", (e) => dragging && setPos(e.clientX));
    divider.addEventListener("pointerup", () => (dragging = false));
    divider.addEventListener("pointercancel", () => (dragging = false));

    divider.addEventListener("keydown", (e) => {
      const step = e.key === "ArrowLeft" ? -4 : e.key === "ArrowRight" ? 4 : 0;
      if (!step) return;
      e.preventDefault();
      const cur = parseFloat(compare.style.getPropertyValue("--pos")) || 50;
      setPos(compare.getBoundingClientRect().left + (cur + step) / 100 * compare.getBoundingClientRect().width);
    });
  }

  /* ---------- 灯箱 ---------- */
  const lightbox = $(".lightbox");
  const lbImg = $(".lightbox img");
  function openLightbox(src) {
    lbImg.src = src;
    lightbox.hidden = false;
    lightbox.setAttribute("aria-hidden", "false");
  }
  function closeLightbox() {
    lightbox.hidden = true;
    lightbox.setAttribute("aria-hidden", "true");
    lbImg.src = "";
  }
  $(".lb-close").addEventListener("click", closeLightbox);
  lightbox.addEventListener("click", (e) => { if (e.target === lightbox) closeLightbox(); });
  document.addEventListener("keydown", (e) => { if (e.key === "Escape") closeLightbox(); });

  /* ---------- 模块 ---------- */
  class Module {
    constructor(root) {
      this.root = root;
      this.file = null;
      this.taskId = null;
      this.timer = null;
      this.paused = false;
      this.sourceUrl = null;
      this.resultName = null;

      this.dz = $(".dropzone", root);
      this.input = $("input[type=file]", this.dz);
      this.dzIdle = $(".dz-idle", this.dz);
      this.dzFile = $(".dz-file", this.dz);
      this.thumb = $(".dz-thumb", this.dz);
      this.dzName = $(".dz-name", this.dz);
      this.dzMeta = $(".dz-meta", this.dz);
      this.removeBtn = $(".dz-remove", this.dz);

      this.cta = $("[data-go]", root);
      this.progress = $(".progress", root);
      this.bar = $(".progress-bar", root);
      this.stage = $(".progress-stage", root);
      this.num = $(".progress-num", root);
      this.pauseBtn = $("[data-pause]", root);

      this.result = $(".result", root);
      this.state = $("[data-state]", this.result);
      this.meta = $(".result-meta", this.result);
      this.compare = $("[data-compare]", this.result);
      this.before = $(".compare-before img", this.result);
      this.after = $(".compare-after img", this.result);
      this.download = $("[data-download]", this.result);
      this.viewFull = $("[data-view-full]", this.result);

      this.wire();
      this.resetResult();
    }

    wire() {
      // 上传
      this.dz.addEventListener("click", (e) => {
        if (e.target.closest(".dz-remove")) return;
        this.input.click();
      });
      this.removeBtn.addEventListener("click", () => this.clearFile());
      this.input.addEventListener("change", () => {
        if (this.input.files && this.input.files[0]) this.setFile(this.input.files[0]);
      });
      ["dragenter", "dragover"].forEach((ev) =>
        this.dz.addEventListener(ev, (e) => { e.preventDefault(); this.dz.classList.add("drag"); }));
      ["dragleave", "drop"].forEach((ev) =>
        this.dz.addEventListener(ev, (e) => { e.preventDefault(); this.dz.classList.remove("drag"); }));
      this.dz.addEventListener("drop", (e) => {
        const f = e.dataTransfer.files && e.dataTransfer.files[0];
        if (f) this.setFile(f);
      });

      this.cta.addEventListener("click", () => this.start());
      this.pauseBtn.addEventListener("click", () => this.togglePause());
      this.viewFull.addEventListener("click", () => this.sourceUrl && openLightbox(this.sourceUrl));
      this.download.addEventListener("click", (e) => {
        // href 由 resultName 生成
        e.preventDefault();
        if (this.resultName) window.location.href = this._downloadUrl();
      });
      wireCompare(this.compare);
      this.resetProgress();
    }

    setFile(file) {
      this.file = file;
      this.dzName.textContent = file.name;
      this.dzMeta.textContent = fmtSize(file.size) + " · " + (file.type || "图片");
      if (this.thumb) {
        this.thumb.src = URL.createObjectURL(file);
        this.sourceUrl = this.thumb.src; // 本地立即预览原图
      }
      this.dzIdle.hidden = true;
      this.dzFile.hidden = false;
      this.resetResult();
      this.cta.disabled = false;
    }

    clearFile() {
      this.file = null;
      if (this.thumb) URL.revokeObjectURL(this.thumb.src);
      this.sourceUrl = null;
      this.resultName = null;
      this.paused = false;
      this.thumb && (this.thumb.src = "");
      this.dzFile.hidden = true;
      this.dzIdle.hidden = false;
      this.resetResult();
      this.cta.disabled = true;
      this.stopPolling();
    }

    /* 读取本模块参数 */
    gatherOptions() {
      const opts = { type: this.root.dataset.mod.toUpperCase() };
      $$("[data-key]", this.root).forEach((el) => {
        if (el.type === "checkbox") opts[el.dataset.key] = el.checked;
        else if (el.type === "radio") { if (el.checked) opts[el.dataset.key] = el.value; }
        else if (el.type === "number") {
          const v = parseInt(el.value, 10);
          if (!isNaN(v) && v > 0) opts[el.dataset.key] = v;
        } else if (el.tagName === "SELECT" && el.dataset.key) {
          if (el.dataset.key === "scale") opts.scale = parseInt(el.value, 10);
          else if (el.dataset.key === "ratio") { if (el.value) opts.ratio = el.value; }
          else if (el.value !== "") opts[el.dataset.key] = el.value;   // backend 等通用 select
        } else if (el.tagName === "INPUT" && el.type === "range") {
          opts[el.dataset.key] = parseInt(el.value, 10);
        }
      });
      return opts;
    }

    /* 结合本地预览 URL 生成原图后端与结果下载地址 */
    _downloadUrl() {
      const base = location.origin;
      const dir = "processed";
      return base + "/api/download?name=" + encodeURIComponent(this.resultName) + "&dir=" + dir +
        "&filename=" + encodeURIComponent(this.file ? this.file.name : "result");
    }

    start() {
      if (!this.file || this.cta.disabled) return;
      this.cta.disabled = true;
      this.paused = false;
      this.resetResult();
      this.showProgress(1, "上传中…");
      this.refreshPause();

      const fd = new FormData();
      fd.append("file", this.file);
      fd.append("options", JSON.stringify(this.gatherOptions()));

      fetch("/api/upload", { method: "POST", body: fd })
        .then(async (res) => {
          const data = await res.json();
          if (!res.ok) throw new Error(data.message || "上传失败");
          this.taskId = data.taskId;
          this.poll();
        })
        .catch((err) => this.fail(err.message));
    }

    poll() {
      this.stopPolling();
      const hit = () => {
        fetch("/api/tasks/" + this.taskId)
          .then((r) => r.json())
          .then((d) => {
            this.showProgress(d.progress, d.stage || "处理中…");
            if (d.status === "PAUSED") {
              this.paused = true;
              this.refreshPause();
              this.showProgress(d.progress, "已暂停（继续后将从当前阶段重新开始处理）");
              this.stopPolling();
              return;
            }
            if (d.status === "SUCCESS") return this.done(d);
            if (d.status === "FAILED") return this.fail(d.error || "处理失败");
            this.timer = setTimeout(hit, 700);
          })
          .catch(() => { this.timer = setTimeout(hit, 1400); });
      };
      hit();
    }

    togglePause() {
      if (!this.taskId) return;
      this.pauseBtn.disabled = true;
      const act = this.paused ? "resume" : "pause";
      fetch("/api/tasks/" + this.taskId + "/" + act, { method: "POST" })
        .then((r) => r.json())
        .then((d) => {
          this.pauseBtn.disabled = false;
          if (act === "resume") {
            this.paused = false;
            this.refreshPause();
            this.poll();
          } else {
            // 等待下一次轮询捕获 PAUSED 状态展示
            this.paused = false;
            this.refreshPause();
          }
        })
        .catch(() => { this.pauseBtn.disabled = false; });
    }

    refreshPause() {
      if (!this.pauseBtn) return;
      if (!this.taskId || this.paused === null) {
        this.pauseBtn.hidden = true;
        return;
      }
      this.pauseBtn.hidden = false;
      this.pauseBtn.textContent = this.paused ? "▶ 继续" : "⏸ 暂停";
      this.pauseBtn.classList.toggle("paused", this.paused);
    }

    done(d) {
      this.stopPolling();
      this.paused = false;
      this.pauseBtn && (this.pauseBtn.hidden = true);
      this.showProgress(100, "处理完成");
      this.resultName = this._nameOf(d.resultUrl);
      this.before.src = d.sourceUrl;
      this.after.src = d.resultUrl;
      this.before.onload = () => { this.after.loading = "eager"; };
      this.state.textContent = "处理完成";
      this.meta.textContent = d.sourceMeta || "";
      this.result.hidden = false;
      this.cta.disabled = false;
      this.download.setAttribute("href", this._downloadUrl());
    }

    fail(msg) {
      this.stopPolling();
      this.paused = false;
      this.pauseBtn && (this.pauseBtn.hidden = true);
      this.showProgress(0, msg || "处理失败");
      this.state.textContent = "失败";
      this.state.style.color = "var(--err)";
      this.cta.disabled = false;
    }

    _nameOf(url) {
      if (!url) return null;
      const m = url.match(/preview\/([^?]+)/);
      return m ? m[1] : null;
    }

    resetResult() {
      this.result.hidden = true;
      this.state.textContent = "完成";
      this.state.style.color = "";
      this.before.src = "";
      this.after.src = "";
      this.meta.textContent = "";
      this.resultName = null;
    }

    showProgress(pct, stage) {
      this.progress.hidden = false;
      this.bar.style.width = pct + "%";
      this.num.textContent = Math.round(pct) + "%";
      this.stage.textContent = stage;
    }

    resetProgress() {
      this.progress.hidden = true;
      this.bar.style.width = "0%";
      this.num.textContent = "0%";
      this.stage.textContent = "等待处理";
    }

    stopPolling() {
      if (this.timer) { clearTimeout(this.timer); this.timer = null; }
    }
  }

  /* ---------- 压缩质量/锐化滑杆联动 ---------- */
  $$('input[data-key="quality"]').forEach((r) => {
    r.addEventListener("input", () => {
      const out = r.closest(".field").querySelector("output[data-ql]");
      if (out) out.textContent = r.value + "%";
    });
  });
  $$('input[data-key="sharpen"]').forEach((r) => {
    r.addEventListener("input", () => {
      const out = r.closest(".field").querySelector("output[data-sh]");
      if (out) out.textContent = r.value;
    });
  });

  /* ---------- 高清增强：按算法适配可选倍率（LapSRN 无 x3 等） ---------- */
  // 后端引擎为单点事实来源：/api/ai/super-res/scales 返回 { 算法: [支持的倍率] }。
  // 前端据此动态渲染倍率下拉框；请求失败时退回本地默认表，保证功能不因元数据接口异常而失效。
  const DEFAULT_ALGO_SCALES = {
    "":  [2, 3, 4],
    edsr: [2, 3, 4],
    fsrcnn: [2, 3, 4],
    espcn: [2, 3, 4],
    lapsrn: [2, 4],
  };
  let ALGO_SCALES = { ...DEFAULT_ALGO_SCALES };
  function wireAlgorithmScales() {
    const mod = $('.mod[data-mod="enhance"]');
    if (!mod) return;
    const algoSel = $('[data-key="superResAlgorithm"]', mod);
    const scaleSel = $('select[data-key="scale"]', mod);
    if (!algoSel || !scaleSel) return;

    const sync = (algoScales) => {
      const allowed = algoScales[algoSel.value] || algoScales[""] || [2, 3, 4];
      Array.from(scaleSel.options).forEach((o) => {
        o.hidden = !allowed.includes(parseInt(o.value, 10));
      });
      if (!allowed.includes(parseInt(scaleSel.value, 10))) {
        scaleSel.value = String(allowed[0]);
      }
    };
    algoSel.addEventListener("change", () => sync(ALGO_SCALES));

    fetch("/api/ai/super-res/scales")
      .then((r) => { if (!r.ok) throw new Error("bad status"); return r.json(); })
      .then((data) => {
        ALGO_SCALES = { ...DEFAULT_ALGO_SCALES, ...data };
        sync(ALGO_SCALES);
      })
      .catch(() => sync(DEFAULT_ALGO_SCALES));
    sync(DEFAULT_ALGO_SCALES);
  }

  /* ---------- 初始化 ---------- */
  document.addEventListener("DOMContentLoaded", () => {
    wireTabs();
    wireAlgorithmScales();
    $$(".mod").forEach((root) => {
      root._module = new Module(root);
      // 初始禁用开始按钮
      const cta = $("[data-go]", root);
      cta.disabled = true;
    });
  });
})();