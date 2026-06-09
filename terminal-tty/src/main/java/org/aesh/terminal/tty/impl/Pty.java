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
package org.aesh.terminal.tty.impl;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.aesh.terminal.Attributes;
import org.aesh.terminal.Terminal;
import org.aesh.terminal.tty.Size;

/**
 * Represents a pseudo-terminal (PTY) providing master/slave input and output streams.
 * A PTY allows a program to interact with a terminal device, providing both
 * the master side (for the controlling process) and the slave side (for the terminal).
 */
public interface Pty extends Closeable {

    /**
     * Returns the master side input stream.
     *
     * @return the master input stream
     */
    InputStream getMasterInput();

    /**
     * Returns the master side output stream.
     *
     * @return the master output stream
     */
    OutputStream getMasterOutput();

    /**
     * Returns the slave side input stream.
     *
     * @return the slave input stream
     */
    InputStream getSlaveInput();

    /**
     * Returns the slave side output stream.
     *
     * @return the slave output stream
     */
    OutputStream getSlaveOutput();

    /**
     * Returns the current terminal attributes.
     *
     * @return the terminal attributes
     * @throws IOException if an I/O error occurs
     */
    Attributes getAttr() throws IOException;

    /**
     * Sets the terminal attributes.
     *
     * @param attr the attributes to set
     * @throws IOException if an I/O error occurs
     */
    void setAttr(Attributes attr) throws IOException;

    /**
     * Returns the current terminal size.
     *
     * @return the terminal size
     * @throws IOException if an I/O error occurs
     */
    Size getSize() throws IOException;

    /**
     * Whether this PTY supports non-blocking read operations with timeouts.
     *
     * @return true if non-blocking reads are supported
     */
    default boolean supportsNonBlockingRead() {
        return false;
    }

    /**
     * Reads a single byte with a timeout.
     * <p>
     * Default implementation delegates to {@link #getSlaveInput()}{@code .read()},
     * ignoring the timeout (blocking).
     *
     * @param timeoutMs timeout in milliseconds; 0 for non-blocking, negative for infinite
     * @return the byte read (0-255), -1 for EOF, or {@link Terminal#READ_EXPIRED} for timeout
     * @throws IOException if an I/O error occurs
     */
    default int read(long timeoutMs) throws IOException {
        return getSlaveInput().read();
    }

    /**
     * Peeks at the next byte without consuming it, with a timeout.
     * <p>
     * Default implementation returns {@link Terminal#READ_EXPIRED} (no peek support).
     *
     * @param timeoutMs timeout in milliseconds; 0 for non-blocking
     * @return the byte peeked (0-255), -1 for EOF, or {@link Terminal#READ_EXPIRED} for timeout
     * @throws IOException if an I/O error occurs
     */
    default int peek(long timeoutMs) throws IOException {
        return Terminal.READ_EXPIRED;
    }

    /**
     * Reads multiple bytes with a timeout for the first byte.
     * <p>
     * After the first byte arrives (or timeout), reads additional bytes that
     * are immediately available without blocking.
     * <p>
     * Default implementation delegates to {@link #getSlaveInput()}{@code .read(b, off, len)},
     * ignoring the timeout (blocking).
     *
     * @param b the buffer to read into
     * @param off the offset in the buffer
     * @param len the maximum number of bytes to read
     * @param timeoutMs timeout in milliseconds for the first byte
     * @return the number of bytes read, -1 for EOF, or {@link Terminal#READ_EXPIRED} for timeout
     * @throws IOException if an I/O error occurs
     */
    default int read(byte[] b, int off, int len, long timeoutMs) throws IOException {
        return getSlaveInput().read(b, off, len);
    }

}
