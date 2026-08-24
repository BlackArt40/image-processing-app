#!/usr/bin/env python3
"""映画工坊 · 性能基线 + 系统级验收脚本（对运行中的服务进行一次性测量/验收）
用法:
  python3 scripts/bench.py single --n 20 --type RETOUCH
  python3 scripts/bench.py conc   --n 12 --workers 6 --type RETOUCH
  python3 scripts/bench.py accept            # 全部模块 E2E 验收（默认 --port 8099）

指标:
  single: 串行提交 n 个任务, 测「提交→SUCCESS」处理耗时分布(p50/p95/p99)
  conc:   并发提交 n 个任务, 测「/api/upload 提交接口延迟」分布, 反映 CallerRuns 是否阻塞提交线程
  accept: 对全部功能模块各上传一次, 断言 SUCCESS + 元数据 + 结果可下载, 作验收/回归基线
"""
import argparse, json, sys, time, urllib.request
from concurrent.futures import ThreadPoolExecutor

BASE = "http://localhost:8099"
SAMPLE = "samples/portrait_sample.jpg"

def _percentiles(xs):
    xs = sorted(xs)
    n = len(xs)
    def pct(p):
        return xs[min(n - 1, int(p * n))]
    return dict(n=n, p50=round(pct(0.50), 3), p95=round(pct(0.95), 3),
                p99=round(pct(0.99), 3), max=round(xs[-1], 3))

def _req(method, url, data=None, headers=None):
    req = urllib.request.Request(BASE + url, data=data, method=method,
                                 headers=headers or {})
    with urllib.request.urlopen(req) as r:
        return json.loads(r.read().decode())

def _raw_status(path):
    """对资源路径做 GET，返回 HTTP 状态码（用于校验结果可下载）。"""
    try:
        with urllib.request.urlopen(BASE + path, timeout=20) as r:
            r.read()
            return r.status
    except urllib.error.HTTPError as e:
        return e.code

def task_duration(tid):
    while True:
        d = _req("GET", f"/api/tasks/{tid}")
        st = d["status"]
        if st == "SUCCESS":
            return d.get("progress")
        if st == "FAILED":
            raise RuntimeError(f"task {tid} failed: {d.get('error')}")
        time.sleep(0.2)

def build_payload(opts):
    with open(SAMPLE, "rb") as f:
        body = f.read()
    boundary = "----bench-boundary"
    parts = [
        f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"p.jpg\"\r\n"
        f"Content-Type: image/jpeg\r\n\r\n".encode() + body + b"\r\n",
        (f"--{boundary}\r\nContent-Disposition: form-data; name=\"options\"\r\n\r\n"
         f"{json.dumps(opts)}\r\n").encode(),
        f"--{boundary}--\r\n".encode(),
    ]
    return b"".join(parts), f"multipart/form-data; boundary={boundary}"

def one_submit(t):
    t0 = time.time()
    data, ctype = build_payload({"type": t})
    d = _req("POST", "/api/upload", data=data,
             headers={"Content-Type": ctype})
    return time.time() - t0, d["taskId"]

def cmd_single(args):
    durs = []
    for i in range(args.n):
        _, tid = one_submit(args.type)
        t0 = time.time()
        task_duration(tid)
        durs.append(time.time() - t0)
        sys.stdout.write(f"\r  {i+1}/{args.n} done")
        sys.stdout.flush()
    print()
    print("单任务处理耗时(s) [提交→SUCCESS]")
    print(json.dumps(_percentiles(durs), indent=2))

def cmd_conc(args):
    submit_times = []
    def work(_):
        dt, _ = one_submit(args.type)
        submit_times.append(dt)
        return dt
    with ThreadPoolExecutor(max_workers=args.workers) as ex:
        list(ex.map(work, range(args.n)))
    print("并发上传: 提交接口延迟(s) [/api/upload]")
    print(json.dumps(_percentiles(submit_times), indent=2))

# 系统级验收用例：(名称, options, 期望元数据子串或 None)
ACCEPT_CASES = [
    ("高清增强・经典2x", {"type": "ENHANCE", "scale": 2, "backend": "classic"}, "经典管线 scale=2x"),
    ("AI精修・美白",      {"type": "RETOUCH", "whitening": True}, "美白"),
    ("马赛克消除",        {"type": "INPAINT"}, None),  # 无马赛克时返回「未检测到马赛克」仍属 SUCCESS
    ("格式转换・webp",   {"type": "CONVERT", "format": "webp"}, "WEBP"),
    ("滤镜・复古",        {"type": "FILTER", "filter": "sepia"}, "复古"),
    ("去雾/低光・去雾",   {"type": "DEHAZE", "dehaze": 50, "lowLight": 0}, "去雾"),
]

def cmd_accept(args):
    failed = 0
    for name, opts, expect in ACCEPT_CASES:
        data, ctype = build_payload(opts)
        d = _req("POST", "/api/upload", data=data, headers={"Content-Type": ctype})
        tid = d["taskId"]
        st = task_duration(tid)
        detail = _req("GET", f"/api/tasks/{tid}")
        ok = True
        reasons = []
        if st != 100:
            ok = False
            reasons.append(f"进度={st}")
        if expect and (not detail.get("sourceMeta") or expect not in detail["sourceMeta"]):
            ok = False
            reasons.append(f"元数据不含[{expect}]实际={detail.get('sourceMeta')}")
        url = detail.get("resultUrl")
        if url and _raw_status(url) != 200:
            ok = False
            reasons.append(f"结果不可下载({_raw_status(url)})")
        mark = "PASS" if ok else "FAIL"
        print(f"  [{mark}] {name}" + (f"  <{', '.join(reasons)}>" if reasons else ""))
        if not ok:
            failed += 1
    print("验收结论: " + ("全部通过 ✓" if failed == 0 else f"{failed} 项失败 ✗"))
    if failed:
        sys.exit(1)

def main():
    global BASE
    p = argparse.ArgumentParser()
    p.add_argument("--port", type=int, default=8099, help="服务端口（默认 8099）")
    sub = p.add_subparsers(dest="cmd", required=True)
    s = sub.add_parser("single"); s.add_argument("--n", type=int, default=20)
    s.add_argument("--type", default="RETOUCH")
    c = sub.add_parser("conc"); c.add_argument("--n", type=int, default=12)
    c.add_argument("--workers", type=int, default=6)
    c.add_argument("--type", default="RETOUCH")
    sub.add_parser("accept")
    a = p.parse_args()
    BASE = f"http://localhost:{a.port}"
    if a.cmd == "single": cmd_single(a)
    elif a.cmd == "conc": cmd_conc(a)
    else: cmd_accept(a)

if __name__ == "__main__":
    main()