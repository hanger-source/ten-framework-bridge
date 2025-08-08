import { Message, Data, Command, CommandResult } from '@/types/websocket';
import { encode, decode, ExtensionCodec, ExtData } from '@msgpack/msgpack';

// TEN框架自定义MsgPack扩展类型码
const TEN_MSGPACK_EXT_TYPE_MSG = -1;

// 创建扩展编解码器
const extensionCodec = new ExtensionCodec();

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
});

export enum WebSocketConnectionState {
    CONNECTING = 'connecting',
    OPEN = 'open',
    CLOSING = 'closing',
    CLOSED = 'closed',
}

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
    private messageHandlers: Map<string, (message: Message) => void> = new Map();
    private connectionStateHandlers: ((state: WebSocketConnectionState) => void)[] = [];

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
                    this.setConnectionState(WebSocketConnectionState.CLOSED);
                    this.handleReconnect();
                };

                this.ws.onerror = (error) => {
                    console.error('WebSocket 连接错误:', error);
                    this.setConnectionState(WebSocketConnectionState.CLOSED);
                    reject(error);
                };

            } catch (error) {
                console.error('创建 WebSocket 连接失败:', error);
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
    public sendTextData(name: string, text: string): void {
        const dataMessage: Data = {
            type: 'data',
            name,
            data: text,
            content_type: 'text/plain',
            encoding: 'UTF-8',
            timestamp: Date.now(),
        };
        this.sendMessage(dataMessage);
    }

    // 发送 JSON 数据
    public sendJsonData(name: string, jsonData: any): void {
        const dataMessage: Data = {
            type: 'data',
            name,
            data: JSON.stringify(jsonData),
            content_type: 'application/json',
            encoding: 'UTF-8',
            timestamp: Date.now(),
        };
        this.sendMessage(dataMessage);
    }

    // 发送命令
    public sendCommand(name: string, args: Record<string, any> = {}): void {
        const commandMessage: Command = {
            type: 'cmd',
            name,
            cmd_id: this.generateCommandId(),
            args,
            properties: args.properties || {}, // 确保 properties 字段被正确设置
            timestamp: Date.now(),
        };
        this.sendMessage(commandMessage);
    }

    // 注册消息处理器
    public onMessage(type: string, handler: (message: Message) => void): void {
        this.messageHandlers.set(type, handler);
    }

    // 注册连接状态处理器
    public onConnectionStateChange(handler: (state: WebSocketConnectionState) => void): void {
        this.connectionStateHandlers.push(handler);
    }

    // 获取连接状态
    public getConnectionState(): WebSocketConnectionState {
        return this.connectionState;
    }

    // 处理接收到的消息
    private handleMessage(event: MessageEvent): void {
        try {
            const message = this.decodeMessage(event.data);
            console.log('收到消息:', message);

            const handler = this.messageHandlers.get(message.type);
            if (handler) {
                handler(message);
            } else {
                console.warn('未找到消息处理器:', message.type);
            }
        } catch (error) {
            console.error('处理消息失败:', error);
        }
    }

    // 设置连接状态
    private setConnectionState(state: WebSocketConnectionState): void {
        this.connectionState = state;
        this.connectionStateHandlers.forEach(handler => handler(state));
    }

    // 处理重连
    private handleReconnect(): void {
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
    private generateCommandId(): number {
        return Date.now() + Math.random();
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
        // 使用扩展编解码器解码
        const decoded = decode(new Uint8Array(data), { extensionCodec });
        return decoded as Message;
    }
}

// 创建全局 WebSocket 管理器实例
export const webSocketManager = new WebSocketManager('ws://localhost:9090/websocket');
