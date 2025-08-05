package com.tenframework.runtime.core;

/**
 * Defines the types of messages that can flow through the framework.
 * This is a direct mapping of the {@code TEN_MSG_TYPE} enum from the C core,
 * providing a type-safe way to handle different kinds of messages in Java.
 */
public enum MessageType {
    /**
     * An invalid or uninitialized message.
     */
    INVALID,

    /**
     * A generic, user-defined command that expects a result.
     * This is the primary vehicle for custom signaling in a real-time session.
     */
    COMMAND,

    /**
     * The result of a previously sent command.
     */
    COMMAND_RESULT,

    /**
     * A command to gracefully close the entire application.
     */
    COMMAND_CLOSE_APP,

    /**
     * A command to initialize and start a graph (e.g., a real-time session or
     * room).
     */
    COMMAND_START_GRAPH,

    /**
     * A command to stop and clean up a running graph.
     */
    COMMAND_STOP_GRAPH,

    /**
     * A command scheduled to be executed by a timer.
     */
    COMMAND_TIMER,

    /**
     * A message indicating that a command has timed out.
     */
    COMMAND_TIMEOUT,

    /**
     * A generic data message that does not necessarily expect a reply.
     */
    DATA,

    /**
     * A message carrying a single frame of video data.
     */
    VIDEO_FRAME,

    /**
     * A message carrying a single frame of audio data.
     */
    AUDIO_FRAME
}