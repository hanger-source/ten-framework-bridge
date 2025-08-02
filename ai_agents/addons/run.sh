#!/bin/bash

# ai_agents/addons/run.sh
# 运行addon的脚本
# 支持指定运行类型：go, java, all

set -e

# 解析命令行参数
RUN_TYPE="go"
while [[ $# -gt 0 ]]; do
    case $1 in
        --go)
            RUN_TYPE="go"
            shift
            ;;
        --java)
            RUN_TYPE="java"
            shift
            ;;

        -h|--help)
            echo "用法: $0 [选项]"
            echo "选项:"
            echo "  --go     运行Go项目 (agent + server + designer) (默认)"
            echo "  --java   运行Java addon服务器"
            echo "  -h, --help 显示此帮助信息"
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

# 根据运行类型检查相应的构建文件
case $RUN_TYPE in
    "java")
        # 检查Java addon服务器
        if [ ! -f "$SCRIPT_DIR/server/target/ten-framework-server-1.0.0.jar" ]; then
            log_warning "addon服务器JAR文件不存在，请先运行构建脚本"
            log_info "运行: ./build.sh --java"
            exit 1
        fi
        log_success "Java构建文件检查完成"
        ;;
    "go")
        # 检查Go项目
        if [ ! -f "$PROJECT_ROOT/server/bin/api" ]; then
            log_warning "Go服务器文件不存在，请先运行构建脚本"
            log_info "运行: ./build.sh --go"
            exit 1
        fi
        if [ ! -f "$PROJECT_ROOT/agents/bin/worker" ]; then
            log_warning "agent worker文件不存在，请先运行构建脚本"
            log_info "运行: ./build.sh --go"
            exit 1
        fi
        log_success "Go构建文件检查完成"
        ;;
    "all")
        # 检查所有构建文件
        if [ ! -f "$SCRIPT_DIR/server/target/ten-framework-server-1.0.0.jar" ]; then
            log_warning "addon服务器JAR文件不存在，请先运行构建脚本"
            log_info "运行: ./build.sh --all"
            exit 1
        fi
        if [ ! -f "$PROJECT_ROOT/server/bin/api" ]; then
            log_warning "Go服务器文件不存在，请先运行构建脚本"
            log_info "运行: ./build.sh --all"
            exit 1
        fi
        if [ ! -f "$PROJECT_ROOT/agents/bin/worker" ]; then
            log_warning "agent worker文件不存在，请先运行构建脚本"
            log_info "运行: ./build.sh --all"
            exit 1
        fi
        log_success "所有构建文件检查完成"
        ;;
esac

# 运行函数
run_addon_server() {
    log_info "启动addon服务器（Java）..."
    cd "$PROJECT_ROOT"
    java -Dfile.encoding=UTF-8 -jar addons/server/target/ten-framework-server-1.0.0.jar &
    ADDON_SERVER_PID=$!
    log_success "addon服务器已启动，PID: $ADDON_SERVER_PID"
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
log_info "开始启动服务..."

case $RUN_TYPE in
    "java")
        log_info "启动Java addon服务器..."
        run_addon_server
        log_success "Java addon服务器已启动！"
        log_info "服务信息："
        log_info "- Java Addon服务器: PID $ADDON_SERVER_PID"
        ;;
    "go")
        log_info "启动Go server（包含designer）..."
        run_backend_server
        sleep 2
        run_graph_designer
        log_success "Go server已启动！"
        log_info "服务信息："
        log_info "- Go Server: PID $BACKEND_SERVER_PID"
        log_info "- 图设计器: PID $DESIGNER_PID"
        ;;
esac

log_info ""
log_info "按 Ctrl+C 停止所有服务"

# 等待
wait