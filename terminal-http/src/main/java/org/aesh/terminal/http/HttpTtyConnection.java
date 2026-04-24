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
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.aesh.terminal.AbstractConnection;
import org.aesh.terminal.Attributes;
import org.aesh.terminal.Device;
import org.aesh.terminal.EventDecoder;
import org.aesh.terminal.io.Decoder;
import org.aesh.terminal.io.Encoder;
import org.aesh.terminal.tty.Capability;
import org.aesh.terminal.tty.Size;
import org.aesh.terminal.tty.TtyOutputMode;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A connection to an http client, independent of the protocol, it could be straight WebSockets or
 * SockJS, etc...
 *
 * <p>
 * The incoming protocol is based on JSON messages with an "action" field:
 * </p>
 *
 * <p>
 * <b>init</b> - Client capability reporting (sent on connect)
 * </p>
 *
 * <pre>{@code
 * {
 *   "action": "init",
 *   "type": "xterm-256color",
 *   "colorDepth": "TRUE_COLOR",
 *   "features": ["UNICODE", "CLIPBOARD"],
 *   "cols": 80,
 *   "rows": 24,
 *   "userAgent": "Mozilla/5.0 ..."
 * }
 * }</pre>
 *
 * <p>
 * <b>read</b> - User terminal input
 * </p>
 *
 * <pre>{@code
 * {
 *   "action": "read",
 *   "data": "what the user typed"
 * }
 * }</pre>
 *
 * <p>
 * <b>resize</b> - Terminal size change
 * </p>
 *
 * <pre>{@code
 * {
 *   "action": "resize",
 *   "cols": 120,
 *   "rows": 40
 * }
 * }</pre>
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public abstract class HttpTtyConnection extends AbstractConnection {

    private static final Logger LOGGER = Logger.getLogger(HttpTtyConnection.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Default terminal size (80 columns x 24 rows). */
    public static final Size DEFAULT_SIZE = new Size(80, 24);
    private final Device device;

    private final Charset charset;
    private Size size;
    private final Decoder decoder;
    private Consumer<String> termHandler;
    private long lastAccessedTime = System.currentTimeMillis();
    private boolean initialized = false;

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

        this.device = new HttpDevice();
        this.attributes = new Attributes();
    }

    /**
     * Returns whether the client has sent an init message.
     *
     * @return true if the client has initialized the connection
     */
    public boolean isInitialized() {
        return initialized;
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
     * <p>
     * Handles the following actions:
     * <ul>
     * <li><b>init</b> - Client capability reporting (type, colorDepth, features, cols, rows)</li>
     * <li><b>read</b> - Terminal input from the user</li>
     * <li><b>resize</b> - Terminal resize events</li>
     * </ul>
     *
     * @param msg the JSON message from the client
     */
    @SuppressWarnings("unchecked")
    public void writeToDecoder(String msg) {
        Map<String, Object> obj;
        String action;
        try {
            obj = MAPPER.readValue(msg, Map.class);
            action = (String) obj.get("action");
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to parse JSON message", e);
            return;
        }
        if (action != null) {
            switch (action) {
                case "init":
                    handleInit(obj);
                    break;
                case "read":
                    lastAccessedTime = System.currentTimeMillis();
                    String data = (String) obj.get("data");
                    decoder.write(data.getBytes()); //write back echo
                    break;
                case "resize":
                    handleResize(obj);
                    break;
            }
        }
    }

    /**
     * Handles the init action from the client, updating device capabilities.
     *
     * @param obj the parsed JSON message
     */
    @SuppressWarnings("unchecked")
    private void handleInit(Map<String, Object> obj) {
        lastAccessedTime = System.currentTimeMillis();
        initialized = true;

        HttpDevice httpDevice = (HttpDevice) device;

        // Update terminal type if provided
        String type = (String) obj.get("type");
        if (type != null && !type.isEmpty()) {
            httpDevice.setType(type);
            if (termHandler != null) {
                termHandler.accept(type);
            }
        }

        // Store color depth
        String colorDepth = (String) obj.get("colorDepth");
        if (colorDepth != null) {
            httpDevice.setReportedColorDepth(colorDepth);
        }

        // Store features
        Object featuresObj = obj.get("features");
        if (featuresObj instanceof List) {
            httpDevice.setFeatures((List<String>) featuresObj);
        }

        // Store user agent
        String userAgent = (String) obj.get("userAgent");
        if (userAgent != null) {
            httpDevice.setUserAgent(userAgent);
        }

        // Handle initial size
        handleResize(obj);
    }

    /**
     * Handles the resize action from the client.
     *
     * @param obj the parsed JSON message
     */
    private void handleResize(Map<String, Object> obj) {
        try {
            Object colsObj = obj.get("cols");
            Object rowsObj = obj.get("rows");
            int cols = colsObj != null ? ((Number) colsObj).intValue() : size.getWidth();
            int rows = rowsObj != null ? ((Number) rowsObj).intValue() : size.getHeight();
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
            LOGGER.log(Level.FINE, "Invalid resize data, ignoring", e);
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
     * {@inheritDoc}
     */
    @Override
    public void close() {
        reading = false;
        if (closeHandler != null) {
            closeHandler.accept(null);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void openBlocking() {
        reading = true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void openNonBlocking() {
        reading = true;
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
