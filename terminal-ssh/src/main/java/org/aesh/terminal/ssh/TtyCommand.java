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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
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
import org.apache.sshd.common.io.IoInputStream;
import org.apache.sshd.common.io.IoOutputStream;
import org.apache.sshd.common.io.IoWriteFuture;
import org.apache.sshd.common.io.WritePendingException;
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
    private IoWriteFuture writeFuture;

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
            decoder.write(buf, start, len);
        } else {
            // Data send too early ?
        }
        return len;
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
            ByteArrayBuffer byteArrayBuffer = new ByteArrayBuffer(bytes);
            // the loop is only needed if we catch a WritePendingException, to retry the write and clear the buffer
            while (byteArrayBuffer.available() > 0) {
                try {
                    IoWriteFuture ioWriteFuture = out.writeBuffer(byteArrayBuffer);
                    // await the write so that we do not lose bytes
                    ioWriteFuture.verify(1, TimeUnit.SECONDS);
                } catch (WritePendingException | EOFException ignored) {
                    // WritePendingException is only cought if the verify() method timeouts
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };
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
            Size size;
            try {
                int width = Integer.parseInt(columns);
                int height = Integer.parseInt(lines);
                size = new Size(width, height);
            } catch (Exception ignore) {
                size = null;
            }
            if (size != null) {
                this.size = size;
                if (conn != null && conn.sizeHandler() != null) {
                    conn.sizeHandler().accept(size);
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
        // Test this
    }

    /**
     * Executes a task asynchronously using the SSH session's executor service.
     *
     * @param task the task to execute
     */
    protected void execute(Runnable task) {
        session.getSession().getFactoryManager().getScheduledExecutorService().execute(task);
    }

    /**
     * Schedules a task for delayed execution using the SSH session's executor service.
     *
     * @param task the task to schedule
     * @param delay the delay before execution
     * @param unit the time unit for the delay
     */
    protected void schedule(Runnable task, long delay, TimeUnit unit) {
        session.getSession().getFactoryManager().getScheduledExecutorService().schedule(task, delay, unit);
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
