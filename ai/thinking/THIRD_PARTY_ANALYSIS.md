# 第三方库分析与 Java 对等替代品评估

本文档记录了对原始 C/C++ 项目 `third_party` 目录中依赖库的分析，并为每个库提出了推荐的 Java 对等替代品。

| C/C++ 库                       | 主要功能                | 推荐的 Java 替代品                               | 备注                                                                                              |
| :----------------------------- | :---------------------- | :----------------------------------------------- | :------------------------------------------------------------------------------------------------ |
| **`ffmpeg`**                   | 多媒体编解码、处理      | **`JavaCV`** (推荐) / `JCodec`                   | `JavaCV` 是对 FFmpeg 等库的封装，功能最全，社区活跃。                                             |
| **`zlib`**                     | 数据压缩                | **Java 标准库 (`java.util.zip`)**                | Java 内置支持 ZLIB 压缩/解压，无需外部依赖。                                                      |
| **`yyjson`, `nlohmann_json`**  | JSON 解析与序列化       | **`Jackson`** (推荐) / `Gson`                    | Jackson 是 Java 生态中功能最强大、性能优异、社区最活跃的 JSON 库。                                |
| **`node`, `node-api-headers`** | Node.js C++ 扩展        | **`GraalVM`**                                    | GraalVM 提供了高性能的 Polyglot 能力，可以在 Java 中直接运行 JavaScript，是与 JS 交互的最佳选择。 |
| **`msgpack`**                  | 二进制序列化            | **`org.msgpack:msgpack-core`**                   | 官方维护的 Java 实现，保持一致性。                                                                |
| **`mbedtls`**                  | TLS/SSL 协议栈          | **`Netty` (推荐) / Bouncy Castle**               | Java 内置了 JSSE，但 Netty 集成了 OpenSSL/BoringSSL，提供更佳的性能和更现代的加密算法支持。       |
| **`libwebsockets`**            | WebSocket 协议          | **`Netty` (推荐) / `Java-WebSocket`**            | Netty, Jetty, Undertow 等都提供高性能的 WebSocket 支持。Netty 最为灵活和强大。                    |
| **`libuv`**                    | 异步 I/O                | **`Netty`**                                      | Netty 是 Java 中异步事件驱动网络应用框架的事实标准，其设计思想和功能集与 libuv 高度对应。         |
| **`googletest`, `googlemock`** | 单元测试与 Mocking      | **`JUnit 5` + `Mockito`**                        | Java 社区进行单元测试和 Mock 测试的黄金组合。                                                     |
| **`curl`**                     | HTTP/网络客户端         | **`OkHttp` (推荐) / `java.net.http.HttpClient`** | OkHttp 是一个现代、高效、易用的 HTTP 客户端，广泛应用于 Android 和服务器端。                      |
| **`clingo`, `clingo-sys`**     | 回答集规划 (ASP) 求解器 | **`OptaPlanner` (推荐) / `Choco Solver`**        | OptaPlanner 是一个成熟的开源约束满足规划器，可以解决类似的调度和规划问题，拥有强大的社区支持。    |

---

## 总结

- **核心网络层**: **Netty** 将是迁移的核心。它一个库就能同时替代 `libuv` (核心 I/O 模型), `libwebsockets` (WebSocket), 和 `mbedtls` (SSL/TLS)。
- **HTTP 客户端**: **OkHttp** 是最佳选择。
- **JSON/MsgPack**: **Jackson** 和 **`msgpack-core`** 将分别处理 JSON 和 MsgPack 序列化。
- **测试**: **JUnit 5 + Mockito**。
- **专业领域**: 对于 `ffmpeg` 和 `clingo`，需要依赖更专门的 Java 库 `JavaCV` 和 `OptaPlanner`。

下一步是在 `pom.xml` 文件中添加这些选定的依赖项。
