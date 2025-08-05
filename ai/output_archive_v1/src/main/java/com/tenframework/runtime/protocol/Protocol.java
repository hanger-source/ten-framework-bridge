package com.tenframework.runtime.protocol;

import com.tenframework.runtime.connection.Connection;
import com.tenframework.runtime.plugin.Addon;

import java.net.URI;
import java.util.concurrent.CompletableFuture;

/**
 * Defines a factory for creating and managing connections for a specific protocol.
 * <p>
 * A Protocol implementation, such as for WebSocket or TCP, acts as a listener
 * for incoming connections and a factory for client-side connections. It is
 * responsible for the low-level details of network communication, bootstrapping
 * connections, and handing them off to the application.
 * <p>
 * This interface extends {@link Addon}, making it discoverable via the
 * Java ServiceLoader mechanism.
 */
public interface Protocol extends Addon, AutoCloseable {

    /**
     * Returns a unique name for the protocol, e.g., "websocket", "tcp".
     * This name is used by the App to look up the protocol implementation.
     *
     * @return The unique protocol name.
     */
    String getName();

    /**
     * Asynchronously starts listening for incoming connections on a specified URI.
     * <p>
     * The implementation should bind to the local address and begin accepting
     * connections. For each successfully accepted connection, a {@link Connection}
     * object is created and passed to the App.
     *
     * @param listenUri         The local URI to bind to.
     * @param connectionHandler A consumer that will be invoked with a new
     *                          {@link Connection}
     *                          for each accepted client. It is the App's
     *                          responsibility
     *                          to manage the connection from this point forward.
     * @return A {@link CompletableFuture} that completes when the protocol has
     *         successfully started listening, or completes exceptionally on
     *         failure.
     */
    CompletableFuture<Void> listen(URI listenUri, java.util.function.Consumer<Connection> connectionHandler);

    /**
     * Asynchronously establishes an outbound connection to a remote endpoint.
     *
     * @param uri The URI of the remote server to connect to.
     * @return A {@link CompletableFuture} that will be completed with the
     *         established
     *         {@link Connection} on success, or completed exceptionally on failure.
     */
    CompletableFuture<Connection> connect(URI uri);

    /**
     * Closes the protocol listener and stops accepting new connections.
     * Any active connections created by this protocol are generally expected
     * to be managed and closed independently.
     */
    @Override
    void close();
}
