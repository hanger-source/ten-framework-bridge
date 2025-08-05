package com.tenframework.runtime.engine;

import com.tenframework.runtime.core.Command;
import com.tenframework.runtime.core.CommandResult;
import com.tenframework.runtime.core.Message;

import java.io.Closeable;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Represents a logical execution unit, analogous to a graph instance in the
 * original ten-framework.
 * <p>
 * An Engine is a self-contained, single-threaded execution environment that
 * processes messages sequentially.
 * All interactions with an Engine and its internal state must be done
 * asynchronously through message passing
 * ({@link #postCommand(Command)} or {@link #postMessage(Message)}).
 * <p>
 * This design ensures thread safety without explicit locking within the
 * engine's domain logic,
 * mirroring the event-loop architecture of the original C framework.
 *
 * An Engine must be explicitly started via {@link #start()} and must be closed
 * via {@link #close()} to
 * release underlying resources like threads. It implements
 * {@link AutoCloseable} to support
 * try-with-resources patterns.
 */
public interface Engine extends AutoCloseable {

    /**
     * Gets the unique identifier of this engine instance.
     *
     * @return The UUID of the engine.
     */
    UUID getId();

    /**
     * Starts the engine's internal execution loop.
     * <p>
     * This method is idempotent. Calling it on an already started engine has no
     * effect.
     *
     * @return A CompletableFuture that completes when the engine is fully started
     *         and ready to process messages.
     */
    CompletableFuture<Void> start();

    /**
     * Asynchronously posts a command to the engine for execution and returns a
     * future for its result.
     * This is the primary mechanism for request-response interactions with the
     * engine.
     *
     * @param command The command to be executed.
     * @param <T>     The specific type of the command result.
     * @return A {@link CompletableFuture} that will be completed with the result of
     *         the command execution.
     */
    <T extends CommandResult> CompletableFuture<T> postCommand(Command<T> command);

    /**
     * Asynchronously posts a one-way message to the engine.
     * This is used for fire-and-forget interactions, such as sending data streams
     * or notifications
     * that do not require a direct response.
     *
     * @param message The message to be processed by the engine.
     */
    void postMessage(Message message);

    /**
     * Initiates a graceful shutdown of the engine.
     * <p>
     * The engine will finish processing any currently queued messages before
     * shutting down its execution thread.
     * New messages posted after shutdown initiation may be ignored.
     *
     * @return A CompletableFuture that completes when the engine has fully shut
     *         down and all resources are released.
     */
    @Override
    CompletableFuture<Void> close();
}
