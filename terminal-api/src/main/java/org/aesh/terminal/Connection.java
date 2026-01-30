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

import java.nio.charset.Charset;
import java.util.EnumSet;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

import org.aesh.terminal.tty.Capability;
import org.aesh.terminal.tty.Point;
import org.aesh.terminal.tty.Signal;
import org.aesh.terminal.tty.Size;
import org.aesh.terminal.utils.ANSI;
import org.aesh.terminal.utils.ColorDepth;
import org.aesh.terminal.utils.TerminalColorCapability;

/**
 * Represent a connection to either a local/direct/remote Terminal.
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public interface Connection extends AutoCloseable {

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

    /**
     * Get the size handler.
     *
     * @return Handler that's called when the terminal changes size
     */
    Consumer<Size> getSizeHandler();

    /**
     * Specify size handler that's called when the terminal changes size.
     *
     * @param handler the size change handler
     */
    void setSizeHandler(Consumer<Size> handler);

    /**
     * Get SignalHandler. A handler that's called when a Signal is sent to the terminal
     *
     * @return Signal handler
     */
    Consumer<Signal> getSignalHandler();

    /**
     * Specify the signal handler.
     * A handler that's called when a Signal is sent to the terminal
     *
     * @param handler signal handler
     */
    void setSignalHandler(Consumer<Signal> handler);

    /**
     * Get the standard input handler.
     *
     * @return the stdin handler that processes input as code point arrays
     */
    Consumer<int[]> getStdinHandler();

    /**
     * Set the standard input handler.
     *
     * @param handler the handler to process input as code point arrays
     */
    void setStdinHandler(Consumer<int[]> handler);

    /**
     * Handler that's called for all output
     *
     * @return output handler
     */
    Consumer<int[]> stdoutHandler();

    /**
     * Specify handler that's called when the input stream is closed.
     *
     * @param closeHandler handler
     */
    void setCloseHandler(Consumer<Void> closeHandler);

    /**
     * Get the close handler.
     *
     * @return handler thats called when the input stream is closed.
     */
    Consumer<Void> getCloseHandler();

    /**
     * Stop reading from the input stream.
     * The stream will be closed and cleanup methods will be called
     * Eg for terminals they will be restored to their original settings.
     *
     * Note that if the reader thread is blocking waiting for data it will wait until either
     * killed or if the input stream is closed.
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

    /**
     * Start reading from the input stream using the current thread.
     * The current thread will be blocked while reading/waiting to read from the stream
     */
    void openBlocking();

    /**
     * Start reading from the input stream in a separate thread.
     * The current thread will continue.
     */
    void openNonBlocking();

    /**
     * Specify terminal settings
     *
     * @param capability capability
     * @param params parameters
     * @return true if the terminal accepted the settings
     */
    boolean put(Capability capability, Object... params);

    /**
     * Get the current terminal attributes.
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

    /**
     * Check if this terminal supports ANSI escape sequences.
     *
     * @return true if ANSI is supported, false otherwise
     */
    boolean supportsAnsi();

    /**
     * Write a string to the output handler
     *
     * @param s string
     * @return this connection
     */
    default Connection write(String s) {
        int[] codePoints = s.codePoints().toArray();
        stdoutHandler().accept(codePoints);
        return this;
    }

    /**
     * Enter raw mode for the terminal.
     * <p>
     * In raw mode, input is not line-buffered, echo is disabled, and special
     * character processing is turned off. This allows reading individual
     * keystrokes as they are typed.
     *
     * @return the previous terminal attributes (to restore later)
     */
    default Attributes enterRawMode() {
        Attributes prvAttr = getAttributes();
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
     * Get the current cursor position in the terminal.
     * <p>
     * This method sends a cursor position query to the terminal and waits
     * for the response. The terminal must be actively reading input for
     * this to work.
     *
     * @return the current cursor position as a Point (row, column)
     */
    default Point getCursorPosition() {
        Consumer<int[]> prevInputHandler = getStdinHandler();
        CountDownLatch latch = new CountDownLatch(1);
        final Point[] p = { null };
        Attributes attributes = enterRawMode();
        setStdinHandler(ints -> {
            p[0] = ANSI.getActualCursor(ints);
            setStdinHandler(prevInputHandler);
            latch.countDown();
            setAttributes(attributes);
        });
        stdoutHandler().accept("\u001B[6n".codePoints().toArray());
        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return p[0];
    }

    /**
     * Send a query to the terminal and wait for a response with timeout.
     * <p>
     * This method enters raw mode, sends the query, collects the response,
     * and restores the original terminal attributes. It's useful for OSC queries
     * and other terminal interrogation sequences.
     * <p>
     * Note: This method requires the connection to be actively reading input
     * (via {@link #openBlocking()} or {@link #openNonBlocking()}).
     *
     * @param query the query sequence to send
     * @param timeoutMs timeout in milliseconds to wait for response
     * @param responseParser function to parse the response; should return non-null when
     *        a complete response is received, null to continue waiting
     * @param <T> the type of the parsed response
     * @return the parsed response, or null if timeout or parsing failed
     */
    default <T> T queryTerminal(String query, long timeoutMs,
            java.util.function.Function<int[], T> responseParser) {
        if (!supportsAnsi()) {
            return null;
        }

        // Check if OSC queries are supported
        if (device() != null && !device().supportsOscQueries()) {
            return null;
        }

        Consumer<int[]> prevInputHandler = getStdinHandler();
        CountDownLatch latch = new CountDownLatch(1);
        @SuppressWarnings("unchecked")
        final Object[] result = { null };
        Attributes savedAttributes = enterRawMode();

        setStdinHandler(ints -> {
            T parsed = responseParser.apply(ints);
            if (parsed != null) {
                result[0] = parsed;
                latch.countDown();
            }
        });

        try {
            stdoutHandler().accept(query.codePoints().toArray());
            latch.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            setStdinHandler(prevInputHandler);
            setAttributes(savedAttributes);
        }

        @SuppressWarnings("unchecked")
        T typedResult = (T) result[0];
        return typedResult;
    }

    /**
     * Check if OSC (Operating System Command) queries are supported.
     * <p>
     * This checks both the device type and environment variables to determine
     * if OSC queries like color detection are likely to work.
     *
     * @return true if OSC queries are likely supported
     */
    default boolean supportsOscQueries() {
        // First check device
        if (device() != null && !device().supportsOscQueries()) {
            return false;
        }

        // Also check environment for terminal multiplexers
        String termProgram = System.getenv("TERM_PROGRAM");
        if (termProgram != null) {
            String lower = termProgram.toLowerCase();
            if (lower.equals("tmux") || lower.equals("screen")) {
                return false;
            }
        }

        return supportsAnsi();
    }

    /**
     * Get the color depth of this terminal connection.
     * <p>
     * This method uses the device's max_colors capability if available,
     * otherwise falls back to environment variable detection.
     *
     * @return the detected color depth
     */
    default ColorDepth getColorDepth() {
        // Check terminfo max_colors capability first
        if (device() != null) {
            Integer maxColors = device().getNumericCapability(Capability.max_colors);
            if (maxColors != null) {
                return ColorDepth.fromColorCount(maxColors);
            }
        }

        // Fall back to environment detection
        return TerminalColorCapability.detectColorDepthFromEnvironment();
    }

    /**
     * Get the color capabilities of this terminal connection.
     * <p>
     * This is a fast, non-blocking operation that uses environment variables
     * and terminfo data. For more accurate color detection including
     * background theme detection via OSC queries, use the
     * {@code TerminalColorDetector} class in the readline module.
     *
     * @return the detected color capabilities
     */
    default TerminalColorCapability getColorCapability() {
        ColorDepth depth = getColorDepth();
        return new TerminalColorCapability(depth, TerminalColorCapability.detectThemeFromEnvironment());
    }

}
