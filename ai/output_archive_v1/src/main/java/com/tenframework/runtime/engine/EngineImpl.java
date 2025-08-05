package com.tenframework.runtime.engine;

import com.tenframework.runtime.core.*;
import com.tenframework.runtime.extension.EngineContext;
import com.tenframework.runtime.extension.Extension;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

public class EngineImpl implements Engine {

    private enum State {
        IDLE,
        INITIALIZING,
        RUNNING,
        STOPPING,
        TERMINATED
    }

    private final UUID id = UUID.randomUUID();
    private final ExecutorService executor;
    private final BlockingQueue<Message> messageQueue = new LinkedBlockingQueue<>();
    private final AtomicReference<State> state = new AtomicReference<>(State.IDLE);
    private final CompletableFuture<Void> terminationFuture = new CompletableFuture<>();

    // Extension and Command Management
    private final Map<String, Extension> extensions;
    private final Map<UUID, CompletableFuture<?>> pendingCommands = new ConcurrentHashMap<>();
    private final EngineContext context;

    public EngineImpl(List<Extension> extensionsToLoad) {
        this.executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "engine-" + id));
        this.extensions = extensionsToLoad.stream()
                .collect(Collectors.toConcurrentMap(Extension::getName, Function.identity()));
        this.context = new EngineContextImpl();
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public CompletableFuture<Void> start() {
        if (state.compareAndSet(State.IDLE, State.INITIALIZING)) {
            executor.submit(this::run);
            return CompletableFuture.completedFuture(null); // Simplified for now
        }
        return CompletableFuture.failedFuture(new IllegalStateException("Engine already started or terminated."));
    }

    @Override
    public <T extends CommandResult> CompletableFuture<T> postCommand(Command<T> command) {
        pendingCommands.put(command.getCommandId(), command.getResultFuture());
        messageQueue.offer(command);
        return command.getResultFuture();
    }

    @Override
    public void postMessage(Message message) {
        messageQueue.offer(message);
    }

    @Override
    public CompletableFuture<Void> close() {
        if (state.getAndSet(State.STOPPING) != State.TERMINATED) {
            // Offer a sentinel message to unblock the queue and trigger shutdown logic
            messageQueue.offer(new PoisonPill());
        }
        return terminationFuture;
    }

    private void run() {
        // Lifecycle: Init
        for (Extension ext : extensions.values()) {
            ext.onInit(context);
        }

        // Lifecycle: Start
        state.set(State.RUNNING);
        for (Extension ext : extensions.values()) {
            ext.onStart();
        }

        // Main Loop
        try {
            while (state.get() == State.RUNNING) {
                Message message = messageQueue.take();
                if (message instanceof PoisonPill) {
                    break;
                }
                processMessage(message);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            // Lifecycle: Stop
            for (Extension ext : extensions.values()) {
                ext.onStop();
            }
            // Lifecycle: Deinit
            for (Extension ext : extensions.values()) {
                ext.onDeinit();
            }
            state.set(State.TERMINATED);
            executor.shutdown();
            terminationFuture.complete(null);
        }
    }

    private void processMessage(Message message) {
        Optional<URI> destUri = message.getDestination();
        if (destUri.isEmpty()) {
            // Broadcast to all extensions? Or handle as engine-internal message?
            // For now, we do nothing.
            return;
        }

        // Simplified routing: URI path is "/<extension_name>"
        String path = destUri.get().getPath();
        if (path == null || path.length() <= 1)
            return;
        String targetName = path.substring(1);

        Extension target = extensions.get(targetName);
        if (target == null) {
            // Target not found, handle error (e.g., return error result for a command)
            return;
        }

        // Dispatch to the correct handler
        if (message instanceof Command) {
            target.onCmd((Command<?>) message);
        } else if (message instanceof Data) {
            target.onData((Data) message);
        } else if (message instanceof AudioFrame) {
            target.onAudioFrame((AudioFrame) message);
        } else if (message instanceof VideoFrame) {
            target.onVideoFrame((VideoFrame) message);
        }
    }

    // Inner class for the EngineContext implementation
    private class EngineContextImpl implements EngineContext {

        @Override
        public void sendCmd(Command<?> cmd) {
            postCommand(cmd);
        }

        @Override
        public void sendData(Data data) {
            postMessage(data);
        }

        @Override
        public void returnResult(CommandResult result) {
            CompletableFuture future = pendingCommands.remove(result.getOriginalCommandId());
            if (future != null) {
                future.complete(result);
            }
        }

        @Override
        public Optional<String> getProperty(String key) {
            // Simplified: properties could be loaded from a config file
            return Optional.empty();
        }
    }

    // Sentinel object to signal shutdown
    private static class PoisonPill implements Message {
        @Override
        public MessageType getMessageType() {
            return null;
        }

        @Override
        public Optional<URI> getSource() {
            return Optional.empty();
        }

        @Override
        public Optional<URI> getDestination() {
            return Optional.empty();
        }
    }
}