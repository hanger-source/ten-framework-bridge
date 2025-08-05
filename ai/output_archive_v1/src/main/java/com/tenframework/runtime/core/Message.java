package com.tenframework.runtime.core;

import java.net.URI;
import java.util.Optional;

/**
 * The base interface for all data units flowing through the TEN framework.
 * <p>
 * A message is an immutable object that represents a piece of information,
 * whether it's a command, a data frame, or a result. It provides fundamental
 * metadata for routing and identification.
 */
public interface Message {

    /**
     * Returns the specific type of this message.
     *
     * @return The {@link MessageType} enum constant.
     */
    MessageType getMessageType();

    /**
     * Gets the URI of the component that originated this message.
     * The source could be an App, an Engine, or an Extension.
     *
     * @return The source URI, or {@link Optional#empty()} if the source is unknown
     *         or internal.
     */
    Optional<URI> getSource();

    /**
     * Gets the URI of the intended recipient of this message.
     *
     * @return The destination URI, or {@link Optional#empty()} if the message is
     *         broadcast.
     */
    Optional<URI> getDestination();

}