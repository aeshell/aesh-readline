/*
 * JBoss, Home of Professional Open Source
 * Copyright 2017 Red Hat Inc. and/or its affiliates and other contributors
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

import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.EnumSet;
import java.util.function.Consumer;

import org.aesh.terminal.io.Encoder;
import org.aesh.terminal.tty.Capability;
import org.aesh.terminal.tty.Signal;
import org.aesh.terminal.tty.Size;
import org.aesh.terminal.utils.Parser;
import org.aesh.terminal.utils.TerminalTheme;

/**
 * Represent a connection to either a local/direct/remote Terminal.
 * <p>
 * This interface provides core I/O operations: input/output handlers,
 * encoding, lifecycle (open/close), and terminal state (attributes, size).
 * <p>
 * Advanced terminal features (queries, capability detection, semantic output,
 * mode toggles) are accessible via {@link #terminal()}, which returns a
 * {@link TerminalFeatures} object.
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public interface Connection extends Appendable, AutoCloseable {

    // ==================== Identity ====================

    /**
     * Get the device associated with this connection.
     *
     * @return type of terminal
     */
    Device device();

    /**
     * Get the current terminal size.
     *
     * @return terminal size
     */
    Size size();

    // ==================== Input ====================

    /**
     * Get the standard input handler.
     *
     * @return the stdin handler that processes input as code point arrays
     */
    Consumer<int[]> stdinHandler();

    /**
     * Set the standard input handler.
     *
     * @param handler the handler to process input as code point arrays
     */
    void setStdinHandler(Consumer<int[]> handler);

    // ==================== Output ====================

    /**
     * Handler that's called for all output.
     *
     * @return output handler
     */
    Consumer<int[]> stdoutHandler();

    /**
     * Write a string to the output handler.
     * When the stdout handler is an Encoder, this uses a fast path that
     * encodes the String directly to bytes without an intermediate int[] allocation.
     *
     * @param s string
     * @return this connection
     */
    default Connection write(String s) {
        Consumer<int[]> handler = stdoutHandler();
        if (handler instanceof Encoder) {
            ((Encoder) handler).accept(s);
        } else {
            handler.accept(Parser.toCodePoints(s));
        }
        return this;
    }

    /**
     * Specify terminal settings.
     *
     * @param capability capability
     * @param params parameters
     * @return true if the terminal accepted the settings
     */
    boolean put(Capability capability, Object... params);

    // ==================== Handlers ====================

    /**
     * Get the signal handler.
     *
     * @return handler that's called when a Signal is sent to the terminal
     */
    Consumer<Signal> signalHandler();

    /**
     * Specify the signal handler.
     *
     * @param handler signal handler
     */
    void setSignalHandler(Consumer<Signal> handler);

    /**
     * Get the size handler.
     *
     * @return handler that's called when the terminal changes size
     */
    Consumer<Size> sizeHandler();

    /**
     * Specify size handler that's called when the terminal changes size.
     *
     * @param handler the size change handler
     */
    void setSizeHandler(Consumer<Size> handler);

    /**
     * Get the close handler.
     *
     * @return handler that's called when the input stream is closed
     */
    Consumer<Void> closeHandler();

    /**
     * Specify handler that's called when the input stream is closed.
     *
     * @param closeHandler handler
     */
    void setCloseHandler(Consumer<Void> closeHandler);

    /**
     * Set a handler to be called when the terminal's theme changes.
     *
     * @param handler the handler to invoke with the new theme, or null to remove
     */
    default void setThemeChangeHandler(Consumer<TerminalTheme> handler) {
        // Default no-op; AbstractConnection delegates to EventDecoder
    }

    /**
     * Get the current theme change handler.
     *
     * @return the theme change handler, or null if not set
     */
    default Consumer<TerminalTheme> themeChangeHandler() {
        return null;
    }

    // ==================== Lifecycle ====================

    /**
     * Start reading from the input stream using the current thread.
     * The current thread will be blocked while reading/waiting to read from the stream.
     */
    void openBlocking();

    /**
     * Start reading from the input stream in a separate thread.
     * The current thread will continue.
     */
    void openNonBlocking();

    /**
     * Check if the connection is actively reading from the input stream.
     *
     * @return true if the connection is actively reading input
     */
    default boolean reading() {
        return false;
    }

    /**
     * Stop reading from the input stream.
     * The stream will be closed and cleanup methods will be called.
     * Eg for terminals they will be restored to their original settings.
     */
    @Override
    void close();

    /**
     * Close the connection with an exit code.
     *
     * @param exit the exit code
     */
    default void close(int exit) {
        close();
    }

    // ==================== Terminal State ====================

    /**
     * Get the current terminal attributes.
     *
     * @return the terminal attributes
     */
    Attributes attributes();

    /**
     * Set the terminal attributes.
     *
     * @param attr the attributes to set
     */
    void setAttributes(Attributes attr);

    /**
     * Enter raw mode for the terminal.
     * <p>
     * In raw mode, input is not line-buffered, echo is disabled, and special
     * character processing is turned off.
     *
     * @return the previous terminal attributes (to restore later)
     */
    default Attributes enterRawMode() {
        Attributes prvAttr = attributes();
        Attributes newAttr = new Attributes(prvAttr);
        newAttr.setLocalFlags(EnumSet.of(Attributes.LocalFlag.ICANON, Attributes.LocalFlag.ECHO, Attributes.LocalFlag.IEXTEN),
                false);
        newAttr.setInputFlags(EnumSet.of(Attributes.InputFlag.IXON, Attributes.InputFlag.ICRNL, Attributes.InputFlag.INLCR),
                false);
        newAttr.setControlChar(Attributes.ControlChar.VMIN, 1);
        newAttr.setControlChar(Attributes.ControlChar.VTIME, 0);
        newAttr.setControlChar(Attributes.ControlChar.VINTR, 0);
        setAttributes(newAttr);
        return prvAttr;
    }

    /**
     * Check if this terminal supports ANSI escape sequences.
     *
     * @return true if ANSI is supported, false otherwise
     */
    boolean supportsAnsi();

    /**
     * Get the input character encoding.
     *
     * @return the charset used for input encoding
     */
    Charset inputEncoding();

    /**
     * Get the output character encoding.
     *
     * @return the charset used for output encoding
     */
    Charset outputEncoding();

    // ==================== Appendable ====================

    @Override
    default Appendable append(char c) {
        return write(String.valueOf(c));
    }

    @Override
    default Appendable append(CharSequence csq) {
        return write(csq.toString());
    }

    @Override
    default Appendable append(CharSequence csq, int start, int end) {
        return write(csq.subSequence(start, end).toString());
    }

    // ==================== Advanced Features ====================

    /**
     * Access advanced terminal features: queries, capability detection,
     * semantic output, and mode management.
     *
     * @return the terminal features for this connection
     */
    default TerminalFeatures terminal() {
        return new TerminalFeatures(this);
    }

    // ==================== Standard I/O Adapters ====================

    /**
     * Return a {@link java.io.Writer} view of this connection.
     * Writes to the returned Writer are forwarded to this connection's output.
     *
     * @return a Writer that delegates to this connection
     */
    default Writer asWriter() {
        return new ConnectionWriter(this);
    }

    /**
     * Return a {@link java.io.PrintWriter} view of this connection.
     * Useful for formatted output (printf, println, etc.).
     *
     * @return a PrintWriter that delegates to this connection
     */
    default PrintWriter asPrintWriter() {
        return new PrintWriter(asWriter(), true);
    }
}
