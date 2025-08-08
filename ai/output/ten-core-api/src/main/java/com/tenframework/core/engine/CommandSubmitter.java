package com.tenframework.core.engine;

import java.util.concurrent.CompletableFuture;

import com.tenframework.core.message.Command;

/**
 * CommandSubmitter接口定义了提交命令并异步获取其结果的契约。
 * Engine将实现此接口，以便外部模块（如AsyncExtensionEnv）可以通过此抽象来提交命令。
 */
public interface CommandSubmitter {
    /**
     * 提交一个命令到Engine，并返回一个CompletableFuture，用于异步获取命令执行结果。
     *
     * @param command 要提交的命令对象。
     * @return CompletableFuture<Object>，代表命令执行的最终结果。
     */
    CompletableFuture<Object> submitCommand(Command command);

    /**
     * Cleanup.
     *
     * @param graphId the graph id
     */
    void cleanup(String graphId);
}