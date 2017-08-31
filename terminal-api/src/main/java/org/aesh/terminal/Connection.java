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
package org.aesh.terminal;

import org.aesh.terminal.tty.Point;
import org.aesh.terminal.tty.Size;
import org.aesh.terminal.tty.Capability;

import java.nio.charset.Charset;
import java.util.EnumSet;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import org.aesh.terminal.tty.Signal;
import org.aesh.utils.ANSI;

/**
 * Represent a connection to either a local/direct/remote Terminal.
 *
 * @author <a href=mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public interface Connection {

    /**
     * @return type of terminal
     */
    Device device();

    /**
     * @return terminal size
     */
    Size size();

    /**
     * @return Handler that's called when the terminal changes size
     */
    Consumer<Size> getSizeHandler();

    /**
     * Specify size handler that's called when the terminal changes size.
     * @param handler
     */
    void setSizeHandler(Consumer<Size> handler);

    /**
     * Get SignalHandler. A handler that's called when a Signal is sent to the terminal
     * @return Signal handler
     */
    Consumer<Signal> getSignalHandler();

    /**
     * Specify the signal handler.
     * A handler that's called when a Signal is sent to the terminal
     * @param handler signal handler
     */
    void setSignalHandler(Consumer<Signal> handler);

    Consumer<int[]> getStdinHandler();

    void setStdinHandler(Consumer<int[]> handler);

    /**
     * Handler that's called for all output
     * @return output handler
     */
    Consumer<int[]> stdoutHandler();

    /**
     * Specify handler that's called when the input stream is closed.
     * @param closeHandler handler
     */
    void setCloseHandler(Consumer<Void> closeHandler);

    /**
     * @return handler thats called when the input stream is closed.
     */
    Consumer<Void> getCloseHandler();

    /**
     * Stop reading from the input stream.
     * The stream will be closed and cleanup methods will be called
     * Eg for terminals they will be restored to their original settings.
     *
     * Note that if the reader thread is blocking waiting for data it will wait until either
     * killed or if the input stream is closed.
     */
    void close();

    /**
     * Start reading from the input stream using the current thread.
     * The current thread will be blocked while reading/waiting to read from the stream
     */
    void openBlocking();

    /**
     * Start reading from the input stream in a separate thread.
     * The current thread will continue.
     */
    void openNonBlocking();

    /**
     * Specify terminal settings
     * @param capability capability
     * @param params parameters
     * @return true if the terminal accepted the settings
     */
    boolean put(Capability capability, Object... params);

    Attributes getAttributes();

    void setAttributes(Attributes attr);

    Charset inputEncoding();

    Charset outputEncoding();

    boolean supportsAnsi();

    /**
     * Write a string to the output handler
     * @param s string
     * @return this connection
     */
    default Connection write(String s) {
        int[] codePoints = s.codePoints().toArray();
        stdoutHandler().accept(codePoints);
        return this;
    }

    default Attributes enterRawMode() {
        Attributes prvAttr = getAttributes();
        Attributes newAttr = new Attributes(prvAttr);
        newAttr.setLocalFlags(EnumSet.of(Attributes.LocalFlag.ICANON, Attributes.LocalFlag.ECHO, Attributes.LocalFlag.IEXTEN), false);
        newAttr.setInputFlags(EnumSet.of(Attributes.InputFlag.IXON, Attributes.InputFlag.ICRNL, Attributes.InputFlag.INLCR), false);
        newAttr.setControlChar(Attributes.ControlChar.VMIN, 1);
        newAttr.setControlChar(Attributes.ControlChar.VTIME, 0);
        newAttr.setControlChar(Attributes.ControlChar.VINTR, 0);
        setAttributes(newAttr);
        return prvAttr;
    }

    default Point getCursorPosition() {
        Consumer<int[]> prevInputHandler = getStdinHandler();
        CountDownLatch latch = new CountDownLatch(1);
        final Point[] p = {null};
        Attributes attributes = enterRawMode();
        setStdinHandler(ints -> {
            p[0] = ANSI.getActualCursor(ints);
            setStdinHandler(prevInputHandler);
            latch.countDown();
            setAttributes(attributes);
        });
        stdoutHandler().accept("\u001B[6n".codePoints().toArray());
        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return p[0];
    }

}
