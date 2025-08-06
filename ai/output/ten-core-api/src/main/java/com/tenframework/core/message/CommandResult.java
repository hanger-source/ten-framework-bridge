package com.tenframework.core.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.function.Predicate;

/**
 * 命令结果消息类
 * 代表命令执行结果的回溯
 * 对应C语言中的ten_cmd_result_t结构
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Slf4j
public final class CommandResult extends AbstractMessage {

    @JsonProperty("cmd_id")
    private String commandId;

    @JsonProperty("result")
    private Map<String, Object> result = new HashMap<>();

    @JsonProperty("is_final")
    private boolean isFinal = true; // 默认为最终结果

    @JsonProperty("error")
    private String error;

    @JsonProperty("error_code")
    private Integer errorCode;

    /**
     * 默认构造函数
     */
    public CommandResult() {
        super();
    }

    /**
     * 创建成功结果的构造函数
     */
    public CommandResult(String commandId) {
        this();
        this.commandId = commandId;
    }

    /**
     * 创建成功结果的构造函数，带结果数据
     */
    public CommandResult(String commandId, Map<String, Object> result) {
        this(commandId);
        if (result != null) {
            this.result.putAll(result);
        }
    }

    /**
     * 创建错误结果的构造函数
     */
    public CommandResult(String commandId, String error, Integer errorCode) {
        this(commandId);
        this.error = error;
        this.errorCode = errorCode;
    }

    /**
     * JSON反序列化构造函数
     */
    @JsonCreator
    public CommandResult(
            @JsonProperty("cmd_id") String commandId,
            @JsonProperty("result") Map<String, Object> result,
            @JsonProperty("is_final") Boolean isFinal,
            @JsonProperty("error") String error,
            @JsonProperty("error_code") Integer errorCode) {
        super();
        this.commandId = commandId;
        this.result = result != null ? new HashMap<>(result) : new HashMap<>();
        this.isFinal = isFinal != null ? isFinal : true;
        this.error = error;
        this.errorCode = errorCode;
    }

    /**
     * 拷贝构造函数
     */
    private CommandResult(CommandResult other) {
        super(other);
        this.commandId = other.commandId;
        this.result = MessageUtils.deepCopyMap(other.result);
        this.isFinal = other.isFinal;
        this.error = other.error;
        this.errorCode = other.errorCode;
    }

    @Override
    public MessageType getType() {
        return MessageType.COMMAND_RESULT;
    }

    // 重写result相关方法以提供防御性拷贝
    public Map<String, Object> getResult() {
        return new HashMap<>(result);
    }

    public void setResult(Map<String, Object> result) {
        this.result.clear();
        if (result != null) {
            this.result.putAll(result);
        }
    }

    /**
     * 获取指定结果值
     */
    public Object getResultValue(String key) {
        return result.get(key);
    }

    /**
     * 获取指定结果值，带类型转换 - 使用Optional和现代Java特性
     */
    public <T> Optional<T> getResultValue(String key, Class<T> type) {
        return Optional.ofNullable(result.get(key))
                .filter(type::isInstance)
                .map(type::cast)
                .or(() -> {
                    if (result.containsKey(key)) {
                        log.warn("无法将结果 {} 转换为类型 {}", key, type.getSimpleName());
                    }
                    return Optional.empty();
                });
    }

    /**
     * 获取指定结果值，带类型转换和默认值
     */
    public <T> T getResultValue(String key, Class<T> type, T defaultValue) {
        return getResultValue(key, type).orElse(defaultValue);
    }

    /**
     * 设置结果值
     */
    public void setResultValue(String key, Object value) {
        if (key != null) {
            if (value != null) {
                result.put(key, value);
            } else {
                result.remove(key);
            }
        }
    }

    /**
     * 检查结果是否存在
     */
    public boolean hasResultValue(String key) {
        return result.containsKey(key);
    }

    /**
     * 移除结果值
     */
    public Object removeResultValue(String key) {
        return result.remove(key);
    }

    /**
     * 检查是否为成功结果 - 使用现代Java特性
     */
    @JsonIgnore
    public boolean isSuccess() {
        return Optional.ofNullable(error)
                .map(String::trim)
                .filter(Predicate.not(String::isEmpty))
                .isEmpty();
    }

    /**
     * 检查是否为错误结果
     */
    public boolean isError() {
        return !isSuccess();
    }

    /**
     * 创建成功结果的静态工厂方法
     */
    public static CommandResult success(String commandId) {
        return new CommandResult(commandId);
    }

    /**
     * 创建成功结果的静态工厂方法，带结果数据
     */
    public static CommandResult success(String commandId, Map<String, Object> result) {
        return new CommandResult(commandId, result);
    }

    /**
     * 创建错误结果的静态工厂方法
     */
    public static CommandResult error(String commandId, String error) {
        return new CommandResult(commandId, error, null);
    }

    /**
     * 创建错误结果的静态工厂方法，带错误代码
     */
    public static CommandResult error(String commandId, String error, Integer errorCode) {
        return new CommandResult(commandId, error, errorCode);
    }

    /**
     * 创建流式中间结果
     */
    public static CommandResult streaming(String commandId, Map<String, Object> result) {
        CommandResult cmdResult = new CommandResult(commandId, result);
        cmdResult.setFinal(false);
        return cmdResult;
    }

    @Override
    public boolean checkIntegrity() {
        return super.checkIntegrity() &&
                MessageUtils.validateStringField(commandId, "命令结果的命令ID");
    }

    @Override
    public CommandResult clone() {
        return new CommandResult(this);
    }

    @Override
    public String toDebugString() {
        return String.format("CommandResult[cmdId=%s, success=%s, final=%s, results=%d, error=%s]",
                commandId,
                isSuccess(),
                isFinal,
                result.size(),
                error);
    }
}