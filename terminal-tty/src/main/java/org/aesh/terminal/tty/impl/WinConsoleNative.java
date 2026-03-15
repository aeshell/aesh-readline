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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * JNI bridge to Windows console API (Kernel32).
 * Replaces JNA for GraalVM native-image compatibility.
 */
final class WinConsoleNative {

    static final int STD_INPUT_HANDLE = -10;
    static final int STD_OUTPUT_HANDLE = -11;
    static final long INVALID_HANDLE = -1L;

    static native long getStdHandle(int nStdHandle);

    static native int getConsoleMode(long handle);

    static native boolean setConsoleMode(long handle, int mode);

    static native int getConsoleOutputCP();

    static native int[] getConsoleSize(long handle);

    static native int[] readConsoleKeyEvent(long handle);

    /** Event type constants matching Windows INPUT_RECORD.EventType */
    static final int KEY_EVENT = 1;
    static final int WINDOW_BUFFER_SIZE_EVENT = 4;

    /**
     * Read a console input event (key or window resize).
     * Returns int[] where first element is the event type:
     * KEY_EVENT (1): {1, keyDown, repeatCount, vKeyCode, unicodeChar, controlKeyState}
     * WINDOW_BUFFER_SIZE_EVENT (4): {4, width, height}
     * Returns null for other event types or on error.
     */
    static native int[] readConsoleInputEvent(long handle);

    static native boolean writeConsole(long handle, char[] buffer, int length);

    static {
        loadLibrary();
    }

    private static void loadLibrary() {
        try {
            System.loadLibrary("aesh-console");
            return;
        } catch (UnsatisfiedLinkError ignore) {
            // Not on system path, try extracting from JAR
        }

        String arch = System.getProperty("os.arch");
        if ("amd64".equals(arch) || "x86_64".equals(arch)) {
            arch = "x86_64";
        }
        String resourcePath = "/native/windows-" + arch + "/aesh-console.dll";
        try (InputStream in = WinConsoleNative.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new UnsatisfiedLinkError("Native library not found in JAR: " + resourcePath);
            }
            File tempFile = File.createTempFile("aesh-console", ".dll");
            tempFile.deleteOnExit();
            try (FileOutputStream out = new FileOutputStream(tempFile)) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
            }
            System.load(tempFile.getAbsolutePath());
        } catch (IOException e) {
            throw new UnsatisfiedLinkError("Failed to extract native library: " + e.getMessage());
        }
    }

    private WinConsoleNative() {
    }
}
