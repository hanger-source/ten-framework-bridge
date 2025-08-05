package com.tenframework.runtime.connection;

import com.tenframework.runtime.core.Message;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * A placeholder implementation of a {@link Connection}.
 * <p>
 * A real implementation of this class would be backed by a networking framework
 * like Netty. It would manage a real socket, handle byte-level I/O, and drive
 * the serialization/deserialization process.
 */
public class ConnectionImpl implements Connection {

    private final URI remoteUri;
    private Consumer<Message> incomingMessageHandler;
    private volatile boolean active = true;

    public ConnectionImpl(URI remoteUri) {
        this.remoteUri = remoteUri;
    }

    @Override
    public URI getRemoteUri() {
        return remoteUri;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public CompletableFuture<Void> send(Message message) {
        if (!active) {
            return CompletableFuture.failedFuture(new IllegalStateException("Connection is not active."));
        }
        // In a real implementation:
        // 1. Serialize the message to bytes.
        // 2. Write the bytes to the underlying socket (e.g., Netty's Channel).
        // 3. Return the Future from the write operation.
        System.out.println("Sending message to " + remoteUri + ": " + message.getMessageType());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void setIncomingMessageHandler(Consumer<Message> handler) {
        this.incomingMessageHandler = handler;
    }

    @Override
    public CompletableFuture<Void> close() {
        if (!active) {
            return CompletableFuture.completedFuture(null);
        }
        active = false;
        System.out.println("Closing connection to " + remoteUri);
        // In a real implementation, this would close the actual socket.
        return CompletableFuture.completedFuture(null);
    }

    /**
     * This method would be called by the underlying networking framework (e.g., Netty)
     * when a full message has been decoded from the incoming byte stream.
     *
     * @param message The deserialized message.
     */
    protected void onMessageReceived(Message message) {
        if (incomingMessageHandler != null) {
            incomingMessageHandler.accept(message);
        }
    }
}
