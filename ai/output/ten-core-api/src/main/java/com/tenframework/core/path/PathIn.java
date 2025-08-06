package com.tenframework.core.path;

import com.tenframework.core.Location;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/**
 * 命令输入路径
 * 表示命令进入Engine的路径
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class PathIn extends AbstractPath {

    /**
     * 是否已收到最终的命令结果
     */
    private boolean hasReceivedFinalCommandResult;

    // TODO: 可能需要添加其他属性，如结果转换策略等

    public PathIn(UUID commandId, UUID parentCommandId, String commandName, Location sourceLocation,
            Location destinationLocation) {
        super();
        this.commandId = commandId;
        this.parentCommandId = parentCommandId;
        this.commandName = commandName;
        this.sourceLocation = sourceLocation;
        this.destinationLocation = destinationLocation;
        this.pathType = PathType.IN;
        this.hasReceivedFinalCommandResult = false;
    }
}