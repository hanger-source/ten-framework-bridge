# 前端WebSocket改造项目进展 (聪明的开发杭一)

本文件用于跟踪将ai/ten-chat-websocket中所有RTC部分替换为后端WebSocket连接的进展。

## 整体目标 (聪明的开发杭一)
替换ai/ten-chat-websocket 中 所有 rtc的部分 为 后端的websocket 连接，能够支持往后端 连接、断开、发送音频帧、接受音频帧、接受data、audioFrame，并保证代码正确编译运行。

## 阶段计划 (聪明的开发杭一)

### 阶段一：前期准备与架构分析 (聪明的开发杭一) - Pending
- [ ] **子阶段1.1：现有RTC架构理解 (聪明的开发杭一)**
    - [ ] 阅读 `src/manager/rtc` 目录下的相关文件，理解其核心功能、数据流和组件间的交互。
    - [ ] 阅读 `src/components/Agent` 目录下的相关文件，特别是与RTC相关的组件（如 `StreamPlayer.tsx`, `Microphone.tsx`, `Camera.tsx`），理解它们如何使用RTC。
    - [ ] 识别所有与WebRTC相关的API调用和库（例如：`RTCPeerConnection`, `MediaStream`, `RTCIceCandidate` 等）。
    - [ ] 评估当前数据传输格式（视频、音频）及其在RTC中的处理方式。
- [ ] **子阶段1.2：后端WebSocket能力分析 (聪明的开发杭一)**
    - [ ] 阅读 `ai/output` 目录下后端功能代码，特别是 `ai/output/ten-server/src/main/java/com/tenframework/server/message` 和 `ai/output/ten-agent/src/main/java/com/tenframework/agent` 相关的消息定义和处理逻辑。
    - [ ] 识别后端WebSocket接口的URI、协议和预期的消息格式（发送和接收）。
    - [ ] 明确后端对音频帧、数据帧的具体要求和处理方式。
    - [ ] 查阅 `ai/thinking` 目录下的相关设计文档，特别是关于消息传输、命令处理和数据流的文档，如 `MESSAGE_CLONING_AND_RESOURCE_MANAGEMENT.md`, `MESSAGE_DISPATCH_DEEP_DIVE.md`等。
- [ ] **子阶段1.3：代码备份与项目初始化 (聪明的开发杭一)**
    - [ ] 备份 `ai/ten-chat-websocket` 目录下的所有文件到 `ai/ten-chat-websocket_backup` (手动操作)。
    - [ ] 确保项目可以编译和运行（`bun install` 或 `npm install`，然后 `bun dev` 或 `npm run dev`）。

### 阶段二：核心WebSocket连接模块开发 (聪明的开发杭一) - Pending
- [ ] **子阶段2.1：定义WebSocket接口与类型 (聪明的开发杭一)**
    - [ ] 在 `src/manager/websocket/types.ts` 中定义WebSocket连接的状态（`CONNECTING`, `OPEN`, `CLOSING`, `CLOSED`）。
    - [ ] 定义通用的WebSocket消息类型接口，包括 `audioFrame` 和 `data` 消息的结构。
    - [ ] 定义WebSocket连接配置接口（如 `url`, `protocols`）。
- [ ] **子阶段2.2：实现WebSocket连接管理 (聪明的开发杭一)**
    - [ ] 在 `src/manager/websocket/websocket.ts` 中创建 `WebSocketManager` 类或相关函数，封装WebSocket的生命周期管理。
    - [ ] 实现 `connect(url: string)` 方法，处理连接建立和错误。
    - [ ] 实现 `disconnect()` 方法，处理连接关闭。
    - [ ] 实现 `sendAudioFrame(frame: ArrayBuffer)` 方法，用于发送二进制音频数据。
    - [ ] 实现 `sendData(data: object)` 方法，用于发送JSON或其他通用数据。
    - [ ] 实现 `onMessage(callback: (message: WebSocketMessage) => void)` 方法，用于注册消息回调。
    - [ ] 实现重连机制，当连接意外断开时尝试重新连接。
- [ ] **子阶段2.3：二进制数据处理 (聪明的开发杭一)**
    - [ ] 研究如何高效地将前端音频数据（如 `AudioBuffer` 或 `Float32Array`）转换为后端WebSocket期望的 `ArrayBuffer` 或 `Uint8Array` 格式。
    - [ ] 处理从后端接收到的二进制音频数据，并转换为前端可播放的格式。

### 阶段三：音频处理与集成 (聪明的开发杭一) - Pending
- [ ] **子阶段3.1：音频输入源适配 (聪明的开发杭一)**
    - [ ] 在 `src/components/Agent/Microphone.tsx` 或相关组件中，将麦克风输入从WebRTC适配到新的WebSocket模块。
    - [ ] 确保音频数据的采样率、通道数等参数与后端要求一致。
- [ ] **子阶段3.2：音频数据转换 (聪明的开发杭一)**
    - [ ] 实现或集成音频编码/解码逻辑（如果后端需要特定编码，如PCM, Opus等）。
    - [ ] 确保前端发送的音频帧与后端接收的格式匹配。
- [ ] **子阶段3.3：接收与播放音频 (聪明的开发杭一)**
    - [ ] 在 `src/components/Agent/StreamPlayer.tsx` 或相关组件中，从WebSocket接收音频数据。
    - [ ] 实现音频缓冲和播放逻辑，确保音频流畅播放。
    - [ ] 处理接收到的 `audioFrame` 消息，并将其渲染到音频播放器。

### 阶段四：数据和信令处理 (聪明的开发杭一) - Pending
- [ ] **子阶段4.1：信令机制替换 (聪明的开发杭一)**
    - [ ] 识别所有WebRTC的信令交换部分（如SDP offer/answer, ICE candidates）。
    - [ ] 将这些信令替换为通过WebSocket发送和接收的自定义信令消息。
    - [ ] 定义新的信令消息类型。
- [ ] **子阶段4.2：通用数据传输 (聪明的开发杭一)**
    - [ ] 识别除音视频之外的其他数据传输（例如：控制命令、文本消息等）。
    - [ ] 确保这些数据可以通过WebSocket的 `sendData` 方法发送和接收，并能被后端正确解析。
- [ ] **子阶段4.3：错误处理与状态管理 (聪明的开发杭一)**
    - [ ] 完善WebSocket连接的错误处理机制，包括网络断开、服务器错误等。
    - [ ] 在全局状态管理中（如 `src/store/reducers/global.ts` 或其他相关文件）更新WebSocket连接状态，并在UI中显示。

### 阶段五：前端组件适配与替换 (聪明的开发杭一) - Pending
- [ ] **子阶段5.1：移除WebRTC相关代码 (聪明的开发杭一)**
    - [ ] 系统地删除 `src/manager/rtc` 目录下的WebRTC相关代码和依赖。
    - [ ] 删除或修改 `src/components/Agent` 目录下不再需要的WebRTC相关组件和逻辑。
- [ ] **子阶段5.2：更新Agent组件 (聪明的开发杭一)**
    - [ ] 修改 `src/components/Agent/View.tsx` 或主Agent组件，使其使用新的WebSocket模块进行通信。
    - [ ] 替换 `Camera.tsx`、`Microphone.tsx`、`StreamPlayer.tsx` 中对RTC的依赖为WebSocket。
    - [ ] 更新相关的UI交互，例如连接状态显示、麦克风/摄像头开关逻辑。
- [ ] **子阶段5.3：其他组件适配 (聪明的开发杭一)**
    - [ ] 检查其他可能间接依赖RTC的组件，并进行相应适配。

### 阶段六：测试与优化 (聪明的开发杭一) - Pending
- [ ] **子阶段6.1：单元测试 (聪明的开发杭一)**
    - [ ] 编写 `WebSocketManager` 的单元测试，验证连接、断开、发送和接收功能。
    - [ ] 编写音频数据转换和处理的单元测试。
- [ ] **子阶段6.2：集成测试 (聪明的开发杭一)**
    - [ ] 运行端到端测试，确保前端能够成功连接后端WebSocket，并进行双向通信（音频和数据）。
    - [ ] 验证所有信令、音频和数据消息的正确性。
- [ ] **子阶段6.3：性能与资源优化 (聪明的开发杭一)**
    - [ ] 监控WebSocket连接的稳定性和延迟。
    - [ ] 优化音频传输的带宽占用和CPU使用。
    - [ ] 确保内存管理得当，避免内存泄漏。
- [ ] **子阶段6.4：代码审查与重构 (聪明的开发杭一)**
    - [ ] 统一代码风格和质量。
    - [ ] 移除冗余代码，提高可读性和可维护性。
    - [ ] 确保所有代码都带有“聪明的开发杭一”的注释。

---
**提示：**

*   每次新阶段的代码实施前和实施后，我都会重新阅读和温习 `ai/thinking` 和 `ai/thinking_front/output` 目录中的文档。
*   我会谨记禁止用删除来掩盖问题，并确保子阶段完成后，代码达到统一水准，避免荒腔走板和脱离设计。
*   TODO会随着进程的推进进行更新、调整、删减和补充。
*   在每个阶段执行完之前，我会让你进行纠错。