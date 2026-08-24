#!/usr/bin/env python3
"""映画工坊 · 性能基线脚本（一次性测量工具）
用法:
  python3 scripts/bench.py single --n 20 --type RETOUCH
  python3 scripts/bench.py conc   --n 12 --workers 6 --type RETOUCH

指标:
  single: 串行提交 n 个任务, 测「提交→SUCCESS」处理耗时分布(p50/p95/p99)
  conc:   并发提交 n 个任务, 测「/api/upload 提交接口延迟」分布, 反映 CallerRuns 是否阻塞提交线程
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

def task_duration(tid):
    while True:
        d = _req("GET", f"/api/tasks/{tid}")
        st = d["status"]
        if st == "SUCCESS":
            return d.get("progress")
        if st == "FAILED":
            raise RuntimeError(f"task {tid} failed: {d.get('error')}")
        time.sleep(0.2)

def submit_payload(t):
    with open(SAMPLE, "rb") as f:
        body = f.read()
    boundary = "----bench-boundary"
    opts = {"type": t}
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
    data, ctype = submit_payload(t)
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

def main():
    p = argparse.ArgumentParser()
    sub = p.add_subparsers(dest="cmd", required=True)
    s = sub.add_parser("single"); s.add_argument("--n", type=int, default=20)
    s.add_argument("--type", default="RETOUCH")
    c = sub.add_parser("conc"); c.add_argument("--n", type=int, default=12)
    c.add_argument("--workers", type=int, default=6)
    c.add_argument("--type", default="RETOUCH")
    a = p.parse_args()
    if a.cmd == "single": cmd_single(a)
    else: cmd_conc(a)

if __name__ == "__main__":
    main()