package com.tenframework.runtime.connection;

import com.tenframework.runtime.core.Message;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Represents a logical, stateful, and bidirectional communication channel.
 * <p>
 * A Connection is an abstraction over a network socket (e.g., TCP or WebSocket)
 * and is responsible for serializing outgoing {@link Message}s and deserializing
 * incoming data into Messages. It is a stateful object representing an active link.
 */
public interface Connection extends AutoCloseable {

    /**
     * Gets the remote URI of the endpoint this connection is linked to.
     *
     * @return The remote endpoint's URI.
     */
    URI getRemoteUri();

    /**
     * Checks if the connection is currently open and active.
     *
     * @return true if the connection is active, false otherwise.
     */
    boolean isActive();

    /**
     * Asynchronously sends a message over this connection.
     * <p>
     * The implementation will handle the serialization of the message into bytes
     * and writing it to the underlying network socket. The returned future
     * completes when the message has been successfully written to the transport layer.
     *
     * @param message The message to be sent.
     * @return A {@link CompletableFuture} that completes when the send operation is finished.
     */
    CompletableFuture<Void> send(Message message);

    /**
     * Sets a handler to process all incoming messages from this connection.
     * <p>
     * The connection's implementation will continuously read from the network,
     * deserialize the data into {@link Message} objects, and invoke the provided
     * handler for each complete message received. This handler will typically be
     * set by the App or Engine that owns the connection.
     *
     * @param handler The consumer that will process incoming messages.
     */
    void setIncomingMessageHandler(Consumer<Message> handler);

    /**
     * Asynchronously closes the connection and releases associated resources.
     * The returned future completes when the connection is fully terminated.
     *
     * @return A {@link CompletableFuture} that completes upon successful closure.
     */
    @Override
    CompletableFuture<Void> close();
}
