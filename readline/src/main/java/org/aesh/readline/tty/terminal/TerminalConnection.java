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
package org.aesh.readline.tty.terminal;

import org.aesh.io.Decoder;
import org.aesh.io.Encoder;
import org.aesh.readline.terminal.impl.ExternalTerminal;
import org.aesh.terminal.Device;
import org.aesh.terminal.Attributes;
import org.aesh.terminal.EventDecoder;
import org.aesh.terminal.Terminal;
import org.aesh.readline.terminal.TerminalBuilder;
import org.aesh.terminal.tty.Capability;
import org.aesh.terminal.Connection;
import org.aesh.util.LoggerUtil;

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
import org.aesh.terminal.tty.Signal;
import org.aesh.terminal.tty.Size;

/**
 * Implementation of Connection meant for local terminal connections.
 *
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class TerminalConnection implements Connection {

    private final Charset inputCharset;
    private final Charset outputCharset;
    private Terminal terminal;

    private static final Logger LOGGER = LoggerUtil.getLogger(TerminalConnection.class.getName());

    private Consumer<Size> sizeHandler;
    private Decoder decoder;
    private Encoder stdOut;
    private Attributes attributes;
    private EventDecoder eventDecoder;
    private volatile boolean reading = false;
    private Consumer<Void> closeHandler;
    private Consumer<Connection> handler;
    private CountDownLatch latch;
    private volatile boolean waiting = false;
    private Terminal.SignalHandler prevIntrHandler;
    private Terminal.SignalHandler prevWincHandler;
    private Terminal.SignalHandler prevContHandler;
    private boolean ansi = true;

    public TerminalConnection(Charset inputCharset, Charset outputCharset, InputStream inputStream,
                              OutputStream outputStream, Consumer<Connection> handler) throws IOException {
        if(inputCharset != null)
            this.inputCharset = inputCharset;
        else
            this.inputCharset = Charset.defaultCharset();
        if(outputCharset != null)
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

    public TerminalConnection(Charset charset, InputStream inputStream,
                              OutputStream outputStream, Consumer<Connection> handler) throws IOException {
        this(charset, charset, inputStream, outputStream, handler);
    }

    public TerminalConnection(Charset charset, InputStream inputStream, OutputStream outputStream) throws IOException {
        this(charset, charset, inputStream, outputStream, null);
    }

    public TerminalConnection() throws IOException {
        this(Charset.defaultCharset(), System.in, System.out);
    }

    public TerminalConnection(Consumer<Connection> handler) throws IOException {
        this(Charset.defaultCharset(), Charset.defaultCharset(), System.in, System.out, handler);
    }

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
            if(getSignalHandler() != null) {
                getSignalHandler().accept(s);
            }
            else {
                LOGGER.log(Level.FINE, "No signal handler is registered, lets stop");
                close();
            }
        });
        prevContHandler = this.terminal.handle(Signal.CONT, s -> {
            if(getSignalHandler() != null)
                getSignalHandler().accept(s);
        });
        //window resize signal
        prevWincHandler = this.terminal.handle(Signal.WINCH, s -> {
            if(getSizeHandler() != null) {
                getSizeHandler().accept(size());
            }
        });

        eventDecoder = new EventDecoder(attributes);
        decoder = new Decoder(512, inputEncoding(), eventDecoder);
        stdOut = new Encoder(outputEncoding(), this::write);

        if(terminal instanceof ExternalTerminal)
            ansi = false;

        if(handler != null)
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
        return terminal.device().puts(stdoutHandler(), capability);
    }

    @Override
    public Attributes getAttributes() {
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
                    if(waiting && reading) {
                        try {
                            latch.await();
                        }
                        catch(InterruptedException e) {
                            Thread.currentThread().interrupt();
                            LOGGER.log(Level.WARNING,
                                    "Reader thread was interrupted while waiting on the latch", e);
                            close();
                        }
                    }
                }
                else if (read < 0) {
                    close();
                }
            }
        }
        catch (IOException ioe) {
            LOGGER.log(Level.WARNING, "Failed while reading, exiting", ioe);
            close();
        }
    }

    public void suspend() {
        if(!waiting) {
            latch = new CountDownLatch(1);
            waiting = true;
        }
    }

    public void awake() {
        if(waiting) {
            waiting = false;
            if(latch != null)
                latch.countDown();
        }
    }

    public boolean suspended() {
        return waiting;
    }

    public boolean isReading() {
        return reading;
    }

     public void stopReading() {
        reading = false;
        awake();
    }

    private void write(byte[] data) {
        try {
            terminal.output().write(data);
        }
        catch(IOException e) {
            LOGGER.log(Level.WARNING, "Failed to write out.",e);
        }
    }

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
    public Consumer<int[]> getStdinHandler() {
        return eventDecoder.getInputHandler();
    }

    @Override
    public void setStdinHandler(Consumer<int[]> handler) {
        eventDecoder.setInputHandler(handler);
        if(handler == null)
            suspend();
        else
            awake();
    }

    @Override
    public Consumer<int[]> stdoutHandler() {
        return stdOut;
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
    public void close() {
        try {
            reading = false;
            //call closeHandler before we close the terminal stream
            if(getCloseHandler() != null)
                getCloseHandler().accept(null);
            //reset signal/size handlers
            terminal.handle(Signal.INT, prevIntrHandler);
            terminal.handle(Signal.WINCH, prevWincHandler);
            terminal.handle(Signal.CONT, prevContHandler);

            //reset attributes
            if (attributes != null && terminal != null) {
                terminal.setAttributes(attributes);
                terminal.close();
            }
            if(latch != null)
                latch.countDown();
        }
        catch(IOException e) {
            LOGGER.log(Level.WARNING, "Failed to close the terminal correctly", e);
        }
    }

}
