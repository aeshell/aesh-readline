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

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
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

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A connection to an http client, independent of the protocol, it could be straight WebSockets or
 * SockJS, etc...
 *
 * The incoming protocol is based on json messages:
 *
 * {
 * "action": "read",
 * "data": "what the user typed"
 * }
 *
 * or
 *
 * {
 * "action": "resize",
 * "cols": 30,
 * "rows: 50
 * }
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public abstract class HttpTtyConnection implements Connection {

    /** Default terminal size (80 columns x 24 rows). */
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

    /**
     * Creates a new HTTP TTY connection with default charset (UTF-8) and size (80x24).
     */
    public HttpTtyConnection() {
        this(StandardCharsets.UTF_8, DEFAULT_SIZE);
    }

    /**
     * Creates a new HTTP TTY connection with the specified charset and size.
     *
     * @param charset the character encoding for the connection
     * @param size the initial terminal size
     */
    public HttpTtyConnection(Charset charset, Size size) {
        this.charset = charset;
        this.size = size;
        this.eventDecoder = new EventDecoder(3, 4, 26);
        this.decoder = new Decoder(512, charset, eventDecoder);
        this.stdout = new TtyOutputMode(new Encoder(charset, this::write));

        this.device = new HttpDevice("vt100");
        attributes = new Attributes();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Charset outputEncoding() {
        return charset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Charset inputEncoding() {
        return charset;
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
     * {@inheritDoc}
     */
    @Override
    public Device device() {
        return device;
    }

    /**
     * Writes raw bytes to the underlying transport.
     *
     * @param buffer the bytes to write
     */
    protected abstract void write(byte[] buffer);

    /**
     * Processes an incoming JSON message from the client and writes it to the decoder.
     * Handles "read" actions (terminal input) and "resize" actions (terminal resize events).
     *
     * @param msg the JSON message from the client
     */
    @SuppressWarnings("unchecked")
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

    /**
     * Returns the handler for terminal type events.
     *
     * @return the terminal type handler, or null if not set
     */
    public Consumer<String> getTerminalTypeHandler() {
        return termHandler;
    }

    /**
     * Sets the handler for terminal type events.
     *
     * @param handler the handler to invoke when terminal type is received
     */
    public void setTerminalTypeHandler(Consumer<String> handler) {
        termHandler = handler;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Size size() {
        return size;
    }

    /**
     * Returns the handler for terminal size change events.
     *
     * @return the size handler, or null if not set
     */
    public Consumer<Size> getSizeHandler() {
        return sizeHandler;
    }

    /**
     * Sets the handler for terminal size change events.
     *
     * @param handler the handler to invoke when terminal size changes
     */
    public void setSizeHandler(Consumer<Size> handler) {
        this.sizeHandler = handler;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Consumer<Signal> getSignalHandler() {
        return eventDecoder.getSignalHandler();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSignalHandler(Consumer<Signal> handler) {
        eventDecoder.setSignalHandler(handler);
    }

    /**
     * Returns the handler for standard input data.
     *
     * @return the stdin handler, or null if not set
     */
    public Consumer<int[]> getStdinHandler() {
        return eventDecoder.getInputHandler();
    }

    /**
     * Sets the handler for standard input data.
     *
     * @param handler the handler to invoke when input is received
     */
    public void setStdinHandler(Consumer<int[]> handler) {
        eventDecoder.setInputHandler(handler);
    }

    /**
     * Returns the standard output handler for writing terminal output.
     *
     * @return the stdout handler
     */
    public Consumer<int[]> stdoutHandler() {
        return stdout;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCloseHandler(Consumer<Void> closeHandler) {
        this.closeHandler = closeHandler;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Consumer<Void> getCloseHandler() {
        return closeHandler;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void openBlocking() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void openNonBlocking() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean put(Capability capability, Object... params) {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Attributes getAttributes() {
        return attributes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setAttributes(Attributes attr) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean supportsAnsi() {
        return true;
    }
}
