#!/bin/bash
# ============================================
# TuGraph v4.5.2 离线安装脚本 (CentOS 7 x86_64)
# ============================================
# 用法:
#   方式1 (推荐): chmod +x install.sh && sudo ./install.sh
#   方式2: sudo bash install.sh
#
# 此脚本需要与分片文件及依赖 RPM 放在同一目录下
# 如果存在分片文件 (*.partaa), 自动合并后再安装

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# 如果存在分片, 先合并为完整 tar.gz
if ls tugraph-offline-centos7-v4.5.2.tar.gz.part* 1>/dev/null 2>&1; then
    echo "检测到分片文件, 正在合并..."
    cat tugraph-offline-centos7-v4.5.2.tar.gz.part* > tugraph-offline-centos7-v4.5.2.tar.gz
    echo "合并完成 ($(du -h tugraph-offline-centos7-v4.5.2.tar.gz | cut -f1))"
fi

# 从 tar.gz 中解压 TuGraph RPM
if [ -f tugraph-offline-centos7-v4.5.2.tar.gz ]; then
    echo "解压 TuGraph RPM..."
    tar xzf tugraph-offline-centos7-v4.5.2.tar.gz tugraph-offline/tugraph-4.5.2-1.el7.x86_64.rpm
    mv tugraph-offline/tugraph-4.5.2-1.el7.x86_64.rpm .
    rm -rf tugraph-offline
fi

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  TuGraph v4.5.2 离线安装${NC}"
echo -e "${GREEN}  目标系统: CentOS 7 x86_64${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# 1. 检查 root 权限
if [ "$(id -u)" != "0" ]; then
    echo -e "${RED}[错误] 请使用 root 权限运行此脚本: sudo bash install.sh${NC}"
    exit 1
fi

# 2. 检查 RPM 包是否存在
echo -e "${YELLOW}[1/5] 检查安装包...${NC}"
REQUIRED_RPMS=(
    "libgcc-4.8.5-44.el7.x86_64.rpm"
    "libgomp-4.8.5-44.el7.x86_64.rpm"
    "libgfortran-4.8.5-44.el7.x86_64.rpm"
    "tugraph-4.5.2-1.el7.x86_64.rpm"
)

for rpm in "${REQUIRED_RPMS[@]}"; do
    if [ ! -f "$rpm" ]; then
        echo -e "${RED}[错误] 缺少文件: $rpm${NC}"
        echo "请将所有 RPM 包放在同一目录下再运行此脚本"
        exit 1
    fi
done
echo -e "${GREEN}  所有安装包就绪${NC}"

# 3. 安装依赖 RPM
echo -e "${YELLOW}[2/5] 安装系统依赖...${NC}"

# 检查是否已经安装
check_and_install() {
    local rpm_file=$1
    local pkg_name=$(rpm -qp --qf '%{NAME}' "$rpm_file" 2>/dev/null || echo "$rpm_file")
    
    if rpm -q "$pkg_name" &>/dev/null; then
        echo "  $pkg_name 已安装，跳过"
        return 0
    fi
    
    echo "  安装 $rpm_file ..."
    rpm -ivh "$rpm_file" || {
        echo -e "${YELLOW}  警告: $rpm_file 安装失败, 尝试强制安装...${NC}"
        rpm -ivh --nodeps "$rpm_file" || {
            echo -e "${RED}  错误: $rpm_file 安装失败${NC}"
            return 1
        }
    }
}

check_and_install "libgcc-4.8.5-44.el7.x86_64.rpm"
check_and_install "libgomp-4.8.5-44.el7.x86_64.rpm"
check_and_install "libgfortran-4.8.5-44.el7.x86_64.rpm"
echo -e "${GREEN}  依赖安装完成${NC}"

# 4. 安装 TuGraph
# 注意: 必须使用 --nodeps, 因为 RPM 的 Requires 中声明了
# liblgraph.so 和 libvsag.so, 但这两个库是本 RPM 自带并安装到
# /usr/local/lib64/lgraph/ 下的, 并非独立 RPM 包, 不需要外部满足。
echo -e "${YELLOW}[3/5] 安装 TuGraph...${NC}"

# 检查是否已安装
RPM_NAME="tugraph-4.5.2-1.el7.x86_64.rpm"
if rpm -q "tugraph" &>/dev/null; then
    echo -e "${YELLOW}  TuGraph 已安装, 将先卸载旧版本...${NC}"
    rpm -e tugraph 2>/dev/null || true
fi

echo "  正在安装 $RPM_NAME (约 100MB, 请稍候)..."
rpm -ivh --nodeps "$RPM_NAME"
echo -e "${GREEN}  TuGraph 安装完成${NC}"

# 5. 配置环境变量
echo -e "${YELLOW}[4/5] 配置环境变量...${NC}"

PROFILE_FILE="/etc/profile.d/tugraph.sh"
cat > "$PROFILE_FILE" << 'EOF'
# TuGraph 环境变量
export LD_LIBRARY_PATH=/usr/local/lib64/lgraph:/usr/local/lib64:$LD_LIBRARY_PATH
export PYTHONPATH=/usr/local/lib64/lgraph:/usr/local/lib64:$PYTHONPATH
export PATH=/usr/local/bin:$PATH
EOF

chmod 644 "$PROFILE_FILE"
source "$PROFILE_FILE"
echo -e "${GREEN}  环境变量已写入 $PROFILE_FILE${NC}"

# 6. 创建运行时目录
echo -e "${YELLOW}[5/5] 创建运行时目录...${NC}"

mkdir -p /var/lib/lgraph/data
mkdir -p /var/log/lgraph_log

echo -e "${GREEN}  目录创建完成${NC}"

# 7. 验证安装
echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  安装完成!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# 验证二进制文件
if command -v lgraph_server &>/dev/null; then
    echo -e "${GREEN}[OK] lgraph_server 可用${NC}"
    lgraph_server --version 2>&1 || true
else
    echo -e "${YELLOW}[警告] lgraph_server 未找到, 请检查 LD_LIBRARY_PATH${NC}"
fi

echo ""
echo "--- 使用说明 ---"
echo "1. 配置文件: /usr/local/etc/lgraph.json"
echo "   你可以编辑该文件修改端口、数据目录等"
echo ""
echo "2. 启动 TuGraph 服务:"
echo "   source /etc/profile.d/tugraph.sh"
echo "   lgraph_server -c /usr/local/etc/lgraph.json -d start"
echo ""
echo "3. 停止 TuGraph 服务:"
echo "   lgraph_server -c /usr/local/etc/lgraph.json -d stop"
echo ""
echo "4. 命令行客户端:"
echo "   lgraph_cli"
echo ""
echo "5. Web 界面 (如果启用):"
echo "   默认端口 7070, 浏览器访问 http://<服务器IP>:7070"
echo ""
echo "6. 查看日志:"
echo "   tail -f /var/log/lgraph_log/lgraph_server.log"
echo ""
echo "--- 默认端口 ---"
echo "  REST API: 7070"
echo "  RPC:      9090"
echo "  Bolt:     7687"
