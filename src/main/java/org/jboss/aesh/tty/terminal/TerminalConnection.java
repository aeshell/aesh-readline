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
package org.jboss.aesh.tty.terminal;

import org.jboss.aesh.io.Decoder;
import org.jboss.aesh.io.Encoder;
import org.jboss.aesh.terminal.Attributes;
import org.jboss.aesh.terminal.Terminal;
import org.jboss.aesh.terminal.TerminalBuilder;
import org.jboss.aesh.tty.Connection;
import org.jboss.aesh.tty.Signal;
import org.jboss.aesh.tty.Size;
import org.jboss.aesh.util.LoggerUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class TerminalConnection implements Connection {

    private Terminal terminal;

    private static final Logger LOGGER = LoggerUtil.getLogger(TerminalConnection.class.getName());

    private Consumer<Size> sizeHandler;
    private Decoder decoder;
    private Consumer<int[]> stdOut;
    private Consumer<int[]> inputHandler;
    private Attributes attributes;
    private Consumer<Signal> eventHandler;
    private volatile boolean reading = true;
    private Consumer<Void> closeHandler;

    public TerminalConnection(InputStream inputStream, OutputStream outputStream) {
        try {
            init(TerminalBuilder.builder()
                    .streams(inputStream, outputStream)
                    .nativeSignals(true)
                    .name("Aesh console")
                    .build());
        }
        catch(IOException e) {
            e.printStackTrace();
        }
    }

    public TerminalConnection() {
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
                LOGGER.info("No signal handler is registered, lets stop");
                close();
            }
        });
        //window resize signal
        this.terminal.handle(Signal.WINCH, s -> {
            if(getSizeHandler() != null) {
                getSizeHandler().accept(size());
            }
        });

        decoder = new Decoder(StandardCharsets.UTF_8, inputHandler);
        stdOut = new Encoder(StandardCharsets.UTF_8, this::write);
    }

    public void startNonBlockingReader() {
        ExecutorService executorService = Executors.newSingleThreadExecutor(runnable -> {
            Thread inputThread = Executors.defaultThreadFactory().newThread(runnable);
            inputThread.setName("Aesh InputStream Reader");
            //need to be a daemon, if not it will block on shutdown
            inputThread.setDaemon(true);
            return inputThread;
        });
        executorService.execute(() -> startBlockingReader());
    }

    public void startBlockingReader() {
        try {
            byte[] bBuf = new byte[1024];
            attributes = terminal.enterRawMode();
            while (reading) {
                int read = terminal.input().read(bBuf);
                if (read > 0) {
                    decoder.write(bBuf, 0, read);
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

    private void stop() {
        reading = false;
    }

    private void write(byte[] data) {
        try {
            terminal.output().write(data);
        }
        catch(IOException e) {
            e.printStackTrace();
        }
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
        return eventHandler;
    }

    @Override
    public void setSignalHandler(Consumer<Signal> handler) {
        eventHandler = handler;
    }

    @Override
    public Consumer<int[]> getStdinHandler() {
        return inputHandler;
    }

    @Override
    public void setStdinHandler(Consumer<int[]> handler) {
        inputHandler = handler;
        decoder.setConsumer(inputHandler);
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
            stop();
            if (attributes != null && terminal != null) {
                terminal.setAttributes(attributes);
                terminal.close();
            }
        }
        catch(IOException e) {
            e.printStackTrace();
        }
    }

}
