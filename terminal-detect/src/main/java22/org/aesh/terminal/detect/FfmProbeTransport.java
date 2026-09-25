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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * FFM-based probe transport for POSIX systems (Java 22+).
 * <p>
 * Replaces the {@code stty} subprocess round-trips with direct
 * {@code tcgetattr}/{@code tcsetattr} calls plus {@code read}/{@code write}
 * on {@code /dev/tty}. Response parsing stays in
 * {@link TerminalColorQuery}; this class only owns raw-mode I/O.
 * <p>
 * Loaded reflectively by {@link TerminalColorQuery} so Java 8 runtimes
 * (where this class does not exist) fall back to the stty path without
 * any linkage failure. All downcall handles are lazy
 * (see {@code FfmPosix}), so availability checks stay cheap.
 * <p>
 * On macOS the session uses stdin (fd 0) because {@code poll} and separate
 * {@code /dev/tty} opens are unreliable there; on Linux it opens
 * {@code /dev/tty} directly to bypass stdin/stdout redirection.
 */
final class FfmProbeTransport implements TerminalProbeTransport {

    private static final File DEV_TTY = new File("/dev/tty");

    FfmProbeTransport() {
    }

    @Override
    public boolean isAvailable() {
        if (FfmPosix.IS_WINDOWS) {
            return false;
        }
        if (!FfmPosix.isNativeAccessEnabled()) {
            return false;
        }
        return DEV_TTY.exists() && DEV_TTY.canRead() && DEV_TTY.canWrite();
    }

    @Override
    public TerminalProbeSession open() throws IOException {
        return new FfmProbeSession();
    }

    /**
     * Check native access without loading any FFM API classes.
     * Called reflectively before instantiation so callers can skip the
     * transport cheaply. Mirrors the {@code FfmPty.isNativeAccessEnabled}
     * pattern used by the terminal provider.
     *
     * @return true if native access is enabled for this module
     */
    static boolean isNativeAccessEnabled() {
        return FfmProbeTransport.class.getModule().isNativeAccessEnabled();
    }

    private static final class FfmProbeSession implements TerminalProbeSession {

        private static final int READ_BUF_SIZE = 4096;

        private final Arena arena;
        private final int ttyFd;
        private final boolean ownsFd;
        private final MemorySegment savedTermios;
        private final MemorySegment ioBuf;
        private final FfmProbeInput input;
        private boolean closed;

        FfmProbeSession() throws IOException {
            Arena sessionArena = Arena.ofConfined();
            boolean opened = false;
            try {
                int fd;
                boolean owns;
                if (FfmPosix.IS_MACOS) {
                    fd = 0;
                    owns = false;
                } else {
                    fd = FfmPosix.open("/dev/tty", FfmPosix.O_RDWR, sessionArena);
                    if (fd < 0) {
                        throw new IOException("Failed to open /dev/tty");
                    }
                    owns = true;
                }
                MemorySegment saved = sessionArena.allocate(FfmPosix.TERMIOS_SIZE, 8);
                try {
                    FfmPosix.tcgetattr(fd, saved);
                } catch (IOException e) {
                    if (owns) {
                        try {
                            FfmPosix.close(fd);
                        } catch (IOException ignored) {
                        }
                    }
                    throw e;
                }
                MemorySegment raw = sessionArena.allocate(FfmPosix.TERMIOS_SIZE, 8);
                MemorySegment.copy(saved, 0, raw, 0, FfmPosix.TERMIOS_SIZE);
                FfmPosix.setRawQueryMode(raw);
                try {
                    FfmPosix.tcsetattr(fd, FfmPosix.TCSAFLUSH, raw);
                } catch (IOException e) {
                    if (owns) {
                        try {
                            FfmPosix.close(fd);
                        } catch (IOException ignored) {
                        }
                    }
                    throw e;
                }
                this.arena = sessionArena;
                this.ttyFd = fd;
                this.ownsFd = owns;
                this.savedTermios = saved;
                this.ioBuf = sessionArena.allocate(READ_BUF_SIZE);
                this.input = new FfmProbeInput();
                opened = true;
            } finally {
                if (!opened) {
                    sessionArena.close();
                }
            }
        }

        @Override
        public void write(byte[] data) throws IOException {
            if (closed) {
                throw new IOException("Session is closed");
            }
            if (data == null || data.length == 0) {
                return;
            }
            try (Arena local = Arena.ofConfined()) {
                MemorySegment buf = local.allocate(data.length);
                MemorySegment.copy(data, 0, buf, ValueLayout.JAVA_BYTE, 0, data.length);
                long offset = 0;
                while (offset < data.length) {
                    long n = FfmPosix.write(ttyFd, buf.asSlice(offset), data.length - offset);
                    if (n < 0) {
                        throw new IOException("write() returned " + n);
                    }
                    if (n == 0) {
                        throw new IOException("write() wrote 0 bytes");
                    }
                    offset += n;
                }
            }
        }

        @Override
        public InputStream input() {
            return input;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                FfmPosix.tcsetattr(ttyFd, FfmPosix.TCSAFLUSH, savedTermios);
            } catch (IOException ignored) {
            }
            if (ownsFd) {
                try {
                    FfmPosix.close(ttyFd);
                } catch (IOException ignored) {
                }
            }
            arena.close();
        }

        /**
         * Response stream over the probe fd. Reads use the session's
         * raw-mode timeout ({@code VMIN=0 VTIME=5}, 0.5s): a {@code 0}
         * return ends the response, matching the stty path contract
         * documented on {@link TerminalProbeSession#input()}.
         */
        private final class FfmProbeInput extends InputStream {

            FfmProbeInput() {
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                if (b == null) {
                    throw new NullPointerException("buffer is null");
                }
                if (off < 0 || len < 0 || off + len > b.length) {
                    throw new IndexOutOfBoundsException("invalid offset/length");
                }
                if (len == 0) {
                    return 0;
                }
                if (closed) {
                    return -1;
                }
                long n = FfmPosix.read(ttyFd, ioBuf, Math.min(len, READ_BUF_SIZE));
                if (n <= 0) {
                    return (int) n;
                }
                MemorySegment.copy(ioBuf, ValueLayout.JAVA_BYTE, 0, b, off, (int) n);
                return (int) n;
            }

            @Override
            public int read() throws IOException {
                byte[] one = new byte[1];
                int n = read(one, 0, 1);
                if (n <= 0) {
                    return -1;
                }
                return one[0] & 0xFF;
            }

            @Override
            public void close() {
                // Owned by the session; session close releases everything.
            }
        }
    }
}
