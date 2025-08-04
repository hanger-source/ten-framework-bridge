import { IChatItem } from "@/types";
import { TextDataChunk } from "./ali-rtc-types";

export class AliRtcMessageHandler {
    private messageCache: { [key: string]: TextDataChunk[] } = {};

    parseData(data: Uint8Array | string, userId: string | null): IChatItem | void {
        try {
            let parsed;
            if (typeof data === 'string') {
                parsed = JSON.parse(data);
            } else {
                const decoder = new TextDecoder('utf-8');
                const text = decoder.decode(data);
                parsed = JSON.parse(text);
            }

            if (parsed.type === "text") {
                return {
                    userId: parsed.userId || "ai",
                    userName: parsed.userName || "ai",
                    text: parsed.content,
                    data_type: "text",
                    type: parsed.userId === userId ? "user" : "agent",
                    isFinal: true,
                    time: Date.now(),
                };
            }
        } catch (err) {
            console.error("Failed to parse data", err);
        }
    }

    handleChunk(formattedChunk: string, userId: string | null) {
        try {
            const chunk = JSON.parse(formattedChunk);
            const messageId = chunk.message_id;

            if (!this.messageCache[messageId]) {
                this.messageCache[messageId] = [];
            }

            this.messageCache[messageId].push(chunk);

            // 检查是否所有分片都已收到
            if (this.messageCache[messageId].length === chunk.total_parts) {
                const reconstructedMessage = this.reconstructMessage(this.messageCache[messageId]);
                const textItem = this.parseData(JSON.stringify({
                    type: "text",
                    content: reconstructedMessage,
                    userId: messageId,
                    userName: "ai"
                }), userId);

                if (textItem) {
                    return textItem;
                }

                // 清理缓存
                delete this.messageCache[messageId];
            }
        } catch (err) {
            console.error("Failed to handle chunk", err);
        }
    }

    reconstructMessage(chunks: TextDataChunk[]): string {
        // 按part_index排序
        const sortedChunks = chunks.sort((a, b) => a.part_index - b.part_index);
        return sortedChunks.map(chunk => chunk.content).join("");
    }

    clearCache() {
        this.messageCache = {};
    }
}