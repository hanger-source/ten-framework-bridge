package com.tenframework.core.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.List;

/**
 * 命令消息类
 * 代表控制流和业务意图的传递
 * 对应C语言中的ten_cmd_t结构
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Slf4j
public final class Command extends AbstractMessage {

    @JsonProperty("cmd_id")
    private long commandId;

    @JsonProperty("parent_cmd_id")
    private long parentCommandId;

    @JsonProperty("args")
    private Map<String, Object> args;

    /**
     * 默认构造函数（用于Jackson反序列化，如果JsonCreator未完全覆盖所有场景）
     * 内部使用，不推荐直接调用
     */
    protected Command() {
        super();
        commandId = generateCommandId();
        args = new HashMap<>();
    }

    /**
     * JSON反序列化和Builder使用的全参数构造函数
     */
    @Builder
    @JsonCreator
    public Command(
            @JsonProperty("cmd_id") long commandId,
            @JsonProperty("parent_cmd_id") long parentCommandId,
            @JsonProperty("name") String name,
            @JsonProperty("args") Map<String, Object> args,
            @JsonProperty("source_location") Location sourceLocation,
            @JsonProperty("destination_locations") List<Location> destinationLocations,
            @JsonProperty("properties") Map<String, Object> properties, // 继承自AbstractMessage
            @JsonProperty("timestamp") Long timestamp // 继承自AbstractMessage
    ) {
        super(sourceLocation, destinationLocations);
        setName(name); // 设置从AbstractMessage继承的name
        setProperties(properties); // 设置从AbstractMessage继承的properties
        if (timestamp != null) {
            setTimestamp(timestamp); // 设置从AbstractMessage继承的timestamp
        }
        this.commandId = commandId != 0 ? commandId : generateCommandId();
        this.parentCommandId = parentCommandId;
        this.args = args != null ? new HashMap<>(args) : new HashMap<>();
    }

    /**
     * 拷贝构造函数
     */
    private Command(Command other) {
        super(other);
        commandId = generateCommandId(); // 在克隆时生成新的commandId
        parentCommandId = other.parentCommandId;
        args = MessageUtils.deepCopyMap(other.args);
    }

    /**
     * 生成long格式的命令ID，使用UUID的最不重要位或最重要位来确保唯一性，模仿C/Python的uint64_t
     */
    public static long generateCommandId() {
        // 使用UUID的两个64位部分中的一个，或者简单地使用随机长整数
        // 这里使用getMostSignificantBits()，因为它与Python/C中常见的UUID到long转换方式一致
        // 也可以考虑 ThreadLocalRandom.current().nextLong() 但UUID提供更强的唯一性
        return UUID.randomUUID().getMostSignificantBits();
    }

    @Override
    public MessageType getType() {
        return MessageType.COMMAND;
    }

    // 重写args相关方法以提供防御性拷贝
    public Map<String, Object> getArgs() {
        return new HashMap<>(args);
    }

    public void setArgs(Map<String, Object> args) {
        this.args.clear();
        if (args != null) {
            this.args.putAll(args);
        }
    }

    /**
     * 获取指定参数值
     */
    public Object getArg(String key) {
        return args.get(key);
    }

    /**
     * 获取指定参数值，带类型转换 - 使用Optional和现代Java特性
     */
    public <T> Optional<T> getArg(String key, Class<T> type) {
        return Optional.ofNullable(args.get(key))
                .filter(type::isInstance)
                .map(type::cast)
                .or(() -> {
                    if (args.containsKey(key)) {
                        log.warn("无法将参数 {} 转换为类型 {}", key, type.getSimpleName());
                    }
                    return Optional.empty();
                });
    }

    /**
     * 获取指定参数值，带类型转换和默认值
     */
    public <T> T getArg(String key, Class<T> type, T defaultValue) {
        return getArg(key, type).orElse(defaultValue);
    }

    /**
     * 设置参数值
     */
    public void setArg(String key, Object value) {
        if (key != null) {
            if (value != null) {
                args.put(key, value);
            } else {
                args.remove(key);
            }
        }
    }

    /**
     * 检查参数是否存在
     */
    public boolean hasArg(String key) {
        return args.containsKey(key);
    }

    /**
     * 移除参数
     */
    public Object removeArg(String key) {
        return args.remove(key);
    }

    /**
     * 如果命令ID为空，则生成一个新的
     */
    public void generateCommandIdIfEmpty() {
        if (commandId == 0) { // 使用0作为默认无效值
            commandId = generateCommandId();
        }
    }

    @Override
    public boolean checkIntegrity() {
        return super.checkIntegrity() &&
                commandId != 0 && // 命令ID不能为0
                MessageUtils.validateStringField(getName(), "命令名称");
    }

    @Override
    public Command clone() throws CloneNotSupportedException {
        return new Command(this);
    }

    @Override
    public String toDebugString() {
        return String.format("Command[id=%d, parent=%d, name=%s, args=%d, src=%s, dest=%s]",
                commandId,
                parentCommandId,
                getName(),
                args.size(),
                getSourceLocation(),
                getDestinationLocations().size());
    }
}