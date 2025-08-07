package com.tenframework.core.path;

import com.tenframework.core.message.Location;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

/**
 * 抽象路径基类
 * 对应C语言中ten_path_t的通用部分
 * 记录命令的生命周期和回溯信息
 */
@Getter
@Setter
@SuperBuilder
public abstract class AbstractPath {

    /**
     * 命令ID，唯一标识一个命令实例
     */
    protected long commandId;

    /**
     * 父命令ID，用于命令链的追溯
     */
    protected long parentCommandId;

    /**
     * 原始命令名称
     */
    protected String commandName;

    /**
     * 路径的类型（IN或OUT）
     */
    protected PathType pathType;

    /**
     * 消息的来源位置
     */
    protected Location sourceLocation;

    /**
     * 消息的目标位置
     */
    protected Location destinationLocation;

    /**
     * 路径创建时间戳
     */
    protected long timestamp;

    public AbstractPath() {
        this.timestamp = Instant.now().toEpochMilli();
    }
}