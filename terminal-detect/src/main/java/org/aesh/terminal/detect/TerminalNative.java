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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * JNI bridge to POSIX terminal functions for terminal-detect.
 * <p>
 * Provides direct access to {@code tcgetattr}/{@code tcsetattr} without
 * spawning stty subprocesses. The native library is loaded from the system
 * library path or extracted from the JAR at runtime.
 * <p>
 * The termios struct is passed as an opaque {@code byte[]} — all flag
 * interpretation happens in Java, keeping the C code minimal and
 * platform-independent.
 */
final class TerminalNative {

    private static final boolean AVAILABLE;

    static {
        AVAILABLE = loadLibrary();
    }

    /**
     * Returns true if the native library was loaded successfully.
     */
    static boolean isAvailable() {
        return AVAILABLE;
    }

    /**
     * Opens /dev/tty and returns the file descriptor.
     *
     * @return the file descriptor, or -1 on error
     */
    static native int openTty();

    /**
     * Closes a file descriptor.
     *
     * @param fd the file descriptor to close
     * @return 0 on success, -1 on error
     */
    static native int closeFd(int fd);

    /**
     * Gets the current terminal attributes as a raw termios byte array.
     *
     * @param fd the terminal file descriptor
     * @return the raw termios struct bytes, or null on error
     */
    static native byte[] tcgetattr(int fd);

    /**
     * Sets the terminal attributes from a raw termios byte array.
     *
     * @param fd the terminal file descriptor
     * @param action the action (0=TCSANOW, 1=TCSADRAIN, 2=TCSAFLUSH)
     * @param termios the raw termios struct bytes
     * @return 0 on success, -1 on error
     */
    static native int tcsetattr(int fd, int action, byte[] termios);

    /**
     * Returns the size of the termios struct on this platform.
     * Used to validate that Java and C agree on the struct size.
     *
     * @return the sizeof(struct termios) in bytes
     */
    static native int termiosSize();

    /**
     * Writes all bytes to a file descriptor, handling partial writes.
     *
     * @param fd the file descriptor
     * @param data the bytes to write
     * @return the number of bytes written, or -1 on error
     */
    static native int writeAll(int fd, byte[] data);

    /**
     * Reads terminal responses with a timeout, exiting early when all
     * expected responses are received. Uses poll() for precise timeout
     * control and counts response terminators (BEL, ST, DA1 'c') to
     * detect completion.
     *
     * @param fd the file descriptor
     * @param timeoutMs maximum time to wait for responses
     * @param expectedResponses number of response terminators to expect
     * @return the accumulated bytes, or null if nothing was read
     */
    static native byte[] readWithTimeout(int fd, int timeoutMs, int expectedResponses);

    private static boolean loadLibrary() {
        try {
            System.loadLibrary("terminal-detect");
            return true;
        } catch (UnsatisfiedLinkError ignore) {
            // Not on system path, try extracting from JAR
        }

        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "");
        if ("amd64".equals(arch) || "x86_64".equals(arch)) {
            arch = "x86_64";
        } else if ("aarch64".equals(arch) || "arm64".equals(arch)) {
            arch = "aarch64";
        }

        String osDir;
        String libName;
        if (os.contains("mac") || os.contains("darwin")) {
            osDir = "darwin-" + arch;
            libName = "libterminal-detect.dylib";
        } else if (os.contains("linux")) {
            osDir = "linux-" + arch;
            libName = "libterminal-detect.so";
        } else {
            return false; // unsupported platform
        }

        String resourcePath = "/native/" + osDir + "/" + libName;
        try (InputStream in = TerminalNative.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                return false;
            }
            File tempFile = File.createTempFile("terminal-detect", libName.substring(libName.lastIndexOf('.')));
            tempFile.deleteOnExit();
            try (FileOutputStream out = new FileOutputStream(tempFile)) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
            }
            System.load(tempFile.getAbsolutePath());
            return true;
        } catch (IOException | UnsatisfiedLinkError e) {
            return false;
        }
    }

    private TerminalNative() {
    }
}
