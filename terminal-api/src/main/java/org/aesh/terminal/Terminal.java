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
package org.aesh.terminal;

import java.io.Closeable;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Consumer;

import org.aesh.terminal.tty.Signal;
import org.aesh.terminal.tty.Size;

/**
 * Terminal interface providing access to terminal functionality.
 */
public interface Terminal extends Closeable {

    /**
     * Get the terminal name.
     *
     * @return the terminal name
     */
    String getName();

    /**
     * Handler for terminal signals.
     */
    interface SignalHandler {
        /**
         * Handle a terminal signal.
         *
         * @param signal the signal to handle
         */
        void handle(Signal signal);
    }

    /**
     * Register a signal handler for the specified signal.
     *
     * @param signal the signal to handle
     * @param handler the handler to register
     * @return the previous handler, or null if none
     */
    SignalHandler handle(Signal signal, SignalHandler handler);

    /**
     * Raise a signal on this terminal.
     *
     * @param signal the signal to raise
     */
    void raise(Signal signal);

    /**
     * Get the terminal input stream.
     *
     * @return the input stream
     */
    InputStream input();

    /**
     * Get the terminal output stream.
     *
     * @return the output stream
     */
    OutputStream output();

    /**
     * Get the current echo state of the terminal.
     *
     * @return true if echo is enabled
     */
    boolean echo();

    /**
     * Set the echo state of the terminal.
     *
     * @param echo true to enable echo
     * @return the previous echo state
     */
    boolean echo(boolean echo);

    /**
     * Get the terminal attributes.
     *
     * @return the terminal attributes
     */
    Attributes getAttributes();

    /**
     * Set the terminal attributes.
     *
     * @param attr the attributes to set
     */
    void setAttributes(Attributes attr);

    /**
     * Get the terminal size.
     *
     * @return the terminal size
     */
    Size getSize();

    /**
     * Get the terminal device for capability queries.
     *
     * @return the terminal device
     */
    Device device();

    /**
     * Get the code point consumer for this terminal.
     *
     * @return the code point consumer, or null if none
     */
    default Consumer<int[]> getCodePointConsumer() {
        return null;
    }
}
