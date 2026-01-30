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
package org.aesh.terminal.tty.impl;

import org.aesh.terminal.Terminal;
import org.aesh.terminal.tty.Signal;

/**
 * Native signal handler implementation that provides default and ignore signal handling.
 * This class provides two singleton instances: SIG_DFL for default signal handling
 * and SIG_IGN for ignoring signals.
 */
public final class NativeSignalHandler implements Terminal.SignalHandler {

    /** Default signal handler that performs the default action for the signal. */
    public static final NativeSignalHandler SIG_DFL = new NativeSignalHandler();

    /** Signal handler that ignores the signal. */
    public static final NativeSignalHandler SIG_IGN = new NativeSignalHandler();

    /**
     * Private constructor to prevent instantiation.
     */
    private NativeSignalHandler() {
    }

    /**
     * Handles the given signal.
     * This implementation throws UnsupportedOperationException as actual
     * handling is performed by the native signal mechanism.
     *
     * @param signal the signal to handle
     * @throws UnsupportedOperationException always thrown by this implementation
     */
    public void handle(Signal signal) {
        throw new UnsupportedOperationException();
    }
}
