package com.tenframework.core.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class CommandResult extends Message {

    @JsonProperty("original_cmd_id")
    private String originalCommandId;

    @JsonProperty("original_cmd_type")
    private int originalCmdType;

    @JsonProperty("original_cmd_name")
    private String originalCmdName;

    @JsonProperty("status_code")
    private int statusCode;

    @JsonProperty("is_final")
    private boolean isFinal;

    @JsonProperty("is_completed")
    private boolean isCompleted;

    // 兼容 Lombok @NoArgsConstructor 的全参构造函数（为了Jackson）
    // 实际内部创建时使用自定义构造函数
    public CommandResult(String id, Location srcLoc, MessageType type, List<Location> destLocs,
            Map<String, Object> properties, long timestamp,
            String originalCommandId, int originalCmdType, String originalCmdName, int statusCode,
            boolean isFinal, boolean isCompleted) {
        super(id, srcLoc, type, destLocs, properties, timestamp);
        this.originalCommandId = originalCommandId;
        this.originalCmdType = originalCmdType;
        this.originalCmdName = originalCmdName;
        this.statusCode = statusCode;
        this.isFinal = isFinal;
        this.isCompleted = isCompleted;
    }

    // 用于内部创建的简化构造函数，匹配新的 Message 基类构造
    public CommandResult(Location srcLoc, List<Location> destLocs, String originalCommandId, int statusCode,
            String detail) {
        super(MessageType.CMD_RESULT, srcLoc, destLocs);
        this.originalCommandId = originalCommandId;
        this.originalCmdType = MessageType.CMD_RESULT.ordinal(); // 简化，实际可能需要原始命令的 type
        this.originalCmdName = originalCommandId; // 原始命令的 ID 作为名称
        this.statusCode = statusCode;
        this.isFinal = true; // 假设结果是最终的
        this.isCompleted = true; // 假设结果是完成的

        // detail 放入 properties map
        if (detail != null) {
            getProperties().put("detail", detail); // 使用基类的 properties Map
        }
    }

    // Getters for specific properties
    public String getOriginalCommandId() {
        return originalCommandId;
    }

    public int getOriginalCmdType() {
        return originalCmdType;
    }

    public String getOriginalCmdName() {
        return originalCmdName;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public boolean isFinal() {
        return isFinal;
    }

    public boolean isCompleted() {
        return isCompleted;
    }

    // 辅助方法：获取 detail (从 properties map 中获取)
    public String getDetail() {
        if (getProperties() != null && getProperties().containsKey("detail")) {
            return (String) getProperties().get("detail");
        }
        return null;
    }

    public static CommandResult success(String originalCommandId, String detail) {
        return new CommandResult(null, Collections.emptyList(), originalCommandId, 0, detail); // 简化，srcLoc和destLocs为null/empty
    }

    public static CommandResult fail(String originalCommandId, String detail) {
        return new CommandResult(null, Collections.emptyList(), originalCommandId, -1, detail); // 简化，srcLoc和destLocs为null/empty
    }
}