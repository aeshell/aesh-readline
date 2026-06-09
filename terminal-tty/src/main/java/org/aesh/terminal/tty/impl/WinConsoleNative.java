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
 * <p>
 * This class provides low-level access to Windows console functions
 * via JNI. The native library ({@code aesh-console.dll}) is loaded
 * from the system library path or extracted from the JAR at runtime.
 */
public final class WinConsoleNative {

    /** Standard input handle identifier. */
    public static final int STD_INPUT_HANDLE = -10;
    /** Standard output handle identifier. */
    public static final int STD_OUTPUT_HANDLE = -11;
    /** Standard error handle identifier. */
    public static final int STD_ERROR_HANDLE = -12;
    /** Invalid handle sentinel value. */
    public static final long INVALID_HANDLE = -1L;

    /**
     * Returns the handle for the specified standard device.
     *
     * @param nStdHandle the standard device identifier
     * @return the device handle
     */
    public static native long getStdHandle(int nStdHandle);

    /**
     * Returns the current console mode for the given handle.
     *
     * @param handle the console handle
     * @return the console mode flags
     */
    public static native int getConsoleMode(long handle);

    /**
     * Sets the console mode for the given handle.
     *
     * @param handle the console handle
     * @param mode the console mode flags
     * @return true if successful
     */
    public static native boolean setConsoleMode(long handle, int mode);

    /**
     * Returns the console output code page.
     *
     * @return the output code page identifier
     */
    public static native int getConsoleOutputCP();

    /**
     * Returns the console size as {columns, rows}.
     *
     * @param handle the console handle
     * @return array of {columns, rows}
     */
    public static native int[] getConsoleSize(long handle);

    /** Event type constants matching Windows INPUT_RECORD.EventType. */
    public static final int KEY_EVENT = 1;
    /** Mouse event type. */
    public static final int MOUSE_EVENT = 2;
    /** Window buffer size event type. */
    public static final int WINDOW_BUFFER_SIZE_EVENT = 4;

    /** Console mode flag: enable virtual terminal processing on output handle. */
    public static final int ENABLE_VIRTUAL_TERMINAL_PROCESSING = 0x0004;
    /** Console mode flag: enable virtual terminal input on input handle. */
    public static final int ENABLE_VIRTUAL_TERMINAL_INPUT = 0x0200;

    /**
     * Read a console input event (key or window resize).
     * Returns int[] where first element is the event type:
     * KEY_EVENT (1): {1, keyDown, repeatCount, vKeyCode, unicodeChar, controlKeyState}
     * MOUSE_EVENT (2): {2, x, y, buttonState, controlKeyState, eventFlags}
     * WINDOW_BUFFER_SIZE_EVENT (4): {4, width, height}
     * Returns null for other event types or on error.
     *
     * @param handle the console input handle
     * @return the event data array, or null
     */
    public static native int[] readConsoleInputEvent(long handle);

    /**
     * Writes characters to the console.
     *
     * @param handle the console output handle
     * @param buffer the characters to write
     * @param length the number of characters to write
     * @return true if successful
     */
    public static native boolean writeConsole(long handle, char[] buffer, int length);

    /** WaitForSingleObject return: the object was signaled. */
    public static final int WAIT_OBJECT_0 = 0x00000000;
    /** WaitForSingleObject return: the wait timed out. */
    public static final int WAIT_TIMEOUT = 0x00000102;
    /** WaitForSingleObject return: the function failed. */
    public static final int WAIT_FAILED = 0xFFFFFFFF;

    /**
     * Waits for the specified object to be signaled or the timeout to elapse.
     * <p>
     * JNI fallback: always returns {@link #WAIT_OBJECT_0} (no timeout support).
     * The FFM multi-release variant calls the real kernel32 function.
     *
     * @param handle the object handle
     * @param timeoutMs timeout in milliseconds
     * @return {@link #WAIT_OBJECT_0}, {@link #WAIT_TIMEOUT}, or {@link #WAIT_FAILED}
     */
    public static int waitForSingleObject(long handle, int timeoutMs) {
        // JNI path: no WaitForSingleObject binding, always report ready
        return WAIT_OBJECT_0;
    }

    /**
     * Returns the number of unread console input events.
     * <p>
     * JNI fallback: always returns {@code -1} (unsupported).
     * The FFM multi-release variant calls the real kernel32 function.
     *
     * @param handle the console input handle
     * @return the number of pending events, or -1 if unsupported
     */
    public static int getNumberOfConsoleInputEvents(long handle) {
        // JNI path: not supported
        return -1;
    }

    /**
     * Whether this implementation supports non-blocking wait with timeout.
     * <p>
     * JNI fallback: returns {@code false}.
     * The FFM multi-release variant returns {@code true}.
     *
     * @return true if WaitForSingleObject is available
     */
    public static boolean supportsNonBlockingWait() {
        return false;
    }

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

    /** Constructor. */
    private WinConsoleNative() {
    }
}
