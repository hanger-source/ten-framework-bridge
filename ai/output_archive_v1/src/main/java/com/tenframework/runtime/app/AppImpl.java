package com.tenframework.runtime.app;

import com.tenframework.runtime.engine.Engine;
import com.tenframework.runtime.engine.EngineImpl;
import com.tenframework.runtime.extension.Extension;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class AppImpl implements App {

    private final ConcurrentMap<UUID, Engine> engines = new ConcurrentHashMap<>();

    @Override
    public CompletableFuture<Engine> startEngine(String graphName, Object configuration) {
        return CompletableFuture.supplyAsync(() -> {
            // In a real implementation, 'configuration' would be parsed to load
            // the specified extensions. For now, we'll create an engine with an empty list.
            List<Extension> extensionsToLoad = Collections.emptyList();
            if (configuration instanceof List) {
                // A simplified way to pass extensions for testing
                extensionsToLoad = (List<Extension>) configuration;
            }

            Engine engine = new EngineImpl(extensionsToLoad);
            engines.put(engine.getId(), engine);
            engine.start();
            return engine;
        });
    }

    @Override
    public CompletableFuture<Void> close() {
        CompletableFuture<?>[] closeFutures = engines.values().stream()
                .map(Engine::close)
                .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(closeFutures).thenRun(engines::clear);
    }

    // For testing purposes
    int getRunningEngineCount() {
        return engines.size();
    }
}