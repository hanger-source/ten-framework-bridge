#!/bin/bash

# ai_agents/addons/debug.sh
# 运行addon的调试版本脚本，支持远程调试
# 支持指定运行类型：go, java, all

set -e

# 解析命令行参数
RUN_TYPE="java"
DEBUG_PORT=${DEBUG_PORT:-5005}
while [[ $# -gt 0 ]]; do
    case $1 in
        --java)
            RUN_TYPE="java"
            shift
            ;;

        --debug-port)
            DEBUG_PORT="$2"
            shift 2
            ;;
        -h|--help)
            echo "用法: $0 [选项]"
            echo "选项:"
            echo "  --java        运行Java addon服务器 (调试模式) (默认)"
            echo "  --debug-port PORT 设置调试端口 (默认: 5005)"
            echo "  -h, --help    显示此帮助信息"
            exit 0
            ;;
        *)
            echo "未知选项: $1"
            echo "使用 --help 查看帮助信息"
            exit 1
            ;;
    esac
done

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 日志函数
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 获取脚本所在目录的绝对路径
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# 在容器中，项目根目录是 /app，所以需要找到正确的项目根目录
if [ "$(basename "$SCRIPT_DIR")" = "addons" ]; then
    PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
else
    # 如果在容器外运行，需要找到正确的项目根目录
    PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
fi

# 默认调试端口
DEBUG_PORT=${DEBUG_PORT:-5005}

log_info "使用调试端口: $DEBUG_PORT"

# 检查.env文件
if [ ! -f "$PROJECT_ROOT/.env" ]; then
    log_error ".env文件不存在: $PROJECT_ROOT/.env"
    log_info "请确保.env文件存在并包含必要的环境变量"
    exit 1
fi

# 加载环境变量
log_info "加载环境变量..."
source "$PROJECT_ROOT/.env"

# 检查构建文件
log_info "检查构建文件..."
log_info "运行类型: $RUN_TYPE"

# 检查Java addon服务器构建文件
if [ ! -f "$SCRIPT_DIR/server/target/ten-framework-server-1.0.0.jar" ]; then
    log_warning "addon服务器JAR文件不存在，请先运行构建脚本"
    log_info "运行: ./build.sh --java"
    exit 1
fi
log_success "Java构建文件检查完成"

# 运行函数
run_addon_server_debug() {
    log_info "启动addon服务器（Java）调试模式..."
    cd "$PROJECT_ROOT"
    java -Dfile.encoding=UTF-8 -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:$DEBUG_PORT -jar addons/server/target/ten-framework-server-1.0.0.jar &
    ADDON_SERVER_PID=$!
    log_success "addon服务器已启动（调试模式），PID: $ADDON_SERVER_PID，调试端口: $DEBUG_PORT"
}

run_backend_server() {
    log_info "启动后端HTTP服务器（Go）..."
    cd "$PROJECT_ROOT"
    "$PROJECT_ROOT/server/bin/api" &
    BACKEND_SERVER_PID=$!
    log_success "后端服务器已启动，PID: $BACKEND_SERVER_PID"
}



run_graph_designer() {
    log_info "启动图设计器服务器..."
    cd "$PROJECT_ROOT/agents"
    tman designer &
    DESIGNER_PID=$!
    log_success "图设计器服务器已启动，PID: $DESIGNER_PID"
}

# 信号处理函数
cleanup() {
    log_info "正在停止所有服务..."

    if [ ! -z "$ADDON_SERVER_PID" ]; then
        kill $ADDON_SERVER_PID 2>/dev/null || true
        log_info "已停止addon服务器"
    fi

    if [ ! -z "$BACKEND_SERVER_PID" ]; then
        kill $BACKEND_SERVER_PID 2>/dev/null || true
        log_info "已停止后端服务器"
    fi

    if [ ! -z "$DESIGNER_PID" ]; then
        kill $DESIGNER_PID 2>/dev/null || true
        log_info "已停止图设计器服务器"
    fi

    log_success "所有服务已停止"
    exit 0
}

# 设置信号处理
trap cleanup SIGINT SIGTERM

# 根据运行类型启动相应的服务
log_info "开始启动服务（调试模式）..."

log_info "启动Java addon服务器（调试模式）..."
run_addon_server_debug
log_success "Java addon服务器已启动（调试模式）！"
log_info "服务信息："
log_info "- Addon服务器（调试）: PID $ADDON_SERVER_PID，调试端口 $DEBUG_PORT"
log_info ""
log_info "调试信息："
log_info "- 可以通过 localhost:$DEBUG_PORT 连接到Java调试器"
log_info "- 在IDE中配置远程调试连接到 localhost:$DEBUG_PORT"

log_info ""
log_info "按 Ctrl+C 停止所有服务"

# 等待
wait