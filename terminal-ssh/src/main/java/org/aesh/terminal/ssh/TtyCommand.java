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

package org.aesh.terminal.ssh;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.EnumSet;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.aesh.terminal.AbstractConnection;
import org.aesh.terminal.Connection;
import org.aesh.terminal.Device;
import org.aesh.terminal.EventDecoder;
import org.aesh.terminal.io.Decoder;
import org.aesh.terminal.io.Encoder;
import org.aesh.terminal.tty.Capability;
import org.aesh.terminal.tty.Size;
import org.aesh.terminal.tty.TtyOutputMode;
import org.aesh.terminal.utils.LoggerUtil;
import org.apache.sshd.common.io.IoInputStream;
import org.apache.sshd.common.io.IoOutputStream;
import org.apache.sshd.common.io.IoWriteFuture;
import org.apache.sshd.common.util.buffer.ByteArrayBuffer;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.channel.ChannelDataReceiver;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.channel.ChannelSessionAware;
import org.apache.sshd.server.command.AsyncCommand;

/**
 * SSH command implementation that handles TTY connections and data transfer.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class TtyCommand implements AsyncCommand, ChannelDataReceiver, ChannelSessionAware {

    private static final Logger LOGGER = LoggerUtil.getLogger(TtyCommand.class.getName());
    private static final Pattern LC_PATTERN = Pattern.compile("(?:\\p{Alpha}{2}_\\p{Alpha}{2}\\.)?([^@]+)(?:@.+)?");

    private final Consumer<Connection> handler;
    private final Charset defaultCharset;
    private Charset charset;
    private Decoder decoder;
    private Consumer<byte[]> out;
    private Size size = null;
    /** The SSH channel session associated with this command. */
    protected ChannelSession session;
    private final AtomicBoolean closed = new AtomicBoolean();
    private ExitCallback exitCallback;
    private SSHConnection conn;
    private IoOutputStream ioOut;
    private long lastAccessedTime = System.currentTimeMillis();
    private Device device;

    /**
     * Per-connection executor for readline processing.
     * All input decoding, event processing, action handling, and output generation
     * runs on this single-thread executor, keeping the Netty IO thread free for
     * network I/O.
     */
    private ScheduledExecutorService readlineExecutor;

    /**
     * Async write queue for serializing SSH channel writes.
     * SSHD throws WritePendingException if writeBuffer() is called while a
     * previous write is still in flight, so we queue writes and drain them
     * one at a time using IoWriteFuture listeners.
     */
    private final Queue<byte[]> writeQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean writing = new AtomicBoolean(false);

    /**
     * Creates a new TtyCommand with the specified charset and connection handler.
     *
     * @param defaultCharset the default character set for encoding/decoding
     * @param handler the consumer that handles new connections
     */
    public TtyCommand(Charset defaultCharset, Consumer<Connection> handler) {
        this.handler = handler;
        this.defaultCharset = defaultCharset;
    }

    @Override
    public int data(ChannelSession channel, byte[] buf, int start, int len) {
        if (decoder != null) {
            lastAccessedTime = System.currentTimeMillis();
            // Copy the buffer — SSHD may reuse it after data() returns.
            // Dispatch to the readline executor to keep the Netty IO thread free.
            byte[] copy = new byte[len];
            System.arraycopy(buf, start, copy, 0, len);
            final int consumed = len;
            readlineExecutor.execute(() -> {
                try {
                    decoder.write(copy, 0, copy.length);
                } finally {
                    // Release the SSH channel window AFTER processing, not before.
                    // This provides backpressure: the client cannot send faster than
                    // we can process, because the window is only refilled once the
                    // readline executor finishes handling each chunk.
                    try {
                        channel.getLocalWindow().release(consumed);
                    } catch (Exception e) {
                        // Channel may already be closed — safe to ignore
                        LOGGER.log(Level.FINE, "Failed to release SSH window", e);
                    }
                }
            });
        }
        // Return 0 = "data queued but not consumed yet". SSHD will not refill
        // the channel window until we call channel.getLocalWindow().release().
        return 0;
    }

    @Override
    public void setChannelSession(ChannelSession session) {
        this.session = session;
    }

    @Override
    public void setInputStream(InputStream in) {
    }

    @Override
    public void setOutputStream(final OutputStream out) {
    }

    @Override
    public void setErrorStream(OutputStream err) {
    }

    @Override
    public void setIoInputStream(IoInputStream in) {
    }

    @Override
    public void setIoOutputStream(IoOutputStream out) {
        this.ioOut = out;
        this.out = bytes -> {
            writeQueue.add(bytes);
            drainWriteQueue();
        };
    }

    /**
     * Drains the write queue one entry at a time. Only one write is in-flight
     * at any time to avoid SSHD's WritePendingException. When a write completes,
     * the IoWriteFuture listener triggers the next drain.
     */
    private void drainWriteQueue() {
        if (!writing.compareAndSet(false, true)) {
            return; // another drain in progress — it will pick up our queued data
        }
        writeNextFromQueue();
    }

    private void writeNextFromQueue() {
        byte[] bytes = writeQueue.poll();
        if (bytes == null) {
            writing.set(false);
            // Re-check after releasing — another thread may have queued between poll and set
            if (!writeQueue.isEmpty() && writing.compareAndSet(false, true)) {
                writeNextFromQueue();
            }
            return;
        }
        try {
            IoWriteFuture future = ioOut.writeBuffer(new ByteArrayBuffer(bytes));
            future.addListener(f -> {
                Throwable ex = f.getException();
                if (ex != null) {
                    LOGGER.log(Level.WARNING, "SSH write failed", ex);
                }
                // Continue draining regardless of success/failure
                writeNextFromQueue();
            });
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "SSH write error", e);
            // Continue draining to avoid stuck queue
            writeNextFromQueue();
        }
    }

    @Override
    public void setIoErrorStream(IoOutputStream err) {

    }

    @Override
    public void setExitCallback(ExitCallback callback) {
        this.exitCallback = callback;
    }

    @Override
    public void start(ChannelSession channelSession, Environment env) {
        String lcctype = env.getEnv().get("LC_CTYPE");
        if (lcctype != null) {
            charset = parseCharset(lcctype);
        }
        if (charset == null) {
            charset = defaultCharset;
        }

        readlineExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "aesh-ssh-readline");
            t.setDaemon(true);
            return t;
        });

        env.addSignalListener((ch, signal) -> updateSize(env), EnumSet.of(org.apache.sshd.server.Signal.WINCH));
        updateSize(env);

        device = new SSHDevice(env.getEnv().get("TERM"));

        org.aesh.terminal.Attributes attrs = SSHAttributesBuilder.builder().environment(env).build();
        EventDecoder ed = new EventDecoder(attrs);
        Consumer<int[]> stdoutHandler = new TtyOutputMode(new Encoder(charset, out));
        conn = new SSHConnection(attrs, ed, stdoutHandler);
        decoder = new Decoder(512, charset, ed);

        session.setDataReceiver(this);
        conn.setReading(true);
        handler.accept(conn);
    }

    /**
     * Updates the terminal size from the SSH environment variables.
     *
     * @param env the SSH environment containing size information
     */
    public void updateSize(Environment env) {
        String columns = env.getEnv().get(Environment.ENV_COLUMNS);
        String lines = env.getEnv().get(Environment.ENV_LINES);
        if (lines != null && columns != null) {
            Size newSize;
            try {
                int width = Integer.parseInt(columns);
                int height = Integer.parseInt(lines);
                newSize = new Size(width, height);
            } catch (Exception ignore) {
                newSize = null;
            }
            if (newSize != null) {
                this.size = newSize;
                if (conn != null && conn.sizeHandler() != null) {
                    // Dispatch to readline executor to avoid racing with input processing
                    Size s = newSize;
                    readlineExecutor.execute(() -> conn.sizeHandler().accept(s));
                }
            }
        }
    }

    @Override
    public void close() {
        close(0);
    }

    private void close(int exit) {
        if (conn != null) {
            conn.setReading(false);
        }
        if (readlineExecutor != null) {
            readlineExecutor.shutdownNow();
        }
        ioOut.close(false).addListener(future -> {
            exitCallback.onExit(exit);
            if (closed.compareAndSet(false, true)) {
                if (conn != null && conn.closeHandler() != null) {
                    conn.closeHandler().accept(null);
                }
            }
        });
    }

    @Override
    public void destroy(ChannelSession channelSession) {
        if (readlineExecutor != null) {
            readlineExecutor.shutdownNow();
        }
    }

    /**
     * Executes a task on the per-connection readline executor.
     *
     * @param task the task to execute
     */
    protected void execute(Runnable task) {
        readlineExecutor.execute(task);
    }

    /**
     * Schedules a task for delayed execution on the per-connection readline executor.
     *
     * @param task the task to schedule
     * @param delay the delay before execution
     * @param unit the time unit for the delay
     */
    protected void schedule(Runnable task, long delay, TimeUnit unit) {
        readlineExecutor.schedule(task, delay, unit);
    }

    private static Charset parseCharset(String value) {
        Matcher matcher = LC_PATTERN.matcher(value);
        if (matcher.matches()) {
            try {
                return Charset.forName(matcher.group(1));
            } catch (Exception ignore) {
            }
        }
        return null;
    }

    private class SSHConnection extends AbstractConnection {

        SSHConnection(org.aesh.terminal.Attributes attributes, EventDecoder eventDecoder,
                Consumer<int[]> stdout) {
            this.attributes = attributes;
            this.eventDecoder = eventDecoder;
            this.stdout = stdout;
        }

        void setReading(boolean reading) {
            this.reading = reading;
        }

        @Override
        public Charset inputEncoding() {
            return charset;
        }

        @Override
        public Charset outputEncoding() {
            return charset;
        }

        @Override
        public boolean supportsAnsi() {
            return true;
        }

        public long lastAccessedTime() {
            return lastAccessedTime;
        }

        @Override
        public Device device() {
            return device;
        }

        @Override
        public Size size() {
            return size;
        }

        @Override
        public void close() {
            TtyCommand.this.close();
        }

        @Override
        public void close(int exit) {
            TtyCommand.this.close(exit);
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

        @Override
        public void setAttributes(org.aesh.terminal.Attributes attr) {
        }

    }
}
