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

    @Override
    public TerminalProbeSession open() throws IOException {
        String savedState = sttyGet();
        if (savedState == null) {
            throw new IOException("stty unavailable");
        }
        sttyRaw();
        return new SttyProbeSession(savedState);
    }

    private static final class SttyProbeSession implements TerminalProbeSession {
        private final String savedState;
        private final FileOutputStream ttyOut;
        private final FileInputStream ttyIn;

        SttyProbeSession(String savedState) throws IOException {
            this.savedState = savedState;
            FileOutputStream out = null;
            try {
                out = new FileOutputStream(DEV_TTY);
                FileInputStream in = new FileInputStream(DEV_TTY);
                this.ttyOut = out;
                this.ttyIn = in;
            } catch (IOException e) {
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException ignored) {
                    }
                }
                sttyRestore(savedState);
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
                sttyRestore(savedState);
            }
        }
    }

    private static String sttyGet() {
        Process p = null;
        try {
            p = new ProcessBuilder("stty", "-g")
                    .redirectInput(DEV_TTY)
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
        } catch (Exception ignored) {
            return null;
        } finally {
            if (p != null)
                p.destroy();
        }
    }

    private static void sttyRaw() {
        Process p = null;
        try {
            p = new ProcessBuilder("stty", "-echo", "-icanon", "-ixon", "min", "0", "time", "5")
                    .redirectInput(DEV_TTY)
                    .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                    .redirectErrorStream(true)
                    .start();
            p.waitFor();
        } catch (Exception ignored) {
        } finally {
            if (p != null)
                p.destroy();
        }
    }

    private static void sttyRestore(String savedState) {
        Process p = null;
        try {
            p = new ProcessBuilder("stty", savedState)
                    .redirectInput(DEV_TTY)
                    .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                    .redirectErrorStream(true)
                    .start();
            p.waitFor();
        } catch (Exception ignored) {
        } finally {
            if (p != null)
                p.destroy();
        }
    }
}
