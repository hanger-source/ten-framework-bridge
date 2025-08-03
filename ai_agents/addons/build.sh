#!/bin/bash

# ai_agents/addons/build.sh
# 构建addon的完整脚本，替代task use AGENT=agents/addons/addon
# 支持指定构建类型：go, java, all

set -e

# 解析命令行参数
BUILD_TYPE="all"
while [[ $# -gt 0 ]]; do
    case $1 in
        --go)
            BUILD_TYPE="go"
            shift
            ;;
        --java)
            BUILD_TYPE="java"
            shift
            ;;
        --java-server)
            BUILD_TYPE="java_server"
            shift
            ;;
        --all)
            BUILD_TYPE="all"
            shift
            ;;
        --go-server)
            BUILD_TYPE="go_server"
            shift
            ;;
        -h|--help)
            echo "用法: $0 [选项]"
            echo "选项:"
            echo "  --go        只构建Go项目 (agent + server)"
            echo "  --java      构建Java addon服务器和agent"
            echo "  --java-server 只构建Java addon服务器"
            echo "  --all       构建所有项目 (默认)"
            echo "  --go-server 只构建Go服务器"
            echo "  -h, --help  显示此帮助信息"
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

log_info "开始构建addon..."
log_info "脚本目录: $SCRIPT_DIR"
log_info "项目根目录: $PROJECT_ROOT"
log_info ""
log_info "项目结构："
log_info "- Java Addon Server: $SCRIPT_DIR/server/ (Maven项目)"
log_info "- Go Agent: $PROJECT_ROOT/agents/ (agent组件)"
log_info "- Go Server: $PROJECT_ROOT/server/ (主服务器)"
log_info ""
log_info "构建类型: $BUILD_TYPE"
log_info ""

# 检查必要的目录和文件
if [ ! -d "$PROJECT_ROOT/agents" ]; then
    log_error "agents目录不存在: $PROJECT_ROOT/agents"
    exit 1
fi

if [ ! -d "$SCRIPT_DIR/addon" ]; then
    log_error "addon目录不存在: $SCRIPT_DIR/addon"
    exit 1
fi

# 检查addon目录中的必要文件
if [ ! -f "$SCRIPT_DIR/addon/manifest.json" ]; then
    log_error "manifest.json文件不存在: $SCRIPT_DIR/addon/manifest.json"
    exit 1
fi

if [ ! -f "$SCRIPT_DIR/addon/property.json" ]; then
    log_error "property.json文件不存在: $SCRIPT_DIR/addon/property.json"
    exit 1
fi

log_info "检查必要文件完成"

# 检查构建环境
log_info "检查构建环境..."
if [ "$BUILD_TYPE" = "java" ] || [ "$BUILD_TYPE" = "java_server" ] || [ "$BUILD_TYPE" = "all" ]; then
    if ! command -v mvn &> /dev/null; then
        log_error "Maven未安装，无法构建Java项目"
        exit 1
    fi
fi

if [ "$BUILD_TYPE" = "go" ] || [ "$BUILD_TYPE" = "go_server" ] || [ "$BUILD_TYPE" = "all" ]; then
    if ! command -v go &> /dev/null; then
        log_error "Go未安装，无法构建Go项目"
        exit 1
    fi
fi

log_success "构建环境检查完成"

# 创建符号链接（所有构建类型都需要）
log_info "创建manifest.json符号链接..."
if [ -L "$PROJECT_ROOT/agents/manifest.json" ]; then
    rm "$PROJECT_ROOT/agents/manifest.json"
fi
ln -sf "$SCRIPT_DIR/addon/manifest.json" "$PROJECT_ROOT/agents/"

log_info "创建property.json符号链接..."
if [ -L "$PROJECT_ROOT/agents/property.json" ]; then
    rm "$PROJECT_ROOT/agents/property.json"
fi
ln -sf "$SCRIPT_DIR/addon/property.json" "$PROJECT_ROOT/agents/"

log_success "符号链接创建完成"

# 构建函数
build_java_addon() {
    log_info "开始构建addon服务器（Java）..."
    cd "$SCRIPT_DIR/server"
    if [ -f "pom.xml" ]; then
        log_info "使用Maven构建Java addon服务器..."
        log_info "Java项目位置: $SCRIPT_DIR/server"
        mvn clean compile package -DskipTests
        if [ -f "target/ten-framework-server-1.0.0.jar" ]; then
            log_success "Java addon服务器构建完成"
        else
            log_error "Java addon服务器构建失败，未找到JAR文件"
            exit 1
        fi
    else
        log_warning "未找到pom.xml，跳过Java addon服务器构建"
    fi
}

build_go_agent() {
    log_info "开始构建agent（Go）..."
    cd "$PROJECT_ROOT/agents"
    if [ -f "scripts/install_deps_and_build.sh" ]; then
        log_info "执行agent构建脚本..."
        log_info "Agent项目位置: $PROJECT_ROOT/agents"
        ./scripts/install_deps_and_build.sh linux x64
        if [ -f "bin/main" ]; then
            mv bin/main bin/worker
            log_success "Agent构建完成，已重命名为worker"
        else
            log_error "Agent构建失败，未找到bin/main文件"
            exit 1
        fi
    else
        log_error "未找到agent构建脚本: scripts/install_deps_and_build.sh"
        exit 1
    fi
}

build_go_server() {
    log_info "开始构建server（Go）..."
    cd "$PROJECT_ROOT/server"
    if [ -f "go.mod" ]; then
        log_info "执行Go模块整理..."
        log_info "Go服务器项目位置: $PROJECT_ROOT/server"
        go mod tidy && go mod download
        log_info "构建Go服务器..."
        go build -o bin/api main.go
        if [ -f "bin/api" ]; then
            log_success "Go服务器构建完成"
        else
            log_error "Go服务器构建失败，未找到bin/api文件"
            exit 1
        fi
    else
        log_warning "未找到go.mod，跳过Go服务器构建"
    fi
}

# 根据构建类型执行构建
case $BUILD_TYPE in
    "java")
        log_info "构建Java addon服务器和agent..."
        build_java_addon
        build_go_agent
        ;;
    "java_server")
        log_info "只构建Java addon服务器..."
        build_java_addon
        ;;
    "go")
        log_info "只构建Go项目..."
        build_go_agent
        build_go_server
        ;;
    "all")
        log_info "构建所有项目..."
        build_java_addon
        build_go_agent
        build_go_server
        ;;
    "go_server")
        log_info "只构建Go服务器..."
        build_go_server
        ;;
    *)
        log_error "未知的构建类型: $BUILD_TYPE"
        exit 1
        ;;
esac

log_success "addon构建完成！"
log_info "构建结果："

case $BUILD_TYPE in
    "java")
        log_info "- Java Addon Server: $SCRIPT_DIR/server/target/ten-framework-server-1.0.0.jar"
        log_info "- Go Agent: $PROJECT_ROOT/agents/bin/worker"
        log_info ""
        log_info "项目位置说明："
        log_info "- Java项目: $SCRIPT_DIR/server/ (addon服务器)"
        log_info "- Go Agent项目: $PROJECT_ROOT/agents/ (agent组件)"
        ;;
    "java_server")
        log_info "- Java Addon Server: $SCRIPT_DIR/server/target/ten-framework-server-1.0.0.jar"
        log_info ""
        log_info "项目位置说明："
        log_info "- Java项目: $SCRIPT_DIR/server/ (addon服务器)"
        ;;
    "go")
        log_info "- Go Agent: $PROJECT_ROOT/agents/bin/worker"
        log_info "- Go Server: $PROJECT_ROOT/server/bin/api"
        log_info ""
        log_info "项目位置说明："
        log_info "- Go Agent项目: $PROJECT_ROOT/agents/ (agent组件)"
        log_info "- Go Server项目: $PROJECT_ROOT/server/ (主服务器)"
        ;;
    "all")
        log_info "- Java Addon Server: $SCRIPT_DIR/server/target/ten-framework-server-1.0.0.jar"
        log_info "- Go Agent: $PROJECT_ROOT/agents/bin/worker"
        log_info "- Go Server: $PROJECT_ROOT/server/bin/api"
        log_info ""
        log_info "项目位置说明："
        log_info "- Java项目: $SCRIPT_DIR/server/ (addon服务器)"
        log_info "- Go Agent项目: $PROJECT_ROOT/agents/ (agent组件)"
        log_info "- Go Server项目: $PROJECT_ROOT/server/ (主服务器)"
        ;;
    "go_server")
        log_info "- Go Server: $PROJECT_ROOT/server/bin/api"
        log_info ""
        log_info "项目位置说明："
        log_info "- Go Server项目: $PROJECT_ROOT/server/ (主服务器)"
        ;;
esac