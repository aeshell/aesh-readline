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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
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
import org.aesh.terminal.utils.LoggerUtil;

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
        if (inputCharset != null)
            this.inputCharset = inputCharset;
        else
            this.inputCharset = Charset.defaultCharset();
        if (outputCharset != null)
            this.outputCharset = outputCharset;
        else
            this.outputCharset = Charset.defaultCharset();
        this.handler = handler;
        init(TerminalBuilder.builder()
                .input(inputStream)
                .output(outputStream)
                .nativeSignals(true)
                .name("Aesh console")
                .build());
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
        this.inputCharset = Charset.defaultCharset();
        this.outputCharset = Charset.defaultCharset();
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
            if (sizeHandler() != null) {
                sizeHandler().accept(size());
            }
        });

        eventDecoder = new EventDecoder(attributes);
        decoder = new Decoder(512, inputEncoding(), eventDecoder);

        if (terminal.getCodePointConsumer() == null) {
            stdout = new Encoder(outputEncoding(), this::write);
        } else {
            stdout = terminal.getCodePointConsumer();
        }
        if (terminal instanceof ExternalTerminal)
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

    /**
     * Opens the Connection stream, this method will block and wait for input.
     */
    @Override
    public void openBlocking() {
        openBlocking(null);
    }

    /**
     * Opens the Connection stream with an initial buffer. This method will block and wait for input.
     *
     * @param buffer initial data to process before reading from the terminal input
     */
    public void openBlocking(String buffer) {
        try {
            reading = true;
            byte[] bBuf = new byte[1024];
            if (buffer != null) {
                decoder.write(buffer.getBytes(inputCharset));
            }
            while (reading) {
                int read = terminal.input().read(bBuf);
                if (read > 0) {
                    decoder.write(bBuf, 0, read);
                    if (waiting && reading) {
                        try {
                            latch.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            LOGGER.log(Level.WARNING,
                                    "Reader thread was interrupted while waiting on the latch", e);
                            close();
                        }
                    }
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

    private void write(byte[] data) {
        try {
            terminal.output().write(data);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to write out.", e);
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
    public void close() {
        try {
            reading = false;
            //call closeHandler before we close the terminal stream
            if (closeHandler() != null)
                closeHandler().accept(null);
            //reset signal/size handlers
            terminal.handle(Signal.INT, prevIntrHandler);
            terminal.handle(Signal.WINCH, prevWincHandler);
            terminal.handle(Signal.CONT, prevContHandler);

            //reset attributes
            if (attributes != null && terminal != null) {
                terminal.setAttributes(attributes);
                terminal.close();
            }
            if (latch != null)
                latch.countDown();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to close the terminal correctly", e);
        }
    }

}
