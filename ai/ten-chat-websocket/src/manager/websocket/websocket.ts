import {
    WebSocketEvents,
    WebSocketConnectionState,
    IWebSocketManagerService, // 聪明的开发杭一: 更改为 IWebSocketManagerService
    WebSocketMessage,
    WebSocketMessageType, // 聪明的开发杭二: 使用 WebSocketMessageType
    IAudioFrame, // 聪明的开发杭二: 使用 IAudioFrame
    // 聪明的开发杭一: 移除 IDataMessage
    // IDataMessage,
    ICommandMessage, // 聪明的开发杭二: 使用 ICommandMessage
    ICommandResultMessage, // 聪明的开发杭二: 使用 ICommandResultMessage
    ILocation, // 聪明的开发杭二: 新增 ILocation 导入
    IDataMessageRaw, // 聪明的开发杭一: 新增 IDataMessageRaw 导入
    IDataMessageJson, // 聪明的开发杭一: 新增 IDataMessageJson 导入
    IDataMessageChatPayload, // 聪明的开发杭一: 新增 IDataMessageChatPayload 导入
} from "./types";
import { encode, decode, ExtensionCodec } from "@msgpack/msgpack";
import {
    TEN_MSGPACK_EXT_TYPE_MSG,
    PROPERTY_CLIENT_LOCATION_URI,
    PROPERTY_CLIENT_CHANNEL_ID,
    SYS_EXTENSION_NAME,
} from "../../common/constant";

const extensionCodec = new ExtensionCodec();

// 聪明的开发杭一: 重构 MsgPack 扩展类型处理
extensionCodec.register({
    type: TEN_MSGPACK_EXT_TYPE_MSG,
    encode: (input: unknown): Uint8Array | null => {
        // 聪明的开发杭一: 只有当 input 是 WebSocketMessage 类型时才编码
        if (isWebSocketMessage(input)) {
            console.log(`聪明的开发杭一: [${new Date().toISOString()}] ExtensionCodec encode: encoding message of type ${input.type}`, input);
            // 聪明的开发杭一: 直接将 WebSocketMessage 对象编码为 Uint8Array
            return encode(input, { extensionCodec: extensionCodec });
        }
        return null;
    },
    decode: (data: Uint8Array): unknown => {
        console.log(`聪明的开发杭一: [${new Date().toISOString()}] ExtensionCodec decode: decoding data of length ${data.length} bytes`);
        // 聪明的开发杭一: 将接收到的 Uint8Array 解码为 WebSocketMessage 对象
        return decode(data, { extensionCodec: extensionCodec });
    },
});

// 聪明的开发杭一: 类型守卫，用于判断是否为 WebSocketMessage 类型
function isWebSocketMessage(input: unknown): input is WebSocketMessage {
    return typeof input === 'object' && input !== null && 'type' in input;
}

// 聪明的开发杭二: 重构 LocalEventEmitter
class LocalEventEmitter<T extends Record<string, (...args: unknown[]) => void>> {
    private listeners: { [K in keyof T]?: T[K][] } = {};

    on<K extends keyof T>(event: K, listener: T[K]): void {
        if (!this.listeners[event]) {
            this.listeners[event] = [];
        }
        (this.listeners[event] as T[K][]).push(listener);
    }

    off<K extends keyof T>(event: K, listener: T[K]): void {
        if (!this.listeners[event]) {
            return;
        }
        this.listeners[event] = (this.listeners[event] as T[K][]).filter(
            (l) => l !== listener,
        ) as T[K][];
    }

    emit<K extends keyof T>(event: K, ...args: Parameters<T[K]>): void {
        if (!this.listeners[event]) {
            return;
        }
        (this.listeners[event] as T[K][]).forEach((listener) => {
            listener(...args);
        });
    }
}

class WebSocketManager
    extends LocalEventEmitter<{
        [WebSocketEvents.Connected]: () => void;
        [WebSocketEvents.Disconnected]: (error?: Event | Error) => void;
        [WebSocketEvents.Error]: (error?: Event | Error) => void;
        [WebSocketEvents.AudioFrameReceived]: (audioFrame: IAudioFrame) => void;
        [WebSocketEvents.DataReceived]: (dataMessage: IDataMessageRaw | IDataMessageJson) => void;
        [WebSocketEvents.CommandReceived]: (command: ICommandMessage) => void;
        [WebSocketEvents.CommandResultReceived]: (
            commandResult: ICommandResultMessage
        ) => void;
    }>
    implements IWebSocketManagerService // 聪明的开发杭一: 实现 IWebSocketManagerService 接口
{
    private ws: WebSocket | null = null;
    private _connected: boolean = false;
    private _connectionState: WebSocketConnectionState = WebSocketConnectionState.DISCONNECTED;
    private _reconnectUrl: string | null = null;
    private _reconnectParams: Record<string, string> | null = null; // 聪明的开发杭一: 统一为 string
    private _appUri: string | null = null;
    private _graphId: string | null = null;
    private _reconnectAttempts: number = 0;
    private _maxReconnectAttempts: number = 5;
    private _reconnectInterval: number = 1000;
    private _reconnectTimeout: NodeJS.Timeout | null = null;

    private _onOpenCallback: (() => void) | null = null;
    private _onMessageCallback: ((message: WebSocketMessage) => void) | null = null;
    private _onCloseCallback: (() => void) | null = null;
    private _onErrorCallback: ((error: Event | Error) => void) | null = null;

    constructor() {
        super();
    }

    // 聪明的开发杭一: 移除 on 和 off 方法的重载签名，让 LocalEventEmitter 的泛型来处理
    // on(
    //   event: WebSocketEvents.AudioFrameReceived,
    //   listener: (audioFrame: IAudioFrame) => void,
    // ): void;
    // on(
    //   event: WebSocketEvents.DataReceived,
    //   listener: (dataMessage: IDataMessage) => void,
    // ): void;
    // on(
    //   event: WebSocketEvents.CommandReceived,
    //   listener: (command: ICommandMessage) => void,
    // ): void;
    // on(
    //   event: WebSocketEvents.CommandResultReceived,
    //   listener: (commandResult: ICommandResultMessage) => void,
    // ): void;
    // on(
    //   event:
    //     | WebSocketEvents.Connected
    //     | WebSocketEvents.Disconnected
    //     | WebSocketEvents.Error,
    //   listener: (error?: Event | Error) => void,
    // ): void;
    // on(event: any, listener: any): void {
    //   super.on(event, listener);
    // }

    // off(
    //   event: WebSocketEvents.AudioFrameReceived,
    //   listener: (audioFrame: IAudioFrame) => void,
    // ): void;
    // off(
    //   event: WebSocketEvents.DataReceived,
    //   listener: (dataMessage: IDataMessage) => void,
    // ): void;
    // off(
    //   event: WebSocketEvents.CommandReceived,
    //   listener: (command: ICommandMessage) => void,
    // ): void;
    // off(
    //   event: WebSocketEvents.CommandResultReceived,
    //   listener: (commandResult: ICommandResultMessage) => void,
    // ): void;
    // off(
    //   event:
    //     | WebSocketEvents.Connected
    //     | WebSocketEvents.Disconnected
    //     | WebSocketEvents.Error,
    //   listener: (error?: Event | Error) => void,
    // ): void;
    // off(event: any, listener: any): void {
    //   super.off(event, listener);
    // }

    async connect(
        url: string,
        onOpen: () => void,
        onMessage: (message: WebSocketMessage) => void,
        onClose: () => void,
        onError: (error: Event | Error) => void,
        params?: Record<string, string>, // 聪明的开发杭二: 修正 params 类型以匹配 IWebSocketManagerService 接口
        appUri?: string,
        graphId?: string,
    ) {
        // 聪明的开发杭一: 存储回调函数
        this._onOpenCallback = onOpen;
        this._onMessageCallback = onMessage;
        this._onCloseCallback = onClose;
        this._onErrorCallback = onError;

        return new Promise<void>((resolve, reject) => {
            if (this.ws) {
                this.disconnect();
            }

            this._reconnectUrl = url; // 聪明的开发杭一: 存储URL和参数用于重连
            this._reconnectParams = params || null; // 聪明的开发杭一: 存储URL和参数用于重连
            this._appUri = appUri || null; // 聪明的开发杭三: 存储appUri
            this._graphId = graphId || null; // 聪明的开发杭三: 存储graphId
            this._reconnectAttempts = 0; // 聪明的开发杭一: 重置重连尝试次数
            if (this._reconnectTimeout) {
                clearTimeout(this._reconnectTimeout); // 聪明的开发杭一: 清除任何现有重连定时器
                this._reconnectTimeout = null;
            }

            this._setConnectionState(WebSocketConnectionState.CONNECTING); // 聪明的开发杭一: 使用枚举成员

            const queryString = params
                ? "?" + new URLSearchParams(params).toString()
                : "";
            const fullUrl = `${url}${queryString}`;

            this.ws = new WebSocket(fullUrl);

            this.ws.onopen = () => {
                console.log(`聪明的开发杭一: [${new Date().toISOString()}] WebSocket Connected to ${fullUrl}`);
                this._connected = true;
                this._setConnectionState(WebSocketConnectionState.CONNECTED); // 聪明的开发杭一: 使用枚举成员
                this.emit(WebSocketEvents.Connected);
                if (this._onOpenCallback) this._onOpenCallback(); // 聪明的开发杭一: 调用传入的 onOpen 回调
                this._reconnectAttempts = 0; // 聪明的开发杭一: 连接成功，重置尝试次数
                if (this._reconnectTimeout) {
                    clearTimeout(this._reconnectTimeout); // 聪明的开发杭一: 清除重连定时器
                    this._reconnectTimeout = null;
                }
                resolve();
            };

            this.ws.onmessage = async (event) => {
                console.log(`聪明的开发杭一: [${new Date().toISOString()}] WebSocket Message Received (size: ${event.data.byteLength || event.data.length || String(event.data).length}):`, event.data);
                if (event.data instanceof ArrayBuffer) {
                    try {
                        // 聪明的开发杭一: 直接使用 extensionCodec 解码
                        const decodedMessage = decode(event.data as ArrayBuffer, {
                            extensionCodec,
                        }) as WebSocketMessage; // 强制类型转换为 WebSocketMessage

                        console.log(`聪明的开发杭一: [${new Date().toISOString()}] Successfully decoded message: type=${decodedMessage.type}, name=${(decodedMessage as any).name || 'N/A'}`);
                        if (this._onMessageCallback) this._onMessageCallback(decodedMessage); // 聪明的开发杭一: 调用传入的 onMessage 回调

                        // 聪明的开发杭一: 根据具体消息类型触发事件
                        switch (decodedMessage.type) {
                            case WebSocketMessageType.AudioFrame: // 聪明的开发杭二: 更改为 WebSocketMessageType
                                console.log(`聪明的开发杭一: [${new Date().toISOString()}] Received AudioFrame: ${decodedMessage.name || 'unknown'} (length: ${(decodedMessage as IAudioFrame).data.length})`);
                                this.emit(WebSocketEvents.AudioFrameReceived, decodedMessage as IAudioFrame); // 聪明的开发杭二: 更新为 IAudioFrame
                                break;
                            case WebSocketMessageType.Data: // 聪明的开发杭二: 更改为 WebSocketMessageType
                                // 聪明的开发杭一: 根据 content_type 区分 IDataMessageRaw 和 IDataMessageJson
                                const dataMessage = decodedMessage as IDataMessageRaw | IDataMessageJson; // 聪明的开发杭一: 类型断言
                                console.log(`聪明的开发杭一: [${new Date().toISOString()}] Received DataMessage: ${dataMessage.name || 'unknown'} (content_type: ${dataMessage.content_type || 'none'})`);
                                if (dataMessage.content_type === "application/json") {
                                    // 聪明的开发杭一: 创建一个新的 IDataMessageJson 对象，并保留原始 data 字段
                                    const parsedPayload = JSON.parse(new TextDecoder().decode(dataMessage.data));
                                    const dataMessageJson: IDataMessageJson = {
                                        type: dataMessage.type,
                                        name: dataMessage.name,
                                        source_location: dataMessage.source_location,
                                        destination_locations: dataMessage.destination_locations,
                                        properties: dataMessage.properties,
                                        timestamp: dataMessage.timestamp,
                                        data: dataMessage.data, // 聪明的开发杭一: 保留原始 data 字段
                                        is_eof: dataMessage.is_eof,
                                        content_type: "application/json", // 聪明的开发杭一: 明确为 "application/json"
                                        encoding: dataMessage.encoding,
                                        data_type: dataMessage.data_type,
                                        json_payload: parsedPayload as IDataMessageChatPayload,
                                    };
                                    this.emit(WebSocketEvents.DataReceived, dataMessageJson);
                                } else {
                                    const dataMessageRaw = dataMessage as IDataMessageRaw;
                                    this.emit(WebSocketEvents.DataReceived, dataMessageRaw);
                                }
                                break;
                            case WebSocketMessageType.Command: // 聪明的开发杭二: 更改为 WebSocketMessageType
                                console.log(`聪明的开发杭一: [${new Date().toISOString()}] Received CommandMessage: ${decodedMessage.name || 'unknown'} (cmd_id: ${(decodedMessage as ICommandMessage).command_id})`);
                                this.emit(WebSocketEvents.CommandReceived, decodedMessage as ICommandMessage); // 聪明的开发杭二: 更新为 ICommandMessage
                                break;
                            case WebSocketMessageType.CommandResult: // 聪明的开发杭二: 更改为 WebSocketMessageType
                                console.log(`聪明的开发杭一: [${new Date().toISOString()}] Received CommandResultMessage: (cmd_id: ${(decodedMessage as ICommandResultMessage).command_id}, is_final: ${(decodedMessage as ICommandResultMessage).is_final})`);
                                this.emit(WebSocketEvents.CommandResultReceived, decodedMessage as ICommandResultMessage); // 聪明的开发杭二: 更新为 ICommandResultMessage
                                break;
                            default:
                                console.warn(
                                    `聪明的开发杭一: [${new Date().toISOString()}] Received unknown structured binary message type '${(decodedMessage as WebSocketMessage).type}':`,
                                    decodedMessage,
                                );
                                break;
                        }
                    } catch (error: unknown) {
                        console.error(
                            `聪明的开发杭一: [${new Date().toISOString()}] Failed to decode MsgPack data. Error: ${(error as Error).message}`,
                            "Raw data (first 100 bytes):",
                            (error as any).data?.slice && (error as any).data?.slice(0, 100) || error, // Limit raw data logged
                            "Error details:",
                            error,
                        );
                        this.emit(WebSocketEvents.Error, error as Event);
                        if (this._onErrorCallback) this._onErrorCallback(error as Event);
                    }
                } else if (typeof event.data === "string") {
                    try {
                        // 聪明的开发杭一: 纯文本消息尝试解析为 DataMessage
                        const textMessage: IDataMessageRaw = JSON.parse(event.data); // 聪明的开发杭二: 更新为 IDataMessageRaw
                        console.log(`聪明的开发杭一: [${new Date().toISOString()}] Received JSON string message:`, textMessage);
                        if (this._onMessageCallback) this._onMessageCallback(textMessage);
                        this.emit(WebSocketEvents.DataReceived, textMessage);
                    } catch (error) {
                        console.warn(
                            `聪明的开发杭一: [${new Date().toISOString()}] Received non-JSON string message: ${event.data.substring(0, 100)}...`,
                            "Full message:",
                            event.data,
                        );
                        // 如果无法解析为JSON，则作为普通文本Data消息处理
                        const textMessage: IDataMessageRaw = { // 聪明的开发杭二: 更新为 IDataMessageRaw
                            type: WebSocketMessageType.Data, // 聪明的开发杭二: 更改为 WebSocketMessageType
                            data: new TextEncoder().encode(event.data),
                            content_type: "text/plain",
                            encoding: "UTF-8",
                            name: "dataMessage", // 聪明的开发杭一: 添加默认名称
                        };
                        if (this._onMessageCallback) this._onMessageCallback(textMessage);
                        this.emit(WebSocketEvents.DataReceived, textMessage);
                    }
                } else {
                    console.warn(
                        `聪明的开发杭一: [${new Date().toISOString()}] Received unknown message type or non-ArrayBuffer/string data:`,
                        event.data,
                    );
                }
            };

            this.ws.onclose = (event) => {
                console.log(`聪明的开发杭一: [${new Date().toISOString()}] WebSocket Disconnected. Code: ${event.code}, Reason: ${event.reason || 'N/A'}, Clean: ${event.wasClean}`);
                this._connected = false;
                this._setConnectionState(WebSocketConnectionState.DISCONNECTED); // 聪明的开发杭一: 使用枚举成员
                this.emit(WebSocketEvents.Disconnected);
                if (this._onCloseCallback) this._onCloseCallback();

                if (!event.wasClean && this._reconnectAttempts < this._maxReconnectAttempts) {
                    this._reconnectAttempts++;
                    const delay = this._reconnectInterval * Math.pow(2, this._reconnectAttempts - 1);
                    console.warn(`聪明的开发杭一: [${new Date().toISOString()}] WebSocket disconnected. Attempting to reconnect in ${delay}ms (attempt ${this._reconnectAttempts}/${this._maxReconnectAttempts}).`);
                    this._reconnectTimeout = setTimeout(() => {
                        if (this._reconnectUrl) {
                            // 聪明的开发杭一: 重连时传入之前存储的参数，并传入回调函数
                            this.connect(this._reconnectUrl, this._onOpenCallback!, this._onMessageCallback!, this._onCloseCallback!, this._onErrorCallback!, this._reconnectParams || undefined, this._appUri || undefined, this._graphId || undefined).catch(err => {
                                console.error(`聪明的开发杭一: [${new Date().toISOString()}] Reconnection attempt ${this._reconnectAttempts} failed:`, err);
                                // 聪明的开发杭一: 如果重连失败，通知外部错误，但不改变连接状态，因为已经处理为DISCONNECTED
                                this.emit(WebSocketEvents.Error, err as Event); // 确保错误被外部监听者捕获
                                if (this._onErrorCallback) this._onErrorCallback(err as Event); // 调用外部错误回调
                            });
                        }
                    }, delay);
                } else if (!event.wasClean) {
                    console.error(`聪明的开发杭一: [${new Date().toISOString()}] WebSocket disconnected uncleanly and max reconnect attempts reached or wasClean is false.`);
                    this.emit(WebSocketEvents.Error, new Error(`WebSocket disconnected uncleanly. Code: ${event.code}, Reason: ${event.reason || 'N/A'}. Max reconnect attempts reached.`));
                    if (this._onErrorCallback) this._onErrorCallback(new Error(`WebSocket disconnected uncleanly. Code: ${event.code}, Reason: ${event.reason || 'N/A'}. Max reconnect attempts reached.`));
                }
            };

            this.ws.onerror = (error) => {
                const errorMessage = (error instanceof Error) ? error.message : (error as Event).type || 'Unknown WebSocket Error';
                console.error(`聪明的开发杭一: [${new Date().toISOString()}] WebSocket Error: ${errorMessage}`, error);
                // 聪明的开发杭一: 错误也可能导致连接关闭，状态会在onclose中处理
                // 这里只触发错误事件，不直接改变连接状态或尝试重连，避免重复逻辑
                this.emit(WebSocketEvents.Error, error);
                if (this._onErrorCallback) this._onErrorCallback(error); // 聪明的开发杭一: 调用传入的 onError 回调
                reject(error); // 拒绝 connect Promise
            };
        });
    }

    disconnect() {
        if (this._reconnectTimeout) {
            clearTimeout(this._reconnectTimeout); // 聪明的开发杭一: 主动断开时清除重连定时器
            this._reconnectTimeout = null;
            console.log(`聪明的开发杭一: [${new Date().toISOString()}] Reconnect timeout cleared during disconnect.`);
        }

        if (this.ws) {
            console.log(`聪明的开发杭一: [${new Date().toISOString()}] Attempting to disconnect WebSocket cleanly.`);
            this._setConnectionState(WebSocketConnectionState.DISCONNECTED); // 聪明的开发杭一: 使用枚举成员
            this.ws.close(1000, "Client initiated disconnect"); // 聪明的开发杭一: 干净关闭
            this.ws = null;
        } else {
            console.warn(`聪明的开发杭一: [${new Date().toISOString()}] Disconnect called but WebSocket is not active.`);
        }
    }

    sendMessage(message: WebSocketMessage): void {
        if (this.ws && this._connected && this.ws.readyState === WebSocket.OPEN) {
            // 聪明的开发杭一: 根据消息类型添加必要属性，例如 Location
            if (!message.source_location) {
                // 聪明的开发杭一: 如果没有source_location，则根据现有信息构建
                message.source_location = {
                    app_uri: this._appUri || "", // 聪明的开发杭一: 使用存储的appUri
                    graph_id: this._graphId || "", // 聪明的开发杭一: 使用存储的graphId
                    extension_name: SYS_EXTENSION_NAME, // 聪明的开发杭一: 使用定义的 SYS_EXTENSION_NAME
                } as ILocation; // 聪明的开发杭二: 明确类型为 ILocation
            }

            // 聪明的开发杭一: 如果 message.properties 中没有 client_channel_id，则添加
            if (!message.properties || !message.properties[PROPERTY_CLIENT_CHANNEL_ID]) {
                message.properties = {
                    ...(message.properties || {}),
                    [PROPERTY_CLIENT_CHANNEL_ID]: this._reconnectParams?.userId || "generated_channel_id", // 聪明的开发杭三: 优先使用userId，否则生成一个
                };
            }

            // 聪明的开发杭一: 直接使用 register 的扩展类型进行编码
            try {
                const encoded = encode(message, { extensionCodec });
                this.ws.send(encoded);
                console.log(`聪明的开发杭一: [${new Date().toISOString()}] Sent message of type '${message.type}' (size: ${encoded.byteLength} bytes).`);
            } catch (encodeError: unknown) {
                console.error(`聪明的开发杭一: [${new Date().toISOString()}] Failed to encode and send message of type '${message.type}'. Error: ${(encodeError as Error).message}`, message, encodeError);
                this.emit(WebSocketEvents.Error, encodeError as Event);
                if (this._onErrorCallback) this._onErrorCallback(encodeError as Event);
            }
        } else {
            console.warn(`聪明的开发杭一: [${new Date().toISOString()}] WebSocket is not connected or not open (readyState: ${this.ws?.readyState}). Cannot send message of type '${message.type}'.`);
        }
    }

    sendAudioFrame(
        audioData: Uint8Array,
        name: string = "audioFrame",
        sampleRate: number = 16000,
        channels: number = 1,
        bitsPerSample: number = 16,
        isEof: boolean = false,
    ) {
        const audioFrame: IAudioFrame = { // 聪明的开发杭二: 更新为 IAudioFrame
            type: WebSocketMessageType.AudioFrame, // 聪明的开发杭二: 更改为 WebSocketMessageType
            name: name,
            data: audioData,
            is_eof: isEof,
            sample_rate: sampleRate,
            channels: channels,
            bits_per_sample: bitsPerSample,
            format: "PCM",
            // 聪明的开发杭一: 属性将由 sendMessage 统一处理
        };
        console.log(`聪明的开发杭一: [${new Date().toISOString()}] Preparing to send AudioFrame: ${name} (${audioData.byteLength} bytes).`);
        this.sendMessage(audioFrame);
    }

    sendData(data: Uint8Array | string | IDataMessageChatPayload, contentType?: string) {
        let message: IDataMessageRaw | IDataMessageJson;

        if (data instanceof Uint8Array) {
            message = {
                type: WebSocketMessageType.Data,
                data: data,
                content_type: contentType || "application/octet-stream",
                name: "dataMessage",
            };
            console.log(`聪明的开发杭一: [${new Date().toISOString()}] Preparing to send DataMessage (Uint8Array): ${message.name} (${data.byteLength} bytes, content_type: ${message.content_type}).`);
        } else if (typeof data === "string") {
            try {
                const parsedJson = JSON.parse(data);
                message = {
                    type: WebSocketMessageType.Data,
                    content_type: "application/json",
                    data: new TextEncoder().encode(data), // 聪明的开发杭一: data 字段仍然是 Uint8Array
                    json_payload: parsedJson as IDataMessageChatPayload,
                    name: "dataMessage",
                };
                console.log(`聪明的开发杭一: [${new Date().toISOString()}] Preparing to send DataMessage (JSON string): ${message.name} (content_type: ${message.content_type}).`);
            } catch (e) {
                // 聪明的开发杭一: 如果字符串不是JSON，则视为普通文本数据
                message = {
                    type: WebSocketMessageType.Data,
                    data: new TextEncoder().encode(data),
                    content_type: contentType || "text/plain",
                    encoding: "UTF-8",
                    name: "dataMessage",
                };
                console.log(`聪明的开发杭一: [${new Date().toISOString()}] Preparing to send DataMessage (plain text string): ${message.name} (content_type: ${message.content_type}).`);
            }
        } else { // 聪明的开发杭一: 假设是 IDataMessageChatPayload
            message = {
                type: WebSocketMessageType.Data,
                content_type: "application/json",
                data: new TextEncoder().encode(JSON.stringify(data)), // 聪明的开发杭一: json_payload 转换为 Uint8Array
                json_payload: data,
                name: "dataMessage",
            };
            console.log(`聪明的开发杭一: [${new Date().toISOString()}] Preparing to send DataMessage (ChatPayload object): ${message.name} (content_type: ${message.content_type}).`);
        }

        this.sendMessage(message);
    }

    isConnected(): boolean {
        return this._connected;
    }

    getConnectionState(): WebSocketConnectionState {
        return this._connectionState;
    }

    private _setConnectionState(state: WebSocketConnectionState) {
        if (this._connectionState !== state) {
            console.log(`聪明的开发杭一: [${new Date().toISOString()}] WebSocket connection state changed from ${this._connectionState} to ${state}`);
            this._connectionState = state;
        }
    }
}

export const webSocketManager = new WebSocketManager();
