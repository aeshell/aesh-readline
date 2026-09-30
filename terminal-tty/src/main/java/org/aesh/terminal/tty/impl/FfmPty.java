/*
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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.aesh.terminal.Attributes;
import org.aesh.terminal.tty.Size;

/**
 * FFM-based PTY implementation (Java 22+).
 * <p>
 * Base-layer stub: the real implementation lives in the Java 22 MRJAR
 * overlay ({@code META-INF/versions/22}) and uses {@code java.lang.foreign}
 * downcalls that cannot be named from release-8 sources. Every entry point
 * here declines, so reflection-only callers ({@code FfmTerminalProvider})
 * fall back to {@code ExecPty} on runtimes without the overlay.
 * <p>
 * The public member set must stay identical to the overlay class: MRJAR
 * validation rejects versioned classes that add public members or change
 * modifiers. Only release-8 types may appear in signatures here.
 */
public class FfmPty implements Pty {

    private FfmPty() throws IOException {
    }

    /**
     * Returns whether FFM native access is available.
     *
     * @return false on runtimes without the Java 22 overlay
     */
    public static boolean isNativeAccessEnabled() {
        return false;
    }

    /**
     * Opens the current terminal as an FFM PTY.
     *
     * @return never returns on runtimes without the Java 22 overlay
     * @throws IOException always, signalling the provider to fall back
     */
    public static Pty current() throws IOException {
        throw new IOException("FfmPty requires Java 22+ with native access enabled");
    }

    private static IOException unavailable() {
        return new IOException("FfmPty requires Java 22+ with native access enabled");
    }

    @Override
    public InputStream getMasterInput() {
        throw new UnsupportedOperationException("FfmPty requires Java 22+ with native access enabled");
    }

    @Override
    public OutputStream getMasterOutput() {
        throw new UnsupportedOperationException("FfmPty requires Java 22+ with native access enabled");
    }

    @Override
    public InputStream getSlaveInput() {
        throw new UnsupportedOperationException("FfmPty requires Java 22+ with native access enabled");
    }

    @Override
    public OutputStream getSlaveOutput() {
        throw new UnsupportedOperationException("FfmPty requires Java 22+ with native access enabled");
    }

    @Override
    public Attributes getAttr() throws IOException {
        throw unavailable();
    }

    @Override
    public void setAttr(Attributes attr) throws IOException {
        throw unavailable();
    }

    @Override
    public Size getSize() throws IOException {
        throw unavailable();
    }

    @Override
    public void close() throws IOException {
        throw unavailable();
    }

    @Override
    public boolean supportsNonBlockingRead() {
        return false;
    }

    @Override
    public int read(long timeoutMs) throws IOException {
        throw unavailable();
    }

    @Override
    public int peek(long timeoutMs) throws IOException {
        throw unavailable();
    }

    @Override
    public int read(byte[] b, int off, int len, long timeoutMs) throws IOException {
        throw unavailable();
    }

    /**
     * Returns the TTY device name for this PTY.
     *
     * @return never returns on runtimes without the Java 22 overlay
     */
    public String getName() {
        throw new UnsupportedOperationException("FfmPty requires Java 22+ with native access enabled");
    }
}
