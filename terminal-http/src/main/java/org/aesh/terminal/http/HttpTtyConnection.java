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
package org.aesh.terminal.http;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A connection to an http client, independent of the protocol, it could be straight WebSockets or
 * SockJS, etc...
 *
 * The incoming protocol is based on json messages:
 *
 * {
 *   "action": "read",
 *   "data": "what the user typed"
 * }
 *
 * or
 *
 * {
 *   "action": "resize",
 *   "cols": 30,
 *   "rows: 50
 * }
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public abstract class HttpTtyConnection implements Connection {

    public static final Size DEFAULT_SIZE = new Size(80, 24);
    private final Device device;

    private Charset charset;
    private Size size;
    private Consumer<Size> sizeHandler;
    private final EventDecoder eventDecoder;
    private final Decoder decoder;
    private final Consumer<int[]> stdout;
    private Consumer<Void> closeHandler;
    private Consumer<String> termHandler;
    private long lastAccessedTime = System.currentTimeMillis();
    private Attributes attributes;

    public HttpTtyConnection() {
        this(StandardCharsets.UTF_8, DEFAULT_SIZE);
    }

    public HttpTtyConnection(Charset charset, Size size) {
        this.charset = charset;
        this.size = size;
        this.eventDecoder = new EventDecoder(3, 4, 26);
        this.decoder = new Decoder(512, charset, eventDecoder);
        this.stdout = new TtyOutputMode(new Encoder(charset, this::write));

        this.device = new HttpDevice("vt100");
        attributes = new Attributes();
    }

    @Override
    public Charset outputEncoding() {
        return charset;
    }

    @Override
    public Charset inputEncoding() {
        return charset;
    }

    public long lastAccessedTime() {
        return lastAccessedTime;
    }

    @Override
    public Device device() {
        return device;
    }

    protected abstract void write(byte[] buffer);

    public void writeToDecoder(String msg) {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> obj;
        String action;
        try {
            obj = mapper.readValue(msg, Map.class);
            action = (String) obj.get("action");
        } catch (IOException e) {
            // Log this
            return;
        }
        if (action != null) {
            switch (action) {
                case "read":
                    lastAccessedTime = System.currentTimeMillis();
                    String data = (String) obj.get("data");
                    decoder.write(data.getBytes()); //write back echo
                    break;
                case "resize":
                    try {
                        int cols = (int) obj.getOrDefault("cols", size.getWidth());
                        int rows = (int) obj.getOrDefault("rows", size.getHeight());
                        if (cols > 0 && rows > 0) {
                            Size newSize = new Size(cols, rows);
                            if (!newSize.equals(size())) {
                                size = newSize;
                                if (sizeHandler != null) {
                                    sizeHandler.accept(size);
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Invalid size
                        // Log this
                    }
                    break;
            }
        }
    }

    public Consumer<String> getTerminalTypeHandler() {
        return termHandler;
    }

    public void setTerminalTypeHandler(Consumer<String> handler) {
        termHandler = handler;
    }

    @Override
    public Size size() {
        return size;
    }

    public Consumer<Size> getSizeHandler() {
        return sizeHandler;
    }

    public void setSizeHandler(Consumer<Size> handler) {
        this.sizeHandler = handler;
    }

    @Override
    public Consumer<Signal> getSignalHandler() {
        return eventDecoder.getSignalHandler();
    }

    @Override
    public void setSignalHandler(Consumer<Signal> handler) {
        eventDecoder.setSignalHandler(handler);
    }

    public Consumer<int[]> getStdinHandler() {
        return eventDecoder.getInputHandler();
    }

    public void setStdinHandler(Consumer<int[]> handler) {
        eventDecoder.setInputHandler(handler);
    }

    public Consumer<int[]> stdoutHandler() {
        return stdout;
    }

    @Override
    public void setCloseHandler(Consumer<Void> closeHandler) {
        this.closeHandler = closeHandler;
    }

    @Override
    public Consumer<Void> getCloseHandler() {
        return closeHandler;
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

    @Override
    public boolean supportsAnsi() {
        return true;
    }
}
