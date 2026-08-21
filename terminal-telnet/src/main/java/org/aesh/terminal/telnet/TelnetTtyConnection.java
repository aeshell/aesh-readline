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

package org.aesh.terminal.telnet;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.aesh.terminal.AbstractConnection;
import org.aesh.terminal.Attributes;
import org.aesh.terminal.Connection;
import org.aesh.terminal.Device;
import org.aesh.terminal.EventDecoder;
import org.aesh.terminal.io.Decoder;
import org.aesh.terminal.io.Encoder;
import org.aesh.terminal.tty.Capability;
import org.aesh.terminal.tty.Size;
import org.aesh.terminal.tty.TtyOutputMode;

/**
 * A telnet handler that implements {@link Connection} via {@link AbstractConnection}.
 * <p>
 * Extends {@link AbstractConnection} for common handler plumbing (signal, stdin,
 * stdout, size, close, mouse, focus, theme change, printAbove, statusLine) and
 * implements {@link TelnetHandler} for telnet protocol callbacks.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public final class TelnetTtyConnection extends AbstractConnection implements TelnetHandler {

    private static final Logger LOGGER = Logger.getLogger(TelnetTtyConnection.class.getName());

    private final boolean inBinary;
    private final boolean outBinary;
    private boolean receivingBinary;
    private boolean sendingBinary;
    private boolean accepted;
    private volatile boolean closed;
    private Size size;
    private String terminalType;
    /** The underlying telnet connection. */
    private TelnetConnection conn;
    private final Charset charset;
    private final ReadBuffer readBuffer = new ReadBuffer(this::execute);
    private final Decoder decoder = new Decoder(512, TelnetCharset.INSTANCE, readBuffer);
    private final Encoder encoder = new Encoder(StandardCharsets.US_ASCII, this::writeToConn);
    private final Consumer<Connection> handler;
    private long lastAccessedTime = System.currentTimeMillis();
    private Device device;

    /**
     * Per-connection executor for readline processing.
     * All input decoding, event processing, action handling, and output generation
     * runs on this single-thread executor, keeping the Netty IO thread free.
     */
    private ScheduledExecutorService readlineExecutor;

    /**
     * Backpressure: number of tasks pending in the readline executor.
     * When this exceeds HIGH_WATER_MARK, reading from the transport is paused.
     * When it drops to LOW_WATER_MARK, reading resumes.
     */
    private final AtomicInteger pendingTasks = new AtomicInteger(0);
    private static final int HIGH_WATER_MARK = 64;
    private static final int LOW_WATER_MARK = 16;

    /**
     * Creates a new TelnetTtyConnection.
     *
     * @param inBinary true to enable binary mode for input
     * @param outBinary true to enable binary mode for output
     * @param charset the charset to use for encoding/decoding
     * @param handler the connection handler to be notified when the connection is established
     */
    public TelnetTtyConnection(boolean inBinary, boolean outBinary, Charset charset, Consumer<Connection> handler) {
        this.charset = charset;
        this.inBinary = inBinary;
        this.outBinary = outBinary;
        this.handler = handler;
        // Initialize inherited fields — eventDecoder and stdout are assigned
        // in onOpen() after the TelnetConnection is available.
    }

    /**
     * Returns the timestamp of the last access to this connection.
     *
     * @return the last accessed time in milliseconds since epoch
     */
    public long lastAccessedTime() {
        return lastAccessedTime;
    }

    /**
     * Returns the terminal type reported by the client.
     *
     * @return the terminal type string, or null if not yet received
     */
    public String terminalType() {
        return terminalType;
    }

    /**
     * Writes encoded output to the underlying telnet connection.
     * Used as the {@link org.aesh.terminal.io.ByteWriter} for the Encoder.
     */
    private void writeToConn(byte[] buf, int off, int len) {
        try {
            conn.write(buf, off, len);
        } catch (Exception e) {
            if (closed) {
                LOGGER.log(Level.FINE, "Write after close (expected during shutdown)", e);
            } else {
                LOGGER.log(Level.WARNING, "Failed to write to telnet connection", e);
            }
        }
    }

    /**
     * Executes a task on the per-connection readline executor if the connection
     * is accepted, otherwise falls back to the Netty event loop.
     * <p>
     * Before acceptance, tasks (like ReadBuffer.drainQueue) must run on the
     * Netty event loop to stay synchronized with option negotiation. After
     * acceptance, tasks are offloaded to the readline executor.
     *
     * @param task the task to execute
     */
    public void execute(Runnable task) {
        if (conn != null) {
            conn.execute(task);
        }
    }

    /**
     * Schedules a task for delayed execution on the per-connection readline executor.
     * Falls back to the Netty event loop if the executor is not yet initialized,
     * the connection is not yet accepted, or the executor has been shut down.
     *
     * @param task the task to execute
     * @param delay the delay before execution
     * @param unit the time unit of the delay
     */
    public void schedule(Runnable task, long delay, TimeUnit unit) {
        if (conn != null) {
            conn.schedule(task, delay, unit);
        }
    }

    @Override
    public Charset inputEncoding() {
        return inBinary ? charset : StandardCharsets.US_ASCII;
    }

    @Override
    public Charset outputEncoding() {
        return outBinary ? charset : StandardCharsets.US_ASCII;
    }

    @Override
    public boolean supportsAnsi() {
        return true;
    }

    @Override
    public void onSendBinary(boolean binary) {
        sendingBinary = binary;
        if (binary) {
            encoder.setCharset(charset);
        }
        checkAccept();
    }

    @Override
    public void onReceiveBinary(boolean binary) {
        receivingBinary = binary;
        if (binary) {
            decoder.setCharset(charset);
        }
        checkAccept();
    }

    @Override
    public void onData(byte[] data) {
        lastAccessedTime = System.currentTimeMillis();
        if (accepted && readlineExecutor != null && !readlineExecutor.isShutdown()) {
            // Connection accepted — dispatch to readline executor
            byte[] copy = data.clone();
            int pending = pendingTasks.incrementAndGet();
            if (pending > HIGH_WATER_MARK) {
                conn.pauseReads();
            }
            readlineExecutor.execute(() -> {
                try {
                    decoder.write(copy);
                } finally {
                    int remaining = pendingTasks.decrementAndGet();
                    if (remaining <= LOW_WATER_MARK) {
                        conn.resumeReads();
                    }
                }
            });
        } else {
            // Before acceptance or executor not ready — process inline on IO
            // thread. This is needed during telnet option negotiation (binary
            // mode) where data may arrive before checkAccept() fires. The
            // ReadBuffer queues this data and drains it when the readHandler
            // is set in checkAccept().
            decoder.write(data);
        }
    }

    @Override
    public void onOpen(TelnetConnection conn) {
        this.conn = conn;

        readlineExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "aesh-telnet-readline");
            t.setDaemon(true);
            return t;
        });

        //set default size for now
        size = new Size(80, 24);

        // Kludge mode
        conn.writeWillOption(Option.ECHO);
        conn.writeWillOption(Option.SGA);

        //
        if (inBinary) {
            conn.writeDoOption(Option.BINARY);
        }
        if (outBinary) {
            conn.writeWillOption(Option.BINARY);
        }

        // Window size
        conn.writeDoOption(Option.NAWS);

        // Get some info about user
        conn.writeDoOption(Option.TERMINAL_TYPE);

        // Initialize inherited fields from AbstractConnection
        this.eventDecoder = new EventDecoder(3, 4, 26);
        this.stdout = new TtyOutputMode(encoder);
        this.attributes = new Attributes();

        //
        checkAccept();
    }

    private void checkAccept() {
        if (!accepted) {
            if (!outBinary | (outBinary && sendingBinary)) {
                if (!inBinary | (inBinary && receivingBinary)) {
                    accepted = true;
                    reading = true;
                    readBuffer.setReadHandler(eventDecoder);
                    handler.accept(this);
                }
            }
        }
    }

    @Override
    public void onTerminalType(String terminalType) {
        this.terminalType = terminalType;

        device = new TelnetDevice(terminalType);

    }

    @Override
    public Size size() {
        return size;
    }

    @Override
    public void onSize(int width, int height) {
        this.size = new Size(width, height);
        if (sizeHandler != null) {
            Size s = size;
            if (readlineExecutor != null) {
                readlineExecutor.execute(() -> sizeHandler.accept(s));
            } else {
                sizeHandler.accept(s);
            }
        }
    }

    @Override
    public Device device() {
        //create a default device for now
        if (device == null)
            device = new TelnetDevice("vt100");
        return device;
    }

    @Override
    public void onClose() {
        if (closed)
            return;
        closed = true;
        reading = false;
        if (readlineExecutor != null) {
            readlineExecutor.shutdownNow();
        }
        try {
            if (closeHandler != null) {
                closeHandler.accept(null);
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Close handler threw exception", e);
        }
    }

    @Override
    public void close() {
        conn.close();
    }

    @Override
    public void openBlocking() {
    }

    @Override
    public void openNonBlocking() {
    }

    @Override
    public boolean put(Capability capability, Object... params) {
        return false;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Telnet PTY modes are negotiated during session setup and cannot be
     * changed afterward — this method is intentionally a no-op.
     */
    @Override
    public void setAttributes(Attributes attr) {
        // No-op: telnet attributes are read-only after negotiation
    }
}
