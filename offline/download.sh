#!/bin/bash
# ============================================
# 下载 TuGraph v4.5.2 离线部署所需的 RPM 包
# ============================================
# 此脚本在有网络的机器上运行，下载所有需要的 RPM 到当前目录。
# 然后将整个目录打包传输到目标离线 CentOS 7 机器。

set -euo pipefail

OUTPUT_DIR="${1:-./tugraph-offline}"
mkdir -p "$OUTPUT_DIR"

GITHUB_RELEASE="https://github.com/TuGraph-family/tugraph-db/releases/download/v4.5.2"
CENTOS_MIRROR="https://vault.centos.org/7.9.2009/os/x86_64/Packages"

echo "========================================="
echo "  TuGraph v4.5.2 离线包下载"
echo "========================================="
echo ""

# 1. TuGraph RPM
echo "[1/5] 下载 TuGraph RPM (约 100 MB)..."
TGRAPH_RPM="tugraph-4.5.2-1.el7.x86_64.rpm"
if [ -f "$OUTPUT_DIR/$TGRAPH_RPM" ]; then
    echo "  已存在，跳过"
else
    curl -L -o "$OUTPUT_DIR/$TGRAPH_RPM" "$GITHUB_RELEASE/$TGRAPH_RPM"
    echo "  完成 ($(ls -lh $OUTPUT_DIR/$TGRAPH_RPM | awk '{print $5}'))"
fi

# 2. libgcc
echo "[2/5] 下载 libgcc..."
LIBGCC="libgcc-4.8.5-44.el7.x86_64.rpm"
if [ -f "$OUTPUT_DIR/$LIBGCC" ]; then
    echo "  已存在，跳过"
else
    curl -L -o "$OUTPUT_DIR/$LIBGCC" "$CENTOS_MIRROR/$LIBGCC"
    echo "  完成"
fi

# 3. libgomp
echo "[3/5] 下载 libgomp..."
LIBGOMP="libgomp-4.8.5-44.el7.x86_64.rpm"
if [ -f "$OUTPUT_DIR/$LIBGOMP" ]; then
    echo "  已存在，跳过"
else
    curl -L -o "$OUTPUT_DIR/$LIBGOMP" "$CENTOS_MIRROR/$LIBGOMP"
    echo "  完成"
fi

# 4. libgfortran
echo "[4/5] 下载 libgfortran..."
LIBGFORTRAN="libgfortran-4.8.5-44.el7.x86_64.rpm"
if [ -f "$OUTPUT_DIR/$LIBGFORTRAN" ]; then
    echo "  已存在，跳过"
else
    curl -L -o "$OUTPUT_DIR/$LIBGFORTRAN" "$CENTOS_MIRROR/$LIBGFORTRAN"
    echo "  完成"
fi

# 5. 复制 install.sh 和 README.txt
echo "[5/5] 复制安装脚本和文档..."
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cp "$SCRIPT_DIR/install.sh" "$OUTPUT_DIR/"
cp "$SCRIPT_DIR/README.txt" "$OUTPUT_DIR/"
chmod +x "$OUTPUT_DIR/install.sh"

echo ""
echo "========================================="
echo "  下载完成！"
echo "========================================="
echo ""
echo "离线包位置: $OUTPUT_DIR/"
ls -lh "$OUTPUT_DIR"/*.rpm "$OUTPUT_DIR"/install.sh "$OUTPUT_DIR"/README.txt
echo ""
echo "传输到目标机器后执行:"
echo "  cd tugraph-offline && sudo ./install.sh"
