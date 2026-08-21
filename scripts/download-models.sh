#!/usr/bin/env bash
# ============================================================
# 下载深度超分模型（FSRCNN / ESPCN / LapSRN / EDSR）
# 模型体积较大，未随仓库提交，请先运行本脚本再启动应用。
# 用法：bash scripts/download-models.sh
# ============================================================
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$DIR/src/main/resources/models"
mkdir -p "$OUT"

# 每个算法：名称|下载地址前缀|可选倍率（LapSRN 无 x3）
ALGOS=(
  "fsrcnn|https://github.com/Saafke/FSRCNN_Tensorflow/raw/master/models/FSRCNN_x|2 3 4"
  "espcn|https://raw.githubusercontent.com/fannymonori/TF-ESPCN/master/export/ESPCN_x|2 3 4"
  "lapsrn|https://raw.githubusercontent.com/fannymonori/TF-LapSRN/master/export/LapSRN_x|2 4"
  "edsr|https://raw.githubusercontent.com/Saafke/EDSR_Tensorflow/master/models/EDSR_x|2 3 4"
)

download() {
  local name="$1"
  local base="$2"
  local scale="$3"
  local target="$OUT/${name}_x${scale}.pb"
  local url="${base}${scale}.pb"
  if [ -s "$target" ]; then
    echo "  已存在 ${name}_x${scale}.pb，跳过"
    return
  fi
  printf "  下载 %s -> %s\n" "${name}_x${scale}.pb" "$url"
  curl -fsSL --retry 2 -o "$target" "$url" || { echo "    下载失败，删除残留"; rm -f "$target"; return 1; }
  if ! file "$target" | grep -q "data"; then
    echo "    文件异常（非模型数据），删除"; rm -f "$target"
  fi
}

for entry in "${ALGOS[@]}"; do
  IFS='|' read -r algo base scales <<< "$entry"
  echo "== $algo =="
  for s in $scales; do
    download "$algo" "$base" "$s"
  done
done

echo
echo "完成。模型目录：$OUT"
ls -1 "$OUT" | grep '\.pb$' || true