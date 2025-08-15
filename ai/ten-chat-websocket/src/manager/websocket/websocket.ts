import { Message, Data, Command, CommandResult, Location, MessageType, StartGraphCommand, StopGraphCommand, CommandType, AudioFrame } from '@/types/websocket';
import { encode, decode, ExtensionCodec, ExtData } from '@msgpack/msgpack';
// import { v4 as uuidv4 } from 'uuid'; // Import uuid to generate unique ids
import { MESSAGE_CONSTANTS } from '@/common/constant'; // Import MESSAGE_CONSTANTS
import { WebSocketConnectionState } from '@/types/websocket'; // Ensure this points to correct WebSocketConnectionState

// TEN框架自定义MsgPack扩展类型码
const TEN_MSGPACK_EXT_TYPE_MSG = -1; // 恢复自定义扩展类型码

// 创建扩展编解码器
const extensionCodec = new ExtensionCodec(); // 恢复扩展编解码器实例

// 注册自定义扩展类型
extensionCodec.register({
    type: TEN_MSGPACK_EXT_TYPE_MSG,
    encode: (input: unknown) => {
        // 检查是否是Message类型的对象
        if (input && typeof input === 'object' && 'type' in input && 'name' in input) {
            // 将Message对象编码为MsgPack字节数组
            const innerEncoded = encode(input);
            return innerEncoded;
        }
        return null;
    },
    decode: (data: Uint8Array) => {
        // 解码内部MsgPack数据为Message对象
        return decode(data) as Message;
    }
}); // 恢复扩展编解码器注册逻辑

export interface WebSocketMessage {
    type: string;
    name: string;
    data?: any;
    properties?: Record<string, any>;
    timestamp?: number;
}

export class WebSocketManager {
    private ws: WebSocket | null = null;
    private connectionState: WebSocketConnectionState = WebSocketConnectionState.CLOSED;
    private reconnectAttempts = 0;
    private maxReconnectAttempts = 5;
    private reconnectDelay = 1000;
    private messageHandlers: Map<string, Array<(message: Message) => void>> = new Map();
    private connectionStateHandlers: ((state: WebSocketConnectionState) => void)[] = [];
    private commandSendHandlers: ((commandName: CommandType, properties: Record<string, any>) => void)[] = []; // New: Handlers for command send events
    private _isManualDisconnect: boolean = false; // New: Flag to indicate manual disconnect

    constructor(private url: string) { }

    // 连接 WebSocket
    public async connect(): Promise<void> {
        return new Promise((resolve, reject) => {
            if (this.connectionState === WebSocketConnectionState.OPEN) {
                resolve();
                return;
            }

            this.setConnectionState(WebSocketConnectionState.CONNECTING);

            try {
                this.ws = new WebSocket(this.url);
                this.ws.binaryType = "arraybuffer"; // 告诉浏览器，接收到的二进制数据请以 ArrayBuffer 形式提供

                this.ws.onopen = () => {
                    console.log('WebSocket 连接已建立');
                    this.setConnectionState(WebSocketConnectionState.OPEN);
                    this.reconnectAttempts = 0;
                    resolve();
                };

                this.ws.onmessage = (event) => {
                    this.handleMessage(event);
                };

                this.ws.onclose = (event) => {
                    console.log('WebSocket 连接已关闭:', event.code, event.reason);
                    console.log("WebSocketManager: onclose triggered. Calling handleReconnect.");
                    this.setConnectionState(WebSocketConnectionState.CLOSED);
                    this.handleReconnect();
                };

                this.ws.onerror = (error) => {
                    console.error('WebSocket 连接错误:', error);
                    console.error('WebSocket 错误事件:', error); // Added detailed log
                    this.setConnectionState(WebSocketConnectionState.CLOSED);
                    reject(error);
                };

            } catch (error) {
                console.error('创建 WebSocket 连接失败:', error);
                console.error('创建 WebSocket 实例时发生错误:', error); // Added detailed log
                this.setConnectionState(WebSocketConnectionState.CLOSED);
                reject(error);
            }
        });
    }

    // 断开连接
    public disconnect(): void {
        if (this.ws) {
            this.setConnectionState(WebSocketConnectionState.CLOSING);
            this.ws.close();
            this.ws = null;
        }
    }

    // 设置手动断开标志
    public setManualDisconnectFlag(isManual: boolean): void {
        this._isManualDisconnect = isManual;
        console.log(`WebSocketManager: Manual disconnect flag set to ${isManual}`);
    }

    // 发送消息
    public sendMessage(message: Message): void {
        if (this.connectionState !== WebSocketConnectionState.OPEN || !this.ws) {
            console.error('WebSocket 未连接，无法发送消息');
            return;
        }

        try {
            // 使用扩展编解码器编码消息
            const encodedMessage = this.encodeMessage(message);
            this.ws.send(encodedMessage);
            console.log('发送消息:', message);
        } catch (error) {
            console.error('发送消息失败:', error);
        }
    }

    // 发送文本数据
    public sendTextData(name: string, text: string, srcLoc: Location, destLocs: Location[] = []): void {
        const dataMessage: Data = {
            id: this.generateMessageId(),
            type: MessageType.DATA,
            name: name,
            src_loc: srcLoc,
            dest_locs: destLocs,
            data: new Uint8Array(0), // data 字段为空的 Uint8Array
            content_type: 'text/plain',
            encoding: 'UTF-8',
            timestamp: Date.now(),
            properties: { text: text, is_final: true }, // 文本内容放在 properties 中
        };
        this.sendMessage(dataMessage);
    }

    // 发送 JSON 数据
    public sendJsonData(name: string, jsonData: any, srcLoc: Location, destLocs: Location[] = []): void {
        const dataMessage: Data = {
            id: this.generateMessageId(),
            type: MessageType.DATA,
            name: name, // name 字段直接放在这里
            src_loc: srcLoc,
            dest_locs: destLocs,
            data: new TextEncoder().encode(JSON.stringify(jsonData)),
            content_type: 'application/json',
            encoding: 'UTF-8',
            timestamp: Date.now(),
        };
        this.sendMessage(dataMessage);
    }

    // 发送音频帧数据
    public sendAudioFrame(
        audioData: Uint8Array,
        srcLoc: Location,
        destLocs: Location[] = [],
        name: string = "audio_frame",
        sampleRate: number = 48000,
        channels: number = 1,
        bitsPerSample: number = 16,
        isEof: boolean = false,
    ): void {
        const audioFrameMessage: AudioFrame = {
            id: this.generateMessageId(),
            type: MessageType.AUDIO_FRAME,
            name: name,
            src_loc: srcLoc,
            dest_locs: destLocs,
            buf: audioData,
            is_eof: isEof,
            sample_rate: sampleRate,
            number_of_channel: channels,
            bits_per_sample: bitsPerSample,
            format: "pcm", // Assuming PCM format
            frame_timestamp: Date.now(), // Add frame_timestamp
            timestamp: Date.now(),
        };
        this.sendMessage(audioFrameMessage);
    }

    // 发送命令
    public sendCommand(
        commandName: CommandType,
        srcLoc: Location,
        destLocs: Location[] = [],
        properties: Record<string, any> = {},
        commandId?: string // Change type to string
    ): void {
        const baseCommand: Command = {
            id: this.generateMessageId(),
            type: MessageType.CMD, // 初始设置为 CMD
            name: commandName, // 命令名称
            src_loc: srcLoc,
            dest_locs: destLocs,
            cmd_id: commandId !== undefined ? commandId : this.generateCommandId(), // Use provided commandId or generate new one
            properties: properties,
            timestamp: Date.now(),
        };

        let finalCommand: Message;

        switch (commandName) {
            case CommandType.START_GRAPH:
                finalCommand = {
                    ...baseCommand,
                    type: MessageType.CMD_START_GRAPH, // 覆盖为 CMD_START_GRAPH
                    long_running_mode: properties.long_running_mode,
                    predefined_graph_name: properties.predefined_graph_name || properties[MESSAGE_CONSTANTS.PROPERTY_CLIENT_GRAPH_NAME],
                    extension_groups_info: properties.extension_groups_info,
                    extensions_info: properties.extensions_info,
                    graph_json: properties.graph_json,
                } as StartGraphCommand; // 转换为 StartGraphCommand
                break;
            case CommandType.STOP_GRAPH:
                finalCommand = {
                    ...baseCommand,
                    type: MessageType.CMD_STOP_GRAPH, // 覆盖为 CMD_STOP_GRAPH
                    location_uri: properties.location_uri || properties[MESSAGE_CONSTANTS.PROPERTY_CLIENT_LOCATION_URI],
                } as StopGraphCommand; // 转换为 StopGraphCommand
                break;
            default:
                finalCommand = baseCommand;
                break;
        }

        this.sendMessage(finalCommand);
        // New: Notify handlers that a command has been sent
        this.commandSendHandlers.forEach(handler => handler(commandName, properties));
    }

    // 注册命令发送处理器
    public onCommandSend(handler: (commandName: CommandType, properties: Record<string, any>) => void): () => void {
        this.commandSendHandlers.push(handler);
        return () => {
            const index = this.commandSendHandlers.indexOf(handler);
            if (index > -1) {
                this.commandSendHandlers.splice(index, 1);
                console.log(`Unregistered command send handler. Remaining handlers: ${this.commandSendHandlers.length}`);
            }
        };
    }

    // 取消注册命令发送处理器
    public offCommandSend(handler: (commandName: CommandType, properties: Record<string, any>) => void): void {
        const index = this.commandSendHandlers.indexOf(handler);
        if (index > -1) {
            this.commandSendHandlers.splice(index, 1);
            console.log(`Unregistered command send handler. Remaining handlers: ${this.commandSendHandlers.length}`);
        }
    }

    // 注册消息处理器
    public onMessage(type: string, handler: (message: Message) => void): () => void {
        if (!this.messageHandlers.has(type)) {
            this.messageHandlers.set(type, []);
        }
        this.messageHandlers.get(type)?.push(handler);
        // console.log(`Registered handler for type: ${type}. Total handlers: ${this.messageHandlers.get(type)?.length}`);

        // 返回一个取消订阅函数
        return () => {
            const handlers = this.messageHandlers.get(type);
            if (handlers) {
                const index = handlers.indexOf(handler);
                if (index > -1) {
                    handlers.splice(index, 1);
                    // console.log(`Unregistered handler for type: ${type}. Remaining handlers: ${handlers.length}`);
                }
            }
        };
    }

    // 取消注册消息处理器
    public offMessage(type: string, handler: (message: Message) => void): void {
        const handlers = this.messageHandlers.get(type);
        if (handlers) {
            const index = handlers.indexOf(handler);
            if (index > -1) {
                handlers.splice(index, 1);
                // console.log(`Unregistered handler for type: ${type}. Remaining handlers: ${handlers.length}`);
            }
        }
    }

    // 注册连接状态处理器
    public onConnectionStateChange(handler: (state: WebSocketConnectionState) => void): () => void {
        this.connectionStateHandlers.push(handler);
        return () => {
            const index = this.connectionStateHandlers.indexOf(handler);
            if (index > -1) {
                this.connectionStateHandlers.splice(index, 1);
                // console.log(`Unregistered connection state handler. Remaining handlers: ${this.connectionStateHandlers.length}`);
            }
        };
    }

    public offConnectionStateChange(handler: (state: WebSocketConnectionState) => void): void {
        const index = this.connectionStateHandlers.indexOf(handler);
        if (index > -1) {
            this.connectionStateHandlers.splice(index, 1);
            // console.log(`Unregistered connection state handler. Remaining handlers: ${this.connectionStateHandlers.length}`);
        }
    }

    // 获取连接状态
    public getConnectionState(): WebSocketConnectionState {
        return this.connectionState;
    }

    // 处理接收到的消息
    private handleMessage(event: MessageEvent): void { // 改回同步方法
        console.log('Received raw message event:', event);
        console.log('Received raw message data type:', typeof event.data);
        
        // 确保 data 是 ArrayBuffer 类型，因为 binaryType 已设置为 "arraybuffer"
        const arrayBufferData: ArrayBuffer = event.data as ArrayBuffer; 
        console.log('Received ArrayBuffer size:', arrayBufferData.byteLength);

        try {
            const message = this.decodeMessage(arrayBufferData);
            console.log('收到消息 (解码后):', message);

            const handlers = this.messageHandlers.get(message.type);
            if (handlers && handlers.length > 0) {
                handlers.forEach(handler => handler(message)); // 遍历所有处理器
            } else {
                console.warn('未找到消息处理器或处理程序列表为空:', message.type);
            }
        } catch (error) {
            console.error('处理消息失败:', error);
            console.error('错误发生时尝试解码的数据 (前20字节):', new Uint8Array(arrayBufferData).slice(0, 20));
            if (error instanceof RangeError) {
                console.error('RangeError: 数据可能被截断或格式不正确。');
            }
            // 确保错误被捕获和打印
        }
    }

    // 设置连接状态
    private setConnectionState(state: WebSocketConnectionState): void {
        console.log(`WebSocketManager: Connection state changed from ${this.connectionState} to ${state}. Notifying ${this.connectionStateHandlers.length} handlers.`);
        this.connectionState = state;
        this.connectionStateHandlers.forEach(handler => handler(state));
    }

    // 处理重连
    private handleReconnect(): void {
        console.log(`WebSocketManager: handleReconnect called. _isManualDisconnect: ${this._isManualDisconnect}, connectionState: ${this.connectionState}`);
        if (this._isManualDisconnect) {
            console.log("WebSocketManager: Manual disconnect detected, skipping reconnect.");
            this._isManualDisconnect = false; // Reset flag
            return; // Skip reconnect logic
        }

        if (this.reconnectAttempts < this.maxReconnectAttempts) {
            this.reconnectAttempts++;
            console.log(`尝试重连 (${this.reconnectAttempts}/${this.maxReconnectAttempts})...`);

            setTimeout(() => {
                this.connect().catch(error => {
                    console.error('重连失败:', error);
                });
            }, this.reconnectDelay * this.reconnectAttempts);
        } else {
            console.error('达到最大重连次数，停止重连');
        }
    }

    // 生成命令 ID
    private generateCommandId(): string { // Change return type to string
        return Date.now().toString() + Math.random().toString().substring(2, 8); // Generate a string ID
    }

    // 生成消息 ID (使用 Date.now() + Math.random())
    private generateMessageId(): string {
        return Date.now().toString() + Math.random().toString().substring(2, 8);
    }

    // 编码消息为 TEN 自定义 MsgPack 格式
    private encodeMessage(message: Message): ArrayBuffer {
        // 使用扩展编解码器尝试编码
        const extData = extensionCodec.tryToEncode(message, undefined);
        if (extData) {
            // 创建扩展类型消息
            const encoded = encode(extData);
            return encoded.buffer.slice(encoded.byteOffset, encoded.byteOffset + encoded.byteLength) as ArrayBuffer;
        } else {
            // 如果扩展编码失败，使用普通编码
            const encoded = encode(message);
            return encoded.buffer.slice(encoded.byteOffset, encoded.byteOffset + encoded.byteLength) as ArrayBuffer;
        }
    }

    // 解码消息
    private decodeMessage(data: ArrayBuffer): Message {
        console.log('Attempting to decode MsgPack data.');
        console.log('Data to decode (Uint8Array length):', new Uint8Array(data).length);
        console.log('Data to decode (first 20 bytes):', new Uint8Array(data).slice(0, 20)); // 打印前20个字节
        try {
            const decoded = decode(new Uint8Array(data), { extensionCodec });
            console.log('MsgPack decoded object:', decoded); // Add log to inspect decoded object
            if (decoded && typeof decoded === 'object' && 'type' in decoded) {
                console.log('Decoded message type:', (decoded as any).type); // Log the type if it exists
            }
            return decoded as Message;
        } catch (error) {
            console.error('MsgPack 解码错误:', error);
            if (error instanceof RangeError) {
                console.error('RangeError: 数据可能被截断或格式不正确。');
            }
            throw error; // 重新抛出，以便上层捕获
        }
    }
}

// 创建全局 WebSocket 管理器实例
export const webSocketManager = new WebSocketManager('ws://localhost:9090/websocket');
