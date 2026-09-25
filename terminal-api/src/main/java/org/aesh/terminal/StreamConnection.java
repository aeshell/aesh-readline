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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.aesh.terminal.io.Decoder;
import org.aesh.terminal.tty.Capability;
import org.aesh.terminal.tty.Size;
import org.aesh.terminal.utils.Parser;

/**
 * A {@link Connection} backed by plain JDK streams, for embedders driving
 * an interactive session over pipes (test harnesses, protocol bridges).
 * <p>
 * Owns a named daemon reader thread that polls the input stream and feeds
 * bytes through a {@link Decoder} into {@link EventDecoder#accept(int[])},
 * so multi-byte sequences split across reads decode correctly and input
 * arriving with no handler set is buffered instead of dropped (never wire
 * decoded output straight to a stdin handler — that would skip signal
 * extraction and the queue).
 * <p>
 * Differences from {@code TerminalConnection} are deliberate:
 * <ul>
 * <li>{@link #isInteractive()} returns {@code true} so embedders stay in
 * async mode instead of dropping to synchronous piped handling.</li>
 * <li>No terminal, PTY, raw-mode, or native-signal setup — nothing
 * process-global is touched.</li>
 * <li>{@link #supportsAnsi()} returns {@code false}; the device reports
 * type {@code "dumb"} (no ANSI output is supported).</li>
 * <li>{@link #close()} interrupts the reader and fires the close handler
 * but never closes the caller-owned streams.</li>
 * </ul>
 * <p>
 * Clean writer-close with no trailing bytes is not delivered as EOF (the
 * poll loop only reads when bytes are available); close the connection
 * explicitly to end the session.
 *
 * @since 3.18.3
 */
public class StreamConnection extends AbstractConnection {

    private static final Logger LOGGER = Logger.getLogger(StreamConnection.class.getName());

    /**
     * Idle poll interval for the reader loop. Bounds input latency and the
     * delay between close() and reader-thread exit without closing the
     * caller-owned stream (which may be JVM-global state).
     */
    private static final int PUMP_POLL_MS = 10;

    private final Charset inputCharset;
    private final Charset outputCharset;
    private final InputStream input;
    private final OutputStream output;
    private final Decoder decoder;
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile Consumer<Throwable> deathHook;
    private volatile Size size = new Size(120, 40);
    private Thread readerThread;

    /**
     * Writer side: encodes code points with the output charset. Failures
     * mean the peer went away; they are logged, never thrown.
     */
    private final class StdoutWriter implements Consumer<int[]> {
        @Override
        public void accept(int[] data) {
            try {
                output.write(Parser.fromCodePoints(data).getBytes(outputCharset));
                output.flush();
            } catch (IOException e) {
                LOGGER.log(Level.FINE, "StreamConnection write failed (peer gone?)", e);
            }
        }
    }

    /**
     * Creates a connection over the given streams.
     *
     * @param charset charset for both input decoding and output encoding
     * @param input stream to read input bytes from (caller-owned, never closed)
     * @param output stream to write output bytes to (caller-owned, never closed)
     */
    public StreamConnection(Charset charset, InputStream input, OutputStream output) {
        this(charset, charset, input, output);
    }

    /**
     * Creates a connection over the given streams.
     *
     * @param inputCharset charset for input decoding
     * @param outputCharset charset for output encoding
     * @param input stream to read input bytes from (caller-owned, never closed)
     * @param output stream to write output bytes to (caller-owned, never closed)
     */
    public StreamConnection(Charset inputCharset, Charset outputCharset,
            InputStream input, OutputStream output) {
        this.inputCharset = inputCharset;
        this.outputCharset = outputCharset;
        this.input = input;
        this.output = output;
        this.attributes = new Attributes();
        this.eventDecoder = new EventDecoder(this.attributes);
        this.decoder = new Decoder(512, inputCharset, this.eventDecoder);
        this.stdout = new StdoutWriter();
    }

    /**
     * Sets the reported terminal size.
     *
     * @param size the size to report from {@link #size()}
     */
    public void setSize(Size size) {
        this.size = size;
    }

    /**
     * Sets a hook invoked once if the reader thread dies unexpectedly.
     * A dead reader silently wedges every later command, so the default
     * logs at SEVERE; harnesses typically record it for diagnostics
     * instead. The hook replaces the default log, it does not add to it.
     *
     * @param hook invoked with the fatal cause, or null to restore logging
     */
    public void setReaderDeathHook(Consumer<Throwable> hook) {
        this.deathHook = hook;
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
    public Device device() {
        return new BaseDevice("dumb");
    }

    @Override
    public Size size() {
        return size;
    }

    @Override
    public boolean put(Capability capability, Object... params) {
        return false;
    }

    @Override
    public boolean supportsAnsi() {
        return false;
    }

    @Override
    public boolean isInteractive() {
        return true;
    }

    @Override
    public void openBlocking() {
        startReader();
        try {
            if (readerThread != null) {
                readerThread.join();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void openNonBlocking() {
        startReader();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            reading = false;
            if (readerThread != null) {
                readerThread.interrupt();
            }
            if (closeHandler != null) {
                closeHandler.accept(null);
            }
        }
    }

    private void startReader() {
        if (readerThread != null) {
            return;
        }
        reading = true;
        readerThread = new Thread(new ReaderLoop(), "aesh-stream-reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    /**
     * Poll-based reader: never blocks indefinitely and never closes the
     * caller-owned stream, so close() always stops the thread promptly.
     */
    private final class ReaderLoop implements Runnable {
        @Override
        public void run() {
            byte[] buffer = new byte[1024];
            try {
                while (!closed.get()) {
                    int available;
                    try {
                        available = input.available();
                    } catch (IOException e) {
                        readerDied(e);
                        break;
                    }
                    if (available > 0) {
                        int n;
                        try {
                            n = input.read(buffer);
                        } catch (IOException e) {
                            readerDied(e);
                            break;
                        }
                        if (n > 0) {
                            decoder.write(buffer, 0, n);
                        } else if (n < 0) {
                            break;
                        }
                    } else {
                        try {
                            Thread.sleep(PUMP_POLL_MS);
                        } catch (InterruptedException e) {
                            // close() interrupted the idle sleep — exit promptly
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            } catch (Throwable t) {
                if (t instanceof ThreadDeath) {
                    throw (ThreadDeath) t;
                }
                readerDied(t);
            } finally {
                reading = false;
            }
        }
    }

    private void readerDied(Throwable cause) {
        Consumer<Throwable> hook = deathHook;
        if (hook != null) {
            try {
                hook.accept(cause);
            } catch (Throwable ignored) {
            }
        } else {
            LOGGER.log(Level.SEVERE,
                    "StreamConnection reader died; input will no longer be delivered", cause);
        }
    }
}
