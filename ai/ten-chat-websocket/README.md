## Playground

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](../LICENSE)
[![Node.js Version](https://img.shields.io/badge/node-%3E%3D20-brightgreen)](package.json)
[![TypeScript](https://img.shields.io=badge/TypeScript-5.0-blue)](tsconfig.json)
[![React](https://img.shields.io/badge/React-18-blue)](package.json)
[![Next.js 15](https://img.shields.io/badge/Next.js-15-black)](package.json)
[![shadcn/ui](https://img.shields.io/badge/UI-shadcn%2Fui-black)](https://ui.shadcn.com)
[![pnpm](https://img.shields.io/badge/pnpm-9.12.3-blue)](package.json)

Local playground for Ten Agent.

## Local Development

### Prerequisites

- Node.js >= 20
- [pnpm 9.12.3](https://pnpm.io/installation)

### Run local Backend WebSocket Server

聪明的开发杭一：为了运行前端应用并使用新的 WebSocket 连接，你需要先启动后端 WebSocket 服务器。

请按照 `../output/ten-server/README.md` 中的说明启动后端服务。如果没有该文件，请参考以下基本步骤：

1.  进入后端项目目录：
    ```bash
    cd ../output/ten-server
    ```
2.  构建并运行 Spring Boot 应用（确保你已安装 Java 和 Maven）：
    ```bash
    # 使用 Maven 运行
    ./mvnw spring-boot:run
    # 或者先构建再运行
    # ./mvnw clean package
    # java -jar target/ten-server-<version>.jar
    ```

### Install frontend dependencies

```bash
# cd ./ten-chat-websocket (如果你不在当前目录)
# install dependencies
pnpm install
```

### Run Frontend UI

```bash
# run
pnpm dev
```
