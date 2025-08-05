package com.tenframework.runtime.extension;

import com.tenframework.runtime.core.Command;
import com.tenframework.runtime.core.CommandResult;
import com.tenframework.runtime.core.Data;
import java.util.Optional;

/**
 * Provides an {@link Extension} with a context to interact with its host Engine
 * and the broader framework.
 * <p>
 * This interface is the sole mechanism for an extension to send data, return
 * results,
 * or access shared resources, ensuring a clear separation of concerns. It is
 * the Java
 * equivalent of the `ten_env` object in the C framework.
 */
public interface EngineContext {

    /**
     * Sends a command to be processed by the engine's message loop.
     * The command may be routed to another extension or handled by the engine
     * itself.
     *
     * @param cmd The command to send.
     */
    void sendCmd(Command<?> cmd);

    /**
     * Sends a data message to be processed by the engine's message loop.
     *
     * @param data The data message to send.
     */
    void sendData(Data data);

    /**
     * Returns a command result to its original caller.
     * The engine tracks pending commands and uses this method to complete the
     * appropriate {@link java.util.concurrent.CompletableFuture}.
     *
     * @param result The result of a command execution.
     */
    void returnResult(CommandResult result);

    /**
     * Gets a configuration property for this extension.
     *
     * @param key The key of the property.
     * @return An Optional containing the property value as a String, or empty if
     *         not found.
     */
    Optional<String> getProperty(String key);

    // Additional methods for sending specific frame types can be added here
    // void sendVideoFrame(VideoFrame frame);
    // void sendAudioFrame(AudioFrame frame);

    // Logging methods can be added here
    // void log(LogLevel level, String message);
}