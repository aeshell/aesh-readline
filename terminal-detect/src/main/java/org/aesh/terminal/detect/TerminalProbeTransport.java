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
package org.aesh.terminal.detect;

import java.io.IOException;

/**
 * Transport for standalone terminal probing (OSC color queries, DA1,
 * DECRQM mode probes, cursor-position probes).
 * <p>
 * The built-in transport talks to {@code /dev/tty} with {@code stty}
 * raw-mode handling, which only works on POSIX systems. Implement this
 * interface to probe terminals where {@code /dev/tty} is unavailable —
 * for example via the Win32 Console API ({@code GetConsoleMode} /
 * {@code SetConsoleMode} for raw mode, {@code WriteConsoleW} /
 * {@code ReadConsoleInputW} for I/O) on native Windows — and register
 * it with {@link TerminalCapabilities#setProbeTransport}.
 * <p>
 * The interface lives in this zero-dependency module so providers in
 * other modules can implement it without inverting the dependency
 * direction: this module never depends on them.
 *
 * @since 3.18.3
 */
public interface TerminalProbeTransport {

    /**
     * Check whether probing is currently possible through this transport
     * (console attached, handles valid, device present). Checked before
     * every probe; a {@code false} result skips probing gracefully.
     *
     * @return true if {@link #open()} is expected to succeed
     */
    boolean isAvailable();

    /**
     * Enter raw mode and open a probe session. The session owns the
     * raw-mode lifecycle: closing it must restore the terminal state.
     *
     * @return an open probe session
     * @throws IOException if raw mode cannot be entered or the terminal
     *         cannot be opened; the probe is skipped
     */
    TerminalProbeSession open() throws IOException;
}
