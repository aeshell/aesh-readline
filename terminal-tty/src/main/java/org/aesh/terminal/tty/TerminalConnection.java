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
package org.aesh.terminal.tty;

import static org.aesh.terminal.Terminal.READ_EXPIRED;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.aesh.terminal.AbstractConnection;
import org.aesh.terminal.Attributes;
import org.aesh.terminal.Connection;
import org.aesh.terminal.Device;
import org.aesh.terminal.EventDecoder;
import org.aesh.terminal.Terminal;
import org.aesh.terminal.io.Decoder;
import org.aesh.terminal.io.Encoder;
import org.aesh.terminal.tty.impl.ExternalTerminal;
import org.aesh.terminal.tty.impl.PosixSysTerminal;
import org.aesh.terminal.tty.impl.WinSysTerminal;
import org.aesh.terminal.utils.ANSI;
import org.aesh.terminal.utils.LoggerUtil;
import org.aesh.terminal.utils.OSUtils;

/**
 * Implementation of Connection meant for local terminal connections.
 *
 * @author <a href="mailto:spederse@redhat.com">Stale W. Pedersen</a>
 */
public class TerminalConnection extends AbstractConnection {

    private final Charset inputCharset;
    private final Charset outputCharset;
    private Terminal terminal;

    private static final Logger LOGGER = LoggerUtil.getLogger(TerminalConnection.class.getName());

    private Decoder decoder;
    private Consumer<Connection> handler;
    private CountDownLatch latch;
    private volatile boolean waiting = false;
    private volatile boolean closed = false;
    private Terminal.SignalHandler prevIntrHandler;
    private Terminal.SignalHandler prevWincHandler;
    private Terminal.SignalHandler prevContHandler;
    private boolean ansi = true;

    /**
     * Creates a new TerminalConnection with the specified charsets, streams, and handler.
     *
     * @param inputCharset the charset for input encoding, or null to use the default charset
     * @param outputCharset the charset for output encoding, or null to use the default charset
     * @param inputStream the input stream for the terminal
     * @param outputStream the output stream for the terminal
     * @param handler the connection handler to be called when the connection is initialized
     * @throws IOException if an I/O error occurs
     */
    public TerminalConnection(Charset inputCharset, Charset outputCharset, InputStream inputStream,
            OutputStream outputStream, Consumer<Connection> handler) throws IOException {
        this.handler = handler;
        Terminal terminal = TerminalBuilder.builder()
                .input(inputStream)
                .output(outputStream)
                .nativeSignals(true)
                .name("Aesh console")
                .build();
        if (inputCharset != null)
            this.inputCharset = inputCharset;
        else
            this.inputCharset = defaultConnectionCharset(terminal, OSUtils.IS_CYGWIN);
        if (outputCharset != null)
            this.outputCharset = outputCharset;
        else
            this.outputCharset = defaultConnectionCharset(terminal, OSUtils.IS_CYGWIN);
        init(terminal);
    }

    /**
     * Creates a new TerminalConnection with the specified charset, streams, and handler.
     *
     * @param charset the charset for both input and output encoding
     * @param inputStream the input stream for the terminal
     * @param outputStream the output stream for the terminal
     * @param handler the connection handler to be called when the connection is initialized
     * @throws IOException if an I/O error occurs
     */
    public TerminalConnection(Charset charset, InputStream inputStream,
            OutputStream outputStream, Consumer<Connection> handler) throws IOException {
        this(charset, charset, inputStream, outputStream, handler);
    }

    /**
     * Creates a new TerminalConnection with the specified charset and streams.
     *
     * @param charset the charset for both input and output encoding
     * @param inputStream the input stream for the terminal
     * @param outputStream the output stream for the terminal
     * @throws IOException if an I/O error occurs
     */
    public TerminalConnection(Charset charset, InputStream inputStream, OutputStream outputStream) throws IOException {
        this(charset, charset, inputStream, outputStream, null);
    }

    /**
     * Creates a new TerminalConnection using system default charset and standard I/O streams.
     *
     * @throws IOException if an I/O error occurs
     */
    public TerminalConnection() throws IOException {
        this(Charset.defaultCharset(), System.in, System.out);
    }

    /**
     * Creates a new TerminalConnection using system defaults with a connection handler.
     *
     * @param handler the connection handler to be called when the connection is initialized
     * @throws IOException if an I/O error occurs
     */
    public TerminalConnection(Consumer<Connection> handler) throws IOException {
        this(Charset.defaultCharset(), Charset.defaultCharset(), System.in, System.out, handler);
    }

    /**
     * Creates a new TerminalConnection wrapping an existing Terminal.
     *
     * @param terminal the terminal to wrap
     */
    public TerminalConnection(Terminal terminal) {
        this.inputCharset = defaultConnectionCharset(terminal, OSUtils.IS_CYGWIN);
        this.outputCharset = defaultConnectionCharset(terminal, OSUtils.IS_CYGWIN);
        init(terminal);
    }

    private void init(Terminal term) {
        this.terminal = term;
        attributes = this.terminal.getAttributes();
        //interrupt signal
        prevIntrHandler = this.terminal.handle(Signal.INT, s -> {
            if (signalHandler() != null) {
                signalHandler().accept(s);
            } else {
                LOGGER.log(Level.FINE, "No signal handler is registered, lets stop");
                close();
            }
        });
        prevContHandler = this.terminal.handle(Signal.CONT, s -> {
            if (signalHandler() != null)
                signalHandler().accept(s);
        });
        //window resize signal
        prevWincHandler = this.terminal.handle(Signal.WINCH, s -> {
            // Resize split screen first (recalculates layout, redraws separator)
            if (splitScreenImpl != null && !splitScreenImpl.isClosed()) {
                splitScreenImpl.handleResize(terminal.getSize());
            }
            if (sizeHandler() != null) {
                sizeHandler().accept(size());
            }
        });

        eventDecoder = new EventDecoder(attributes);
        decoder = new Decoder(512, inputEncoding(), eventDecoder);

        if (terminal.getCodePointConsumer() == null) {
            stdout = new Encoder(outputEncoding(), this::writeBytes);
        } else {
            stdout = terminal.getCodePointConsumer();
        }
        if (terminal instanceof ExternalTerminal)
            ansi = false;
        // Suppress ANSI output when stdout is not a TTY (redirected to file/pipe).
        // Standard POSIX behavior: programs check isatty(STDOUT_FILENO) to decide
        // whether to emit escape sequences.
        if (!TtyDetect.isStdoutTty())
            ansi = false;

        if (handler != null)
            handler.accept(this);
    }

    @Override
    public void openNonBlocking() {
        ExecutorService executorService = Executors.newSingleThreadExecutor(runnable -> {
            Thread inputThread = Executors.defaultThreadFactory().newThread(runnable);
            inputThread.setName("Aesh InputStream Reader");
            //need to be a daemon, if not it will block on shutdown
            inputThread.setDaemon(true);
            return inputThread;
        });
        executorService.execute(this::openBlocking);
    }

    @Override
    public boolean put(Capability capability, Object... params) {
        return terminal.device().puts(stdoutHandler(), capability, params);
    }

    @Override
    public Attributes attributes() {
        return terminal.getAttributes();
    }

    @Override
    public void setAttributes(Attributes attr) {
        terminal.setAttributes(attr);
    }

    @Override
    public Charset inputEncoding() {
        return inputCharset;
    }

    @Override
    public Charset outputEncoding() {
        return outputCharset;
    }

    @Override
    public boolean supportsAnsi() {
        return ansi;
    }

    @Override
    public boolean isInteractive() {
        return TtyDetect.isStdinTty();
    }

    @Override
    public void setMouseHandler(java.util.function.Consumer<MouseEvent> handler) {
        super.setMouseHandler(handler);
        // On Windows, enable/disable ENABLE_MOUSE_INPUT on the console
        if (terminal instanceof WinSysTerminal) {
            ((WinSysTerminal) terminal).setMouseHandler(handler);
        }
    }

    /**
     * Opens the Connection stream, this method will block and wait for input.
     */
    @Override
    public void openBlocking() {
        openBlocking(null);
    }

    /** Default poll timeout (ms) for the non-blocking read loop. */
    private static final int POLL_TIMEOUT_MS = 100;
    /**
     * Poll interval (ms) for the legacy read loop. Bounds the added input
     * latency (~half the interval on average) and the delay between close()
     * and reader-thread exit, while keeping idle CPU at one non-blocking
     * syscall per interval.
     */
    private static final int LEGACY_POLL_INTERVAL_MS = 10;

    /**
     * Opens the Connection stream with an initial buffer. This method will block and wait for input.
     *
     * @param buffer initial data to process before reading from the terminal input
     */
    public void openBlocking(String buffer) {
        if (terminal.supportsNonBlockingRead()) {
            openBlockingWithPoll(buffer);
        } else {
            openBlockingLegacy(buffer);
        }
    }

    /**
     * Non-blocking read loop using poll() with timeout (Java 22+ with FFM).
     * <p>
     * Uses {@link Terminal#read(byte[], int, int, long)} with a poll timeout so the
     * loop naturally yields control on each timeout, enabling clean shutdown without
     * closing the file descriptor and supporting future features like printAbove().
     */
    private void openBlockingWithPoll(String buffer) {
        try {
            reading = true;
            byte[] bBuf = new byte[1024];
            if (buffer != null) {
                decoder.write(buffer.getBytes(inputCharset));
            }
            while (reading) {
                if (waiting) {
                    // When suspended, poll with short timeout instead of latch.await().
                    // This avoids blocking the thread while still being responsive.
                    try {
                        Thread.sleep(POLL_TIMEOUT_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        close();
                        break;
                    }
                    continue;
                }

                int read = terminal.read(bBuf, 0, bBuf.length, POLL_TIMEOUT_MS);
                if (read == READ_EXPIRED) {
                    // Timeout — loop back to check reading flag, handle timers, etc.
                    continue;
                } else if (read > 0) {
                    decoder.write(bBuf, 0, read);
                } else if (read < 0) {
                    // EOF
                    close();
                }
            }
        } catch (IOException ioe) {
            LOGGER.log(Level.WARNING, "Failed while reading, exiting", ioe);
            close();
        }
    }

    /**
     * Legacy read loop for terminals without non-blocking read support
     * (Java 8-21, non-FFM terminals).
     * <p>
     * Polls {@link InputStream#available()} instead of blocking in
     * {@link InputStream#read(byte[])} so {@link #close()} — which only flips
     * the {@code reading} flag — stops the loop within one poll interval.
     * The underlying stream is never closed here: it may be JVM-global state
     * such as {@code System.in}, and on some platforms (macOS) closing a tty
     * fd while another thread reads from it blocks the closer indefinitely.
     * While suspended, bytes are left in the kernel buffer until awake().
     */
    private void openBlockingLegacy(String buffer) {
        try {
            reading = true;
            byte[] bBuf = new byte[1024];
            if (buffer != null) {
                decoder.write(buffer.getBytes(inputCharset));
            }
            while (reading) {
                if (waiting) {
                    // Suspended (stdin handler removed): don't consume; awake()
                    // clears the flag and the loop resumes consuming.
                    if (!sleepPollInterval()) {
                        close();
                        break;
                    }
                    continue;
                }
                int available;
                try {
                    available = terminal.input().available();
                } catch (IOException ioe) {
                    LOGGER.log(Level.WARNING, "Failed while reading, exiting", ioe);
                    close();
                    break;
                }
                if (available <= 0) {
                    if (!sleepPollInterval()) {
                        close();
                        break;
                    }
                    continue;
                }
                int read = terminal.input().read(bBuf);
                if (read > 0) {
                    decoder.write(bBuf, 0, read);
                } else if (read < 0) {
                    close();
                }
            }
        } catch (IOException ioe) {
            LOGGER.log(Level.WARNING, "Failed while reading, exiting", ioe);
            close();
        }
    }

    /**
     * Sleeps briefly between legacy poll iterations.
     *
     * @return false if the thread was interrupted (caller should close and exit)
     */
    private static boolean sleepPollInterval() {
        try {
            Thread.sleep(LEGACY_POLL_INTERVAL_MS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Suspends reading from the terminal input stream.
     * The reading thread will wait until {@link #awake()} is called.
     */
    public void suspend() {
        if (!waiting) {
            latch = new CountDownLatch(1);
            waiting = true;
        }
    }

    /**
     * Resumes reading from the terminal input stream after a suspend.
     */
    public void awake() {
        if (waiting) {
            waiting = false;
            if (latch != null)
                latch.countDown();
        }
    }

    /**
     * Returns whether the connection is currently suspended.
     *
     * @return true if the connection is suspended, false otherwise
     */
    public boolean suspended() {
        return waiting;
    }

    /**
     * Returns whether the connection is currently reading from the input stream.
     *
     * @return true if actively reading, false otherwise
     */
    public boolean isReading() {
        return reading;
    }

    /**
     * Stops reading from the terminal input stream and wakes up any suspended threads.
     */
    public void stopReading() {
        reading = false;
        awake();
    }

    @Override
    public org.aesh.terminal.Connection write(String s) {
        // Fast path: no split screen — direct field check, no method dispatch
        if (currentRegion != null) {
            currentRegion.write(s);
            return this;
        }
        java.util.function.Consumer<int[]> handler = stdoutHandler();
        if (handler instanceof org.aesh.terminal.io.Encoder) {
            ((org.aesh.terminal.io.Encoder) handler).accept(s);
        } else {
            handler.accept(org.aesh.terminal.utils.Parser.toCodePoints(s));
        }
        return this;
    }

    @Override
    public void printAbove(String text) {
        // When split screen is active, route to top region
        if (splitScreenImpl != null && !splitScreenImpl.isClosed() && !splitScreenImpl.isSuspended()) {
            if (text != null && !text.isEmpty()) {
                splitScreenImpl.topRegion().writeln(text);
            }
            return;
        }
        // Default behavior: use printAbove handler or write directly
        java.util.function.Consumer<String> handler = printAboveHandler();
        if (handler != null) {
            handler.accept(text);
        } else {
            write(text + "\n");
        }
    }

    private void writeBytes(byte[] buf, int off, int len) {
        try {
            terminal.output().write(buf, off, len);
            terminal.output().flush();
        } catch (IOException e) {
            // During shutdown (closed=true), write/flush failures are expected
            // because the user's signal handler may have already closed the
            // connection before Readline.finish() writes cleanup sequences.
            // Log at FINE to avoid noisy WARNING messages during normal exit.
            if (closed) {
                LOGGER.log(Level.FINE, "Write after close (expected during shutdown)", e);
            } else {
                LOGGER.log(Level.WARNING, "Failed to write out.", e);
            }
        }
    }

    /**
     * Returns the underlying Terminal instance.
     *
     * @return the terminal
     */
    public Terminal getTerminal() {
        return terminal;
    }

    @Override
    public Device device() {
        return terminal.device();
    }

    @Override
    public Size size() {
        // When split screen is active, return the bottom region size
        // so readline constrains itself to the bottom region bounds
        if (splitScreenImpl != null && !splitScreenImpl.isSuspended()) {
            return splitScreenImpl.bottomRegion().size();
        }
        return terminal.getSize();
    }

    @Override
    public void setStdinHandler(Consumer<int[]> handler) {
        eventDecoder.setInputHandler(handler);
        if (handler == null)
            suspend();
        else
            awake();
    }

    @Override
    public boolean supportsNonBlockingRead() {
        return terminal.supportsNonBlockingRead();
    }

    @Override
    public int peek(long timeoutMs) throws IOException {
        return terminal.peek(timeoutMs);
    }

    // ==================== Split Screen ====================

    private volatile org.aesh.terminal.tty.split.SplitScreenImpl splitScreenImpl;
    private volatile org.aesh.terminal.tty.ScreenRegion currentRegion;

    @Override
    public org.aesh.terminal.tty.SplitScreen splitScreen(double ratio) {
        if (splitScreenImpl != null) {
            throw new IllegalStateException("Screen is already split");
        }
        Size termSize = size();
        int availableRows = termSize.getHeight() - 1; // 1 for separator
        int topRows = (int) (availableRows * ratio);
        int bottomRows = availableRows - topRows;
        if (topRows < SplitScreen.MIN_REGION_HEIGHT || bottomRows < SplitScreen.MIN_REGION_HEIGHT) {
            throw new IllegalArgumentException(
                    "Terminal too small for split: " + termSize.getHeight() + " rows, need at least "
                            + (SplitScreen.MIN_REGION_HEIGHT * 2 + 1));
        }
        splitScreenImpl = new org.aesh.terminal.tty.split.SplitScreenImpl(this, ratio);
        return splitScreenImpl;
    }

    @Override
    public org.aesh.terminal.tty.SplitScreen splitScreen() {
        return splitScreenImpl;
    }

    @Override
    public void setCurrentRegion(org.aesh.terminal.tty.ScreenRegion region) {
        this.currentRegion = region;
    }

    @Override
    public org.aesh.terminal.tty.ScreenRegion currentRegion() {
        return currentRegion;
    }

    /**
     * Default charset for connection I/O when the caller did not specify one.
     * <p>
     * Cygwin/MSYS2 consoles (mintty) speak UTF-8, while the JVM default on
     * Windows without beta-UTF-8 is a legacy codepage — decoding/encoding
     * with the JVM default garbles non-ASCII text (#280). Explicitly passed
     * charsets always win; this only chooses the default.
     * <p>
     * Package-private for testing: the Cygwin environment flag is a parameter
     * because OSUtils.IS_CYGWIN is a static constant.
     *
     * @param terminal the terminal being wrapped
     * @param cygwinEnvironment whether running in Cygwin/MSYS2 on Windows
     * @return the charset to use for unspecified connection I/O
     */
    static Charset defaultConnectionCharset(Terminal terminal, boolean cygwinEnvironment) {
        if (cygwinEnvironment && terminal instanceof PosixSysTerminal) {
            return StandardCharsets.UTF_8;
        }
        return Charset.defaultCharset();
    }

    /**
     * Build the escape sequence written on close() to release terminal modes.
     * <p>
     * Package-private for testing: the byte content of the close cleanup is
     * verified headless, while the ansi/focus gates are exercised live.
     *
     * @param ansi whether ANSI output is enabled (false for external
     *        terminals and redirected stdout)
     * @param focusTracking whether focus tracking was enabled
     * @return the cleanup sequence (possibly empty, never null)
     */
    static String closeCleanupSequences(boolean ansi, boolean focusTracking) {
        StringBuilder cleanup = new StringBuilder();
        if (ansi) {
            cleanup.append(ANSI.MODE_2026_DISABLE);
            // Exit the alternate screen (no-op when already on the main
            // screen) and ensure the cursor is visible — the same pair Vim
            // writes on exit. Pty4j-based terminals (IntelliJ) only notice
            // child exit while processing output; without this the shell
            // prompt can appear stuck until a keypress (#273).
            cleanup.append(ANSI.MAIN_BUFFER);
            cleanup.append(ANSI.CURSOR_SHOW);
        }
        if (focusTracking) {
            cleanup.append(ANSI.FOCUS_TRACKING_DISABLE);
        }
        return cleanup.toString();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        reading = false;
        // Close split screen if active
        if (splitScreenImpl != null) {
            try {
                splitScreenImpl.close();
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Failed to close split screen", e);
            }
            splitScreenImpl = null;
        }
        try {
            //call closeHandler before we close the terminal stream
            if (closeHandler() != null)
                closeHandler().accept(null);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Close handler failed", e);
        }
        try {
            //reset signal/size handlers
            terminal.handle(Signal.INT, prevIntrHandler);
            terminal.handle(Signal.WINCH, prevWincHandler);
            terminal.handle(Signal.CONT, prevContHandler);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to reset signal handlers", e);
        }
        // End synchronized output and focus tracking if active.
        // Leaving BSU (Mode 2026) without ESU causes the terminal
        // to buffer all output indefinitely.
        try {
            String cleanup = closeCleanupSequences(supportsAnsi(), focusHandler() != null);
            if (!cleanup.isEmpty()) {
                byte[] cleanupBytes = cleanup.getBytes();
                writeBytes(cleanupBytes, 0, cleanupBytes.length);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to disable terminal modes during close", e);
        }
        // NOTE: the input stream is deliberately never closed here.
        // openBlockingLegacy() polls available() instead of blocking in
        // read(), so setting reading = false above is sufficient to stop the
        // reader within one poll interval. Closing the stream is both
        // unnecessary and unsafe: it may be JVM-global state such as
        // System.in, and on some platforms (macOS) closing a tty fd while
        // another thread reads from it blocks the closer indefinitely (#288).
        try {
            //reset attributes and close terminal
            if (attributes != null && terminal != null) {
                terminal.setAttributes(attributes);
                terminal.close();
            }
        } catch (Exception | java.io.IOError e) {
            LOGGER.log(Level.WARNING, "Failed to close the terminal correctly", e);
        } finally {
            if (latch != null)
                latch.countDown();
        }
    }

}
