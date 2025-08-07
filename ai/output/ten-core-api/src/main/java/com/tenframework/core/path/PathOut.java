package com.tenframework.core.path;

import com.tenframework.core.message.Location;
import com.tenframework.core.message.CommandResult;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 命令输出路径
 * 表示命令从Engine发出到外部的路径
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class PathOut extends AbstractPath {

    /**
     * 用于处理命令结果的回调函数（Java中的CompletableFuture）
     */
    private transient CompletableFuture<Object> resultFuture;

    /**
     * 与此PathOut关联的Netty Channel的唯一ID，用于将结果回传给特定客户端
     */
    private String channelId;

    /**
     * 是否已收到最终的命令结果
     */
    @Builder.Default
    private boolean hasReceivedFinalCommandResult = false;

    /**
     * 缓存的命令结果，用于某些结果策略（例如FIRST_ERROR_OR_LAST_OK）
     */
    @Builder.Default
    private CommandResult cachedCommandResult = null;

    /**
     * 结果返回策略（对应TEN_RESULT_RETURN_POLICY）
     */
    @Builder.Default
    private ResultReturnPolicy returnPolicy = ResultReturnPolicy.FIRST_ERROR_OR_LAST_OK;

    public PathOut(long commandId, long parentCommandId, String commandName, Location sourceLocation,
            Location destinationLocation, CompletableFuture<Object> resultFuture,
            ResultReturnPolicy returnPolicy, String channelId) {
        super();
        this.commandId = commandId;
        this.parentCommandId = parentCommandId;
        this.commandName = commandName;
        this.sourceLocation = sourceLocation;
        this.destinationLocation = destinationLocation;
        this.pathType = PathType.OUT;
        this.resultFuture = resultFuture;
        this.returnPolicy = returnPolicy != null ? returnPolicy : ResultReturnPolicy.FIRST_ERROR_OR_LAST_OK;
        this.hasReceivedFinalCommandResult = false;
        this.channelId = channelId;
    }
}