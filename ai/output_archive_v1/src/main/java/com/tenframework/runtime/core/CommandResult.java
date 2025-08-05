package com.tenframework.runtime.core;

import java.util.Optional;
import java.util.UUID;

/**
 * Represents the result of a {@link Command} execution.
 * <p>
 * A CommandResult is a message that provides feedback on a previously issued
 * command.
 * It contains status information and a reference to the original command.
 */
public interface CommandResult extends Message {

    /**
     * A status code indicating the outcome of the command execution.
     * This could be a simple success/failure enum or a more detailed set of codes.
     *
     * @return The status code.
     */
    int getStatusCode();

    /**
     * A human-readable detail message providing more context about the result,
     * especially in case of an error.
     *
     * @return An {@link Optional} containing the detail message.
     */
    Optional<String> getDetailMessage();

    /**
     * The unique ID of the original {@link Command} that this result corresponds
     * to.
     * This is crucial for correlating results with their requests.
     *
     * @return The UUID of the original command.
     */
    UUID getOriginalCommandId();

}