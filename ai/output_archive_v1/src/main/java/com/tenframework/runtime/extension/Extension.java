package com.tenframework.runtime.extension;

import com.tenframework.runtime.core.AudioFrame;
import com.tenframework.runtime.core.Command;
import com.tenframework.runtime.core.Data;
import com.tenframework.runtime.core.VideoFrame;

/**
 * Represents a business logic plugin that can be loaded and managed by an
 * Engine.
 * This interface defines the lifecycle and message handling hooks that the
 * Engine
 * will invoke on the extension. All methods are called on the Engine's single
 * execution thread, so implementations do not need to be thread-safe.
 */
public interface Extension {

    /**
     * Gets the unique instance name of this extension within a graph.
     * This name is used for routing messages.
     *
     * @return The unique instance name.
     */
    String getName();

    /**
     * Called when the extension is first loaded and initialized.
     * The extension should perform its one-time setup here.
     *
     * @param context The context for interacting with the host Engine.
     */
    void onInit(EngineContext context);

    /**
     * Called when the graph is started.
     * The extension can begin its processing or await messages.
     */
    void onStart();

    /**
     * Called when the graph is stopped.
     * The extension should cease processing and release runtime resources.
     */
    void onStop();

    /**
     * Called when the extension is about to be unloaded.
     * The extension should perform final cleanup here.
     */
    void onDeinit();

    /**
     * Called when a command message is routed to this extension.
     *
     * @param cmd The command message.
     */
    void onCmd(Command<?> cmd);

    /**
     * Called when a data message is routed to this extension.
     *
     * @param data The data message.
     */
    void onData(Data data);

    /**
     * Called when an audio frame is routed to this extension.
     *
     * @param frame The audio frame.
     */
    void onAudioFrame(AudioFrame frame);

    /**
     * Called when a video frame is routed to this extension.
     *
     * @param frame The video frame.
     */
    void onVideoFrame(VideoFrame frame);
}