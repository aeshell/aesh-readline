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

import org.aesh.io.Decoder;
import org.aesh.io.Encoder;
import org.aesh.terminal.Attributes;
import org.aesh.terminal.Connection;
import org.aesh.terminal.Device;
import org.aesh.terminal.EventDecoder;
import org.aesh.terminal.tty.Capability;
import org.aesh.terminal.tty.Signal;
import org.aesh.terminal.tty.Size;
import org.aesh.terminal.tty.TtyOutputMode;
import org.apache.sshd.common.channel.PtyMode;
import org.apache.sshd.common.io.IoInputStream;
import org.apache.sshd.common.io.IoOutputStream;
import org.apache.sshd.common.io.IoWriteFuture;
import org.apache.sshd.common.util.buffer.ByteArrayBuffer;
import org.apache.sshd.server.AsyncCommand;
import org.apache.sshd.server.ChannelSessionAware;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.channel.ChannelDataReceiver;
import org.apache.sshd.server.channel.ChannelSession;

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

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class TtyCommand implements AsyncCommand, ChannelDataReceiver, ChannelSessionAware {

    private static final Pattern LC_PATTERN = Pattern.compile("(?:\\p{Alpha}{2}_\\p{Alpha}{2}\\.)?([^@]+)(?:@.+)?");

    private final Consumer<Connection> handler;
    private final Charset defaultCharset;
    private Charset charset;
    private EventDecoder eventDecoder;
    private Decoder decoder;
    private Consumer<int[]> stdout;
    private Consumer<byte[]> out;
    private Size size = null;
    private Consumer<Size> sizeHandler;
    private Consumer<Void> closeHandler;
    protected ChannelSession session;
    private final AtomicBoolean closed = new AtomicBoolean();
    private ExitCallback exitCallback;
    private Connection conn;
    private IoOutputStream ioOut;
    private long lastAccessedTime = System.currentTimeMillis();
    private Device device;
    private IoWriteFuture writeFuture;
    private Attributes attributes;

    public TtyCommand(Charset defaultCharset, Consumer<Connection> handler) {
        this.handler = handler;
        this.defaultCharset = defaultCharset;
    }

    @Override
    public int data(ChannelSession channel, byte[] buf, int start, int len) throws IOException {
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
        this.out = (byte[] bytes) -> {
            if(writeFuture == null)
                writeFuture = out.write(new ByteArrayBuffer(bytes));
            else if(writeFuture.isWritten())
                writeFuture = out.write(new ByteArrayBuffer(bytes));
            else {
                try {
                    writeFuture.await();
                    writeFuture = out.write(new ByteArrayBuffer(bytes));
                } catch (IOException e) {
                    e.printStackTrace();
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
    public void start(final Environment env) throws IOException {
        String lcctype = env.getEnv().get("LC_CTYPE");
        if (lcctype != null) {
            charset = parseCharset(lcctype);
        }
        if (charset == null) {
            charset = defaultCharset;
        }
        env.addSignalListener(signal -> updateSize(env), EnumSet.of(org.apache.sshd.server.Signal.WINCH));
        updateSize(env);

        // Event handling
        int vintr = getControlChar(env, PtyMode.VINTR, 3);
        int veof = getControlChar(env, PtyMode.VEOF, 4);
        int vsusp = getControlChar(env, PtyMode.VSUSP, 26);

        device = new SSHDevice(env.getEnv().get("TERM"));
        attributes = SSHAttributesBuilder.builder().environment(env).build();
        eventDecoder = new EventDecoder(attributes);
        decoder = new Decoder(512, charset, eventDecoder);
        stdout = new TtyOutputMode(new Encoder(charset, out));
        conn = new SSHConnection();

        session.setDataReceiver(this);
        handler.accept(conn);
    }

    private int getControlChar(Environment env, PtyMode key, int def) {
        Integer controlChar = env.getPtyModes().get(key);
        return controlChar != null ? controlChar : def;
    }

    public void updateSize(Environment env) {
        String columns = env.getEnv().get(Environment.ENV_COLUMNS);
        String lines = env.getEnv().get(Environment.ENV_LINES);
        if (lines != null && columns != null) {
            Size size;
            try {
                int width = Integer.parseInt(columns);
                int height = Integer.parseInt(lines);
                size = new Size(width, height);
            }
            catch (Exception ignore) {
                size = null;
            }
            if (size != null) {
                this.size = size;
                if (sizeHandler != null) {
                    sizeHandler.accept(size);
                }
            }
        }
    }

    @Override
    public void close() throws IOException {
        ioOut.close(false).addListener(future -> {
            exitCallback.onExit(0);
            if (closed.compareAndSet(false, true)) {
                if (closeHandler != null) {
                    closeHandler.accept(null);
                } else {
                    // This happen : report it to the SSHD project
                }
            }
        });
    }

    @Override
    public void destroy() {
        // Test this
    }

    protected void execute(Runnable task) {
        session.getSession().getFactoryManager().getScheduledExecutorService().execute(task);
    }

    protected void schedule(Runnable task, long delay, TimeUnit unit) {
        session.getSession().getFactoryManager().getScheduledExecutorService().schedule(task, delay, unit);
    }

    private static Charset parseCharset(String value) {
        Matcher matcher = LC_PATTERN.matcher(value);
        if (matcher.matches()) {
            try {
                return Charset.forName(matcher.group(1));
            }
            catch (Exception ignore) {
            }
        }
        return null;
    }

    private class SSHConnection implements Connection {

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

        public Device device() {
            return device;
        }

        @Override
        public Consumer<int[]> getStdinHandler() {
            return eventDecoder.getInputHandler();
        }

        @Override
        public void setStdinHandler(Consumer<int[]> handler) {
            eventDecoder.setInputHandler(handler);
        }

        @Override
        public Size size() {
            return size;
        }

        @Override
        public Consumer<Size> getSizeHandler() {
            return sizeHandler;
        }

        @Override
        public void setSizeHandler(Consumer<Size> handler) {
            sizeHandler = handler;
        }

        @Override
        public Consumer<Signal> getSignalHandler() {
            return eventDecoder.getSignalHandler();
        }

        @Override
        public void setSignalHandler(Consumer<Signal> handler) {
            eventDecoder.setSignalHandler(handler);
        }

        @Override
        public Consumer<int[]> stdoutHandler() {
            return stdout;
        }

        @Override
        public void setCloseHandler(Consumer<Void> handler) {
            closeHandler = handler;
        }

        @Override
        public Consumer<Void> getCloseHandler() {
            return closeHandler;
        }

        @Override
        public void close() {
            try {
                TtyCommand.this.close();
            } catch (IOException ignore) {
            }
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
        public Attributes getAttributes() {
            return attributes;
        }

        @Override
        public void setAttributes(Attributes attr) {
        }

    }
}
