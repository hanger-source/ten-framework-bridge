package com.tenframework.runtime.core;

import java.nio.ByteBuffer;

/**
 * Represents a generic data message.
 */
public interface Data extends Message {
    /**
     * Returns the binary payload of the data message.
     *
     * @return A {@link ByteBuffer} containing the data.
     */
    ByteBuffer getPayload();
}