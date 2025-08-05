package com.tenframework.runtime.plugin;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A generic service for discovering and loading {@link Addon}s.
 * <p>
 * This class uses the standard Java {@link ServiceLoader} mechanism to find
 * addon implementations available on the classpath. To make an addon discoverable,
 * its fully qualified class name must be listed in a file located at
 * {@code META-INF/services/com.tenframework.runtime.plugin.Addon}.
 *
 * @param <T> The type of addon to load.
 */
public interface AddonLoader<T extends Addon> {

    /**
     * Loads all available addons of the specified type.
     *
     * @return A map where the keys are addon names and the values are the
     *         addon instances.
     */
    Map<String, T> loadAddons();

    /**
     * Creates a default addon loader for a given addon type.
     *
     * @param addonType The class of the addon to load (e.g., Protocol.class).
     * @param <T>       The type of the addon.
     * @return A new instance of a default addon loader.
     */
    static <T extends Addon> AddonLoader<T> create(Class<T> addonType) {
        return () -> {
            ServiceLoader<T> loader = ServiceLoader.load(addonType);
            return loader.stream()
                         .map(ServiceLoader.Provider::get)
                         .collect(Collectors.toMap(Addon::getName, Function.identity()));
        };
    }
}
