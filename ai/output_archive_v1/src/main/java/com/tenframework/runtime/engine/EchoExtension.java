package com.tenframework.runtime.engine;

import com.tenframework.runtime.core.*;
import com.tenframework.runtime.extension.EngineContext;
import com.tenframework.runtime.extension.Extension;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * A mock Extension for testing purposes. It records the lifecycle calls and
 * messages it receives.
 * If it receives a command, it echoes back a successful result.
 */
class EchoExtension implements Extension {

    private final String name;
    public final List<String> lifecycleCalls = new ArrayList<>();
    public final List<Message> receivedMessages = new ArrayList<>();
    private EngineContext context;

    public EchoExtension(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void onInit(EngineContext context) {
        this.context = context;
        lifecycleCalls.add("onInit");
    }

    @Override
    public void onStart() {
        lifecycleCalls.add("onStart");
    }

    @Override
    public void onStop() {
        lifecycleCalls.add("onStop");
    }

    @Override
    public void onDeinit() {
        lifecycleCalls.add("onDeinit");
    }

    @Override
    public void onCmd(Command<?> cmd) {
        receivedMessages.add(cmd);
        // Echo back a successful result
        context.returnResult(new DummyCommandResult(cmd.getCommandId()));
    }

    @Override
    public void onData(Data data) {
        receivedMessages.add(data);
    }

    @Override
    public void onAudioFrame(AudioFrame frame) {
        receivedMessages.add(frame);
    }

    @Override
    public void onVideoFrame(VideoFrame frame) {
        receivedMessages.add(frame);
    }

    // Helper classes for testing
    static class DummyCommand implements Command<DummyCommandResult> {
        private final UUID id = UUID.randomUUID();
        private final CompletableFuture<DummyCommandResult> future = new CompletableFuture<>();
        private final URI destination;

        public DummyCommand(String destExtensionName) {
            this.destination = URI.create("engine://local/" + destExtensionName);
        }

        @Override
        public UUID getCommandId() {
            return id;
        }

        @Override
        public Optional<String> getSequenceId() {
            return Optional.empty();
        }

        @Override
        public CompletableFuture<DummyCommandResult> getResultFuture() {
            return future;
        }

        @Override
        public MessageType getMessageType() {
            return MessageType.COMMAND;
        }

        @Override
        public Optional<URI> getSource() {
            return Optional.empty();
        }

        @Override
        public Optional<URI> getDestination() {
            return Optional.of(destination);
        }
    }

    static class DummyCommandResult implements CommandResult {
        private final UUID originalId;

        public DummyCommandResult(UUID originalId) {
            this.originalId = originalId;
        }

        @Override
        public int getStatusCode() {
            return 0;
        }

        @Override
        public Optional<String> getDetailMessage() {
            return Optional.of("Success");
        }

        @Override
        public UUID getOriginalCommandId() {
            return originalId;
        }

        @Override
        public MessageType getMessageType() {
            return MessageType.COMMAND_RESULT;
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