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
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class TerminalConnection implements Connection {

    private Terminal terminal;

    private static final Logger LOGGER = LoggerUtil.getLogger(TerminalConnection.class.getName());

    private Consumer<Size> sizeHandler;
    private Decoder decoder;
    private Consumer<int[]> stdOut;
    private Attributes attributes;
    private EventDecoder eventDecoder = new EventDecoder();
    private volatile boolean reading = false;
    private volatile boolean close = false;
    private Consumer<Void> closeHandler;
    private CountDownLatch latch;
    private volatile boolean waiting = false;

    public TerminalConnection(InputStream inputStream, OutputStream outputStream) throws IOException {
            init(TerminalBuilder.builder()
                    .input(inputStream)
                    .output(outputStream)
                    .nativeSignals(true)
                    .name("Aesh console")
                    .build());
    }

    public TerminalConnection() throws IOException {
        this(System.in, System.out);
    }

    public TerminalConnection(Terminal terminal) {
        init(terminal);
    }

    private void init(Terminal term) {
        this.terminal = term;
        //interrupt signal
        this.terminal.handle(Signal.INT, s -> {
            if(getSignalHandler() != null) {
                getSignalHandler().accept(s);
            }
            else {
                LOGGER.log(Level.FINE, "No signal handler is registered, lets stop");
                close();
            }
        });
        //window resize signal
        this.terminal.handle(Signal.WINCH, s -> {
            if(getSizeHandler() != null) {
                getSizeHandler().accept(size());
            }
        });

        decoder = new Decoder(512, Charset.defaultCharset(), eventDecoder);
        stdOut = new Encoder(Charset.defaultCharset(), this::write);
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
    public void stopReading() {
        reading = false;
    }

    @Override
    public boolean put(Capability capability, Object... params) {
        return terminal.puts(capability, params);
    }

    /**
     * Opens the Connection stream, this method will block and wait for input.
     */
    @Override
    public void openBlocking() {
        try {
            reading = true;
            byte[] bBuf = new byte[1024];
            if(attributes == null)
                attributes = terminal.enterRawMode();
            while (reading) {
                int read = terminal.input().read(bBuf);
                if (read > 0) {
                    decoder.write(bBuf, 0, read);
                    if(waiting) {
                        try {
                            latch.await();
                        }
                        catch(InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
                else if (read < 0) {
                    if(getCloseHandler() != null)
                        getCloseHandler().accept(null);
                    close();
                    return;
                }
            }
        }
        catch (IOException ioe) {
            LOGGER.log(Level.WARNING, "Failed while reading, exiting", ioe);
            if(getCloseHandler() != null)
                getCloseHandler().accept(null);
            close();
        }
    }

    public boolean isReading() {
        return reading;
    }

    private void write(byte[] data) {
        try {
            terminal.output().write(data);
        }
        catch(IOException e) {
            LOGGER.log(Level.WARNING, "Failed to write out.",e);
        }
    }

    @Override
    public void suspend() {
        latch = new CountDownLatch(1);
        waiting = true;
    }

    @Override
    public void awake() {
        if(waiting) {
            waiting = false;
            latch.countDown();
        }
    }

    @Override
    public boolean suspended() {
        return waiting;
    }

    public Terminal getTerminal() {
        return terminal;
    }

    @Override
    public String terminalType() {
        return terminal.getName();
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
            close = reading = false;
            if(waiting)
                latch.countDown();
            if (attributes != null && terminal != null) {
                terminal.setAttributes(attributes);
                terminal.close();
                if(getCloseHandler() != null)
                    getCloseHandler().accept(null);
            }
        }
        catch(IOException e) {
            LOGGER.log(Level.WARNING, "Failed to close the terminal correctly", e);
        }
    }

}
