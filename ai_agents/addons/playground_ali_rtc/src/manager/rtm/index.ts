"use client"

import RTM from "@dingrtc/rtm";
import { AGEventEmitter } from "../events"
import { apiGenAliData } from "@/common"
import { type IRTMTextItem, ERTMTextType } from "@/types"

export interface IRtmEvents {
  rtmMessage: (text: IRTMTextItem) => void
}

export type TRTMMessageEvent = {
  channelType: "STREAM" | "MESSAGE" | "USER"
  channelName: string
  topicName?: string
  messageType: "STRING" | "BINARY"
  customType?: string
  publisher: string
  message: string | Uint8Array
  timestamp: number
}

export type TRTMPresenceEvent = {
  channelType: "STREAM" | "MESSAGE" | "USER"
  channelName: string
  topicName?: string
  publisher: string
  timestamp: number
  data?: unknown
}

export class RtmManager extends AGEventEmitter<IRtmEvents> {
  private _joined: boolean
  _client: RTM | null
  channel: string = ""
  userId: number = 0
  appId: string = ""
  token: string = ""

  constructor() {
    super()
    this._joined = false
    this._client = null
  }

  async init({
    channel,
    userId,
    appId,
    token,
  }: {
    channel: string
    userId: number
    appId: string
    token: string
  }) {
    if (this._joined) {
      return
    }
    this.channel = channel
    this.userId = userId
    this.appId = appId
    this.token = token

    // 创建 RTM 实例
    const rtm = new RTM({
      logLevel: "debug", // TODO: use INFO
    })

    // 加入频道
    await rtm.join({
      appId: appId,
      channel: channel,
      token: token,
      uid: String(userId),
      userName: `user_${userId}`,
    })

    this._joined = true
    this._client = rtm

    // 监听事件
    this._listenRtmEvents()
  }

  private _listenRtmEvents() {
    if (!this._client) return

    // 监听消息事件
    this._client.on("message", (messageData) => {
      this.handleRtmMessage(messageData)
    })

    // 监听连接状态变化
    this._client.on("connection-state-changed", (currState, prevState, reason) => {
      console.log("[RTM] Connection state changed:", prevState, "->", currState, reason)
    })

    console.log("[RTM] Listen RTM events success!")
  }

  async handleRtmMessage(messageData: { message: Uint8Array; uid: string; sessionId: string; broadcast: boolean }) {
    console.log("[RTM] [TRTMMessageEvent] RAW", JSON.stringify(messageData))
    const { message, uid } = messageData

    try {
      const decoder = new TextDecoder("utf-8")
      const decodedMessage = decoder.decode(message)
      const msg: IRTMTextItem = JSON.parse(decodedMessage)

      if (msg) {
        console.log("[RTM] Emitting rtmMessage event with msg:", msg)
        this.emit("rtmMessage", msg)
      }
    } catch (err) {
      console.error("[RTM] Failed to parse message:", err)
    }
  }

  async sendText(text: string) {
    if (!this._client) return

    const msg: IRTMTextItem = {
      is_final: true,
      ts: Date.now(),
      text,
      type: ERTMTextType.INPUT_TEXT,
      stream_id: String(this.userId),
    }

    const encoder = new TextEncoder()
    const messageBytes = encoder.encode(JSON.stringify(msg))

    // 发布消息到频道 - 使用 publish 方法
    // 注意：需要先加入 session，这里简化处理
    this._client.publish(this.channel, messageBytes)
    this.emit("rtmMessage", msg)
  }

  async destroy() {
    if (!this._client) return

    // 离开频道
    await this._client.leave()
    this._client = null
    this._joined = false
  }
}

export const rtmManager = new RtmManager()
