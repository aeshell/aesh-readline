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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Built-in POSIX probe transport: {@code /dev/tty} with {@code stty}
 * subprocesses for raw-mode handling.
 * <p>
 * This is the fallback transport, tried after the FFM and Win32
 * transports. It works on any Java version but spawns three
 * subprocesses per probe (save state, raw mode, restore). Response
 * parsing stays in {@link TerminalColorQuery}; this class only owns
 * raw-mode I/O.
 */
final class SttyProbeTransport implements TerminalProbeTransport {

    static final SttyProbeTransport INSTANCE = new SttyProbeTransport();

    private static final File DEV_TTY = new File("/dev/tty");

    private SttyProbeTransport() {
    }

    @Override
    public boolean isAvailable() {
        return DEV_TTY.exists() && DEV_TTY.canRead() && DEV_TTY.canWrite();
    }

    /**
     * Open a probe session, capturing the current terminal state first.
     * <p>
     * Raw-mode setup must succeed: a session in canonical mode would
     * block reads without the assumed VMIN/VTIME timeout.
     *
     * @return an open session with raw mode established
     * @throws IOException if the terminal state cannot be saved, raw
     *         mode cannot be established, or the streams cannot be opened
     */
    @Override
    public TerminalProbeSession open() throws IOException {
        return open("stty", DEV_TTY);
    }

    /**
     * Open a probe session against an explicit command and tty device.
     * Package visible so tests can substitute a fixture command without
     * touching a real terminal.
     *
     * @param command the stty-compatible executable
     * @param tty the terminal device file
     * @return an open session with raw mode established
     * @throws IOException if setup fails at any step; the saved state is
     *         restored before propagating raw-mode and stream failures
     */
    static TerminalProbeSession open(String command, File tty) throws IOException {
        String savedState = sttyGet(command, tty);
        if (savedState == null) {
            throw new IOException("stty unavailable");
        }
        try {
            sttyRaw(command, tty);
        } catch (IOException | RuntimeException e) {
            sttyRestore(command, tty, savedState);
            throw e;
        }
        return new SttyProbeSession(command, tty, savedState);
    }

    private static final class SttyProbeSession implements TerminalProbeSession {
        private final String command;
        private final File tty;
        private final String savedState;
        private final FileOutputStream ttyOut;
        private final FileInputStream ttyIn;

        SttyProbeSession(String command, File tty, String savedState) throws IOException {
            this.command = command;
            this.tty = tty;
            this.savedState = savedState;
            FileOutputStream out = null;
            try {
                out = new FileOutputStream(tty);
                FileInputStream in = new FileInputStream(tty);
                this.ttyOut = out;
                this.ttyIn = in;
            } catch (IOException e) {
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException ignored) {
                    }
                }
                sttyRestore(command, tty, savedState);
                throw e;
            }
        }

        @Override
        public void write(byte[] data) throws IOException {
            ttyOut.write(data);
            ttyOut.flush();
        }

        @Override
        public InputStream input() {
            return ttyIn;
        }

        @Override
        public void close() {
            try {
                ttyIn.close();
            } catch (IOException ignored) {
            }
            try {
                ttyOut.close();
            } catch (IOException ignored) {
            } finally {
                sttyRestore(command, tty, savedState);
            }
        }
    }

    private static String sttyGet(String command, File tty) {
        Process p = null;
        try {
            p = new ProcessBuilder(command, "-g")
                    .redirectInput(tty)
                    .redirectErrorStream(true)
                    .start();
            byte[] buf = new byte[256];
            StringBuilder sb = new StringBuilder();
            int n;
            while ((n = p.getInputStream().read(buf)) != -1) {
                sb.append(new String(buf, 0, n));
            }
            p.waitFor();
            return p.exitValue() == 0 ? sb.toString().trim() : null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception ignored) {
            return null;
        } finally {
            if (p != null)
                p.destroy();
        }
    }

    private static void sttyRaw(String command, File tty) throws IOException {
        Process p = null;
        try {
            p = new ProcessBuilder(command, "-echo", "-icanon", "-ixon", "min", "0", "time", "5")
                    .redirectInput(tty)
                    .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                    .redirectErrorStream(true)
                    .start();
            p.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while setting terminal raw mode", e);
        } finally {
            if (p != null)
                p.destroy();
        }
        if (p.exitValue() != 0) {
            throw new IOException("stty raw mode failed with exit code " + p.exitValue());
        }
    }

    private static void sttyRestore(String command, File tty, String savedState) {
        Process p = null;
        try {
            p = new ProcessBuilder(command, savedState)
                    .redirectInput(tty)
                    .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                    .redirectErrorStream(true)
                    .start();
            p.waitFor();
        } catch (InterruptedException e) {
            // Best-effort restore must not swallow the interrupt that
            // caused the failure being cleaned up.
            Thread.currentThread().interrupt();
        } catch (Exception ignored) {
        } finally {
            if (p != null)
                p.destroy();
        }
    }
}
