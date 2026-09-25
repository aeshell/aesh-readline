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

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

/**
 * A single raw-mode terminal probe session, opened via
 * {@link TerminalProbeTransport#open()}. Used for one probe round
 * (write query bytes, read the response), then closed.
 * <p>
 * Closing the session must restore the terminal state captured at
 * {@code open()} time (echo, canonical mode, console flags).
 *
 * @since 3.18.3
 */
public interface TerminalProbeSession extends Closeable {

    /**
     * Write query bytes to the terminal.
     *
     * @param data the bytes to write (query sequences, US-ASCII except
     *        for the grapheme probe which is UTF-8)
     * @throws IOException if the write fails; the probe is skipped
     */
    void write(byte[] data) throws IOException;

    /**
     * The response stream. Reads observe the transport's timeout: a read
     * returning {@code 0} or {@code -1} ends the response (the built-in
     * transport uses {@code stty min 0 time 5}, a 500ms timeout). The
     * stream is owned by the session and closed by {@link #close()}.
     *
     * @return the terminal response stream
     */
    InputStream input();
}
