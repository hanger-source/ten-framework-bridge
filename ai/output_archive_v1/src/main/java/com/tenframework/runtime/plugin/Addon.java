package com.tenframework.runtime.plugin;

/**
 * A marker interface for all dynamically loadable addons (plugins).
 * <p>
 * Framework components that are discoverable at runtime, such as Protocol
 * implementations, should implement a sub-interface of Addon. This allows them
 * to be loaded via Java's {@link java.util.ServiceLoader}.
 */
public interface Addon {
}
