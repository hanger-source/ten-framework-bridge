package com.tenframework.runtime.plugin;

import com.tenframework.runtime.protocol.Protocol;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * A registry for discovering and managing all runtime-discoverable {@link Addon}s.
 * <p>
 * This class uses the Java {@link ServiceLoader} to find and instantiate
 * available addon implementations (like Protocols) at runtime.
 */
public class AddonRegistry {

    private final Map<String, Protocol> protocolAddons;

    /**
     * Creates a new AddonRegistry and immediately loads all discoverable addons.
     */
    public AddonRegistry() {
        this.protocolAddons = loadAddons(Protocol.class);
    }

    /**
     * Finds a registered {@link Protocol} addon by its unique name.
     *
     * @param name The name of the protocol (e.g., "websocket").
     * @return The {@link Protocol} implementation, or null if not found.
     */
    public Protocol getProtocol(String name) {
        return protocolAddons.get(name);
    }

    private <T extends Addon> Map<String, T> loadAddons(Class<T> addonType) {
        ServiceLoader<T> loader = ServiceLoader.load(addonType);
        return StreamSupport.stream(loader.spliterator(), false)
                .collect(Collectors.toConcurrentMap(
                        addon -> {
                            // This is a bit of a simplification. We're assuming the addon
                            // has a getName() method. A more robust solution might use
                            // annotations or a more specific addon sub-interface.
                            // For Protocol, we've added getName().
                            try {
                                return (String) addonType.getMethod("getName").invoke(addon);
                            } catch (Exception e) {
                                throw new IllegalStateException("Failed to get name for addon: " + addon.getClass(), e);
                            }
                        },
                        addon -> addon
                ));
    }
}
