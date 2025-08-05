package com.tenframework.runtime.app;

import com.tenframework.runtime.core.Command;
import com.tenframework.runtime.core.CommandResult;
import com.tenframework.runtime.core.MessageType;
import com.tenframework.runtime.engine.Engine;
import com.tenframework.runtime.plugin.AddonRegistry;
import com.tenframework.runtime.protocol.Protocol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppImplTest {

    @Mock
    private AddonRegistry mockRegistry;
    @Mock
    private Protocol mockProtocol;

    private App app;

    @BeforeEach
    void setUp() {
        app = new AppImpl(mockRegistry);
    }

    @Test
    void startEngine_shouldReturnCompletedFutureWithEngine() {
        // When
        CompletableFuture<Engine> engineFuture = app.startEngine("test-graph", null);

        // Then
        assertDoesNotThrow(() -> {
            Engine engine = engineFuture.get();
            assertNotNull(engine);
            assertNotNull(engine.getId());
        });
    }

    @Test
    void postCommand_withValidEngineId_shouldRouteToEngine() throws Exception {
        // Given
        Engine engine = app.startEngine("test-graph", null).get();
        // We need a spy to verify method calls on the real engine instance
        Engine spyEngine = spy(engine);
        // This is a hacky way to replace the engine in the app's map.
        // A real DI framework would make this cleaner.
        ((AppImpl) app).getEnginesForTest().put(engine.getId(), spyEngine);

        URI destination = new URI("engine://app/" + engine.getId());
        Command<CommandResult> command = new DummyCommand(destination);

        // When
        app.postCommand(command);

        // Then
        verify(spyEngine, timeout(1000).times(1)).postCommand(command);
    }

    @Test
    void listen_withKnownProtocol_shouldSucceed() {
        // Given
        String protocolName = "test-protocol";
        URI listenUri = URI.create("ws://localhost:8080");
        when(mockRegistry.getProtocol(protocolName)).thenReturn(mockProtocol);
        when(mockProtocol.listen(eq(listenUri), any())).thenReturn(CompletableFuture.completedFuture(null));

        // When
        CompletableFuture<Void> listenFuture = app.listen(protocolName, listenUri);

        // Then
        assertDoesNotThrow(() -> listenFuture.get());
        verify(mockProtocol).listen(eq(listenUri), any());
    }

    @Test
    void listen_withUnknownProtocol_shouldFail() {
        // Given
        String protocolName = "unknown-protocol";
        URI listenUri = URI.create("ws://localhost:8080");
        when(mockRegistry.getProtocol(protocolName)).thenReturn(null);

        // When
        CompletableFuture<Void> listenFuture = app.listen(protocolName, listenUri);

        // Then
        assertThrows(ExecutionException.class, listenFuture::get);
    }

    private static class DummyCommand implements Command<CommandResult> {
        private final CompletableFuture<CommandResult> future = new CompletableFuture<>();
        private final URI destination;

        DummyCommand(URI destination) {
            this.destination = destination;
        }

        @Override
        public UUID getCommandId() {
            return UUID.randomUUID();
        }

        @Override
        public Optional<String> getSequenceId() {
            return Optional.empty();
        }

        @Override
        public CompletableFuture<CommandResult> getResultFuture() {
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
            return Optional.ofNullable(destination);
        }
    }
}
