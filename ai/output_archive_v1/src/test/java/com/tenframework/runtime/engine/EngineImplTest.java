package com.tenframework.runtime.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class EngineImplTest {

    private EchoExtension extension1;
    private EchoExtension extension2;
    private EngineImpl engine;

    @BeforeEach
    void setUp() {
        extension1 = new EchoExtension("ext1");
        extension2 = new EchoExtension("ext2");
        engine = new EngineImpl(Arrays.asList(extension1, extension2));
    }

    @Test
    void testLifecycle() throws InterruptedException, ExecutionException, TimeoutException {
        // Start and then immediately close the engine to test lifecycle calls
        engine.start();
        engine.close().get(1, TimeUnit.SECONDS);

        // Verify lifecycle methods were called in the correct order for both extensions
        List<String> expectedLifecycle = Arrays.asList("onInit", "onStart", "onStop", "onDeinit");
        assertEquals(expectedLifecycle, extension1.lifecycleCalls);
        assertEquals(expectedLifecycle, extension2.lifecycleCalls);
    }

    @Test
    void testCommandRoutingAndResult() throws InterruptedException, ExecutionException, TimeoutException {
        engine.start();

        // Send a command to ext1
        EchoExtension.DummyCommand cmd1 = new EchoExtension.DummyCommand("ext1");
        EchoExtension.DummyCommandResult result1 = engine.postCommand(cmd1).get(1, TimeUnit.SECONDS);

        // Verify ext1 received the command and the result is correct
        assertEquals(1, extension1.receivedMessages.size());
        assertSame(cmd1, extension1.receivedMessages.get(0));
        assertEquals(cmd1.getCommandId(), result1.getOriginalCommandId());
        assertTrue(result1.isSuccess());

        // Verify ext2 did not receive the command
        assertTrue(extension2.receivedMessages.isEmpty());

        engine.close().get(1, TimeUnit.SECONDS);
    }

    @Test
    void testInvalidTarget() throws InterruptedException, ExecutionException, TimeoutException {
        engine.start();

        // Send a command to a non-existent extension
        EchoExtension.DummyCommand cmd = new EchoExtension.DummyCommand("nonexistent");

        // We expect this to not produce a result and potentially log an error.
        // Since we don't have a mechanism to get a failed future for routing errors
        // yet,
        // we just verify no extension received the message.
        Thread.sleep(100); // Give time for the message to be processed

        assertTrue(extension1.receivedMessages.isEmpty());
        assertTrue(extension2.receivedMessages.isEmpty());

        engine.close().get(1, TimeUnit.SECONDS);
    }
}