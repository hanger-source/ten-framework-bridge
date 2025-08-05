package com.tenframework.runtime.core;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Represents a command to be executed within the framework.
 * <p>
 * A Command is a special type of {@link Message} that represents a request for
 * an operation to be performed. It is identifiable, can be part of a sequence,
 * and always has an associated future to hold its result.
 *
 * @param <T> The specific type of {@link CommandResult} expected for this
 *            command.
 */
public interface Command<T extends CommandResult> extends Message {

    /**
     * A unique identifier for this specific command instance, typically generated
     * by the framework for internal tracking and routing.
     *
     * @return The unique command ID.
     */
    UUID getCommandId();

    /**
     * An optional sequence identifier, typically provided by an external client,
     * to correlate a command with its result in an asynchronous conversation.
     *
     * @return An {@link Optional} containing the sequence ID, if provided.
     */
    Optional<String> getSequenceId();

    /**
     * Returns a {@link CompletableFuture} that will eventually be completed with
     * the result of this command's execution.
     * <p>
     * This is the primary mechanism for handling asynchronous responses in a
     * non-blocking manner.
     *
     * @return The future that will contain the command's result.
     */
    CompletableFuture<T> getResultFuture();
}