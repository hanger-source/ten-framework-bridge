package com.tenframework.runtime.app;

import com.tenframework.runtime.engine.Engine;
import java.util.concurrent.CompletableFuture;

/**
 * Represents the top-level container for the TEN Framework runtime.
 * An App manages the lifecycle of one or more {@link Engine} instances and
 * handles the overall configuration and resource management for the
 * application.
 */
public interface App {

    /**
     * Starts a new Engine instance based on a graph definition.
     * In a real implementation, this would likely take a configuration object
     * or a path to a graph definition file (e.g., a JSON file).
     *
     * @param graphName     A name for the graph to be started.
     * @param configuration Configuration for the graph, including the extensions to
     *                      load.
     * @return A CompletableFuture that completes with the newly created Engine
     *         instance.
     */
    CompletableFuture<Engine> startEngine(String graphName, Object configuration);

    /**
     * Shuts down the application and all running Engine instances gracefully.
     *
     * @return A CompletableFuture that completes when the application has fully
     *         shut down.
     */
    CompletableFuture<Void> close();
}