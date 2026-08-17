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

import org.aesh.terminal.Attributes;
import org.aesh.terminal.Connection;
import org.aesh.terminal.Device;
import org.aesh.terminal.EventDecoder;
import org.aesh.terminal.io.Decoder;
import org.aesh.terminal.io.Encoder;
import org.aesh.terminal.tty.Capability;
import org.aesh.terminal.tty.Signal;
import org.aesh.terminal.tty.Size;
import org.aesh.terminal.tty.TtyOutputMode;

/**
 * A telnet handler that implements {@link org.aesh.terminal.Connection}.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public final class TelnetTtyConnection extends TelnetHandler implements Connection {

    private final boolean inBinary;
    private final boolean outBinary;
    private boolean receivingBinary;
    private boolean sendingBinary;
    private boolean accepted;
    private Size size;
    private String terminalType;
    private Consumer<Size> sizeHandler;
    private Consumer<Void> closeHandler;
    /** The underlying telnet connection. */
    private TelnetConnection conn;
    private final Charset charset;
    private final EventDecoder eventDecoder = new EventDecoder(3, 4, 26);
    private final ReadBuffer readBuffer = new ReadBuffer(this::execute);
    private final Decoder decoder = new Decoder(512, TelnetCharset.INSTANCE, readBuffer);
    private final Encoder encoder = new Encoder(StandardCharsets.US_ASCII, data -> conn.write(data));
    private final Consumer<int[]> stdout = new TtyOutputMode(encoder);
    private final Consumer<Connection> handler;
    private long lastAccessedTime = System.currentTimeMillis();
    private Device device;
    private Attributes attributes;
    private volatile boolean reading = false;

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
    protected void onSendBinary(boolean binary) {
        sendingBinary = binary;
        if (binary) {
            encoder.setCharset(charset);
        }
        checkAccept();
    }

    @Override
    protected void onReceiveBinary(boolean binary) {
        receivingBinary = binary;
        if (binary) {
            decoder.setCharset(charset);
        }
        checkAccept();
    }

    @Override
    protected void onData(byte[] data) {
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
    protected void onOpen(TelnetConnection conn) {
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

        attributes = new Attributes();

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
    protected void onTerminalType(String terminalType) {
        this.terminalType = terminalType;

        device = new TelnetDevice(terminalType);

    }

    @Override
    public Size size() {
        return size;
    }

    @Override
    protected void onSize(int width, int height) {
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
    public Consumer<Size> sizeHandler() {
        return sizeHandler;
    }

    @Override
    public void setSizeHandler(Consumer<Size> handler) {
        this.sizeHandler = handler;
    }

    @Override
    public Consumer<Signal> signalHandler() {
        return eventDecoder.getSignalHandler();
    }

    @Override
    public void setSignalHandler(Consumer<Signal> handler) {
        eventDecoder.setSignalHandler(handler);
    }

    @Override
    public Consumer<int[]> stdinHandler() {
        return eventDecoder.getInputHandler();
    }

    @Override
    public void setStdinHandler(Consumer<int[]> handler) {
        eventDecoder.setInputHandler(handler);
    }

    @Override
    public Consumer<int[]> stdoutHandler() {
        return stdout;
    }

    @Override
    public void setCloseHandler(Consumer<Void> closeHandler) {
        this.closeHandler = closeHandler;
    }

    @Override
    public Consumer<Void> closeHandler() {
        return closeHandler;
    }

    @Override
    protected void onClose() {
        reading = false;
        if (readlineExecutor != null) {
            readlineExecutor.shutdownNow();
        }
        if (closeHandler != null) {
            closeHandler.accept(null);
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
    public boolean reading() {
        return reading;
    }

    @Override
    public boolean put(Capability capability, Object... params) {
        return false;
    }

    @Override
    public Attributes attributes() {
        return attributes;
    }

    @Override
    public void setAttributes(Attributes attr) {

    }
}
