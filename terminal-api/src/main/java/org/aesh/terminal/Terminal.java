/*
 * JBoss, Home of Professional Open Source
 * Copyright 2014 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.aesh.terminal;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Consumer;

import org.aesh.terminal.tty.Signal;
import org.aesh.terminal.tty.Size;

/**
 * Terminal interface providing access to terminal functionality.
 */
public interface Terminal extends Closeable {

    /**
     * Return value indicating a read operation timed out without data.
     */
    int READ_EXPIRED = -2;

    /**
     * Get the terminal name.
     *
     * @return the terminal name
     */
    String getName();

    /**
     * Handler for terminal signals.
     */
    @FunctionalInterface
    interface SignalHandler {
        /**
         * Handle a terminal signal.
         *
         * @param signal the signal to handle
         */
        void handle(Signal signal);
    }

    /**
     * Register a signal handler for the specified signal.
     *
     * @param signal the signal to handle
     * @param handler the handler to register
     * @return the previous handler, or null if none
     */
    SignalHandler handle(Signal signal, SignalHandler handler);

    /**
     * Raise a signal on this terminal.
     *
     * @param signal the signal to raise
     */
    void raise(Signal signal);

    /**
     * Get the terminal input stream.
     *
     * @return the input stream
     */
    InputStream input();

    /**
     * Get the terminal output stream.
     *
     * @return the output stream
     */
    OutputStream output();

    /**
     * Get the current echo state of the terminal.
     *
     * @return true if echo is enabled
     */
    boolean echo();

    /**
     * Set the echo state of the terminal.
     *
     * @param echo true to enable echo
     * @return the previous echo state
     */
    boolean echo(boolean echo);

    /**
     * Get the terminal attributes.
     *
     * @return the terminal attributes
     */
    Attributes getAttributes();

    /**
     * Set the terminal attributes.
     *
     * @param attr the attributes to set
     */
    void setAttributes(Attributes attr);

    /**
     * Get the terminal size.
     *
     * @return the terminal size
     */
    Size getSize();

    /**
     * Get the terminal device for capability queries.
     *
     * @return the terminal device
     */
    Device device();

    /**
     * Get the code point consumer for this terminal.
     *
     * @return the code point consumer, or null if none
     */
    default Consumer<int[]> getCodePointConsumer() {
        return null;
    }

    /**
     * Whether this terminal supports non-blocking read operations with timeouts.
     * <p>
     * When this returns {@code true}, {@link #read(long)} and {@link #peek(long)}
     * provide timeout-based reads using platform-native mechanisms (e.g., {@code poll()}
     * on POSIX, {@code WaitForSingleObject} on Windows). When {@code false}, those
     * methods fall back to blocking reads via {@link #input()}.
     *
     * @return true if non-blocking reads are supported
     */
    default boolean supportsNonBlockingRead() {
        return false;
    }

    /**
     * Reads a single byte from the terminal input with a timeout.
     * <p>
     * If data is available, returns the byte value (0-255). If the end of
     * stream is reached, returns -1. If the timeout expires with no data
     * available, returns {@link #READ_EXPIRED} (-2).
     * <p>
     * The default implementation delegates to {@link #input()}{@code .read()}
     * (blocking, ignoring the timeout). Terminals that support non-blocking
     * reads should override this to use platform-native polling.
     *
     * @param timeoutMs timeout in milliseconds; 0 means non-blocking (return
     *        immediately), negative means block indefinitely
     * @return the byte read (0-255), -1 for EOF, or {@link #READ_EXPIRED} for timeout
     * @throws IOException if an I/O error occurs
     */
    default int read(long timeoutMs) throws IOException {
        return input().read();
    }

    /**
     * Peeks at the next byte from the terminal input without consuming it.
     * <p>
     * If data is available within the timeout, returns the byte value (0-255)
     * but leaves it available for the next {@link #read(long)} call. If the
     * end of stream is reached, returns -1. If the timeout expires with no
     * data, returns {@link #READ_EXPIRED} (-2).
     * <p>
     * The default implementation returns {@link #READ_EXPIRED} (no peek
     * support). Terminals that support non-blocking reads should override
     * this to provide true peek functionality.
     *
     * @param timeoutMs timeout in milliseconds; 0 means non-blocking
     * @return the byte peeked (0-255), -1 for EOF, or {@link #READ_EXPIRED} for timeout
     * @throws IOException if an I/O error occurs
     */
    default int peek(long timeoutMs) throws IOException {
        return READ_EXPIRED;
    }

    /**
     * Reads multiple bytes from the terminal input into a buffer with a timeout.
     * <p>
     * Reads the first byte using the specified timeout, then reads additional
     * bytes that are immediately available (non-blocking). Returns the total
     * number of bytes read, -1 for EOF, or {@link #READ_EXPIRED} if the
     * timeout expires with no data.
     * <p>
     * The default implementation delegates to {@link #input()}{@code .read(b, off, len)}
     * (blocking). Terminals that support non-blocking reads should override
     * this to use platform-native polling.
     *
     * @param b the buffer to read into
     * @param off the offset in the buffer
     * @param len the maximum number of bytes to read
     * @param timeoutMs timeout in milliseconds for the first byte
     * @return the number of bytes read, -1 for EOF, or {@link #READ_EXPIRED} for timeout
     * @throws IOException if an I/O error occurs
     */
    default int read(byte[] b, int off, int len, long timeoutMs) throws IOException {
        return input().read(b, off, len);
    }
}
