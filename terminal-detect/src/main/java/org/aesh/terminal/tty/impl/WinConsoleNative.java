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

/**
 * Facade for Windows console API (Kernel32) access.
 * Replaces JNA for GraalVM native-image compatibility.
 * <p>
 * The public member set must stay identical to the Java 22 MRJAR overlay:
 * validation rejects versioned classes that change modifiers (including
 * {@code native}), so neither variant declares native methods here. The
 * JNI bindings live in package-private {@code WinConsoleNativeJni} (used
 * on runtimes without the overlay); the overlay implements the same
 * methods with FFM downcalls.
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
    public static long getStdHandle(int nStdHandle) {
        return WinConsoleNativeJni.getStdHandle(nStdHandle);
    }

    /**
     * Returns the current console mode for the given handle.
     *
     * @param handle the console handle
     * @return the console mode flags
     */
    public static int getConsoleMode(long handle) {
        return WinConsoleNativeJni.getConsoleMode(handle);
    }

    /**
     * Sets the console mode for the given handle.
     *
     * @param handle the console handle
     * @param mode the console mode flags
     * @return true if successful
     */
    public static boolean setConsoleMode(long handle, int mode) {
        return WinConsoleNativeJni.setConsoleMode(handle, mode);
    }

    /**
     * Returns the console output code page.
     *
     * @return the output code page identifier, or -1 on error/non-Windows
     */
    public static int getConsoleOutputCP() {
        return WinConsoleNativeJni.getConsoleOutputCP();
    }

    /**
     * Returns the console size as {columns, rows}.
     *
     * @param handle the console handle
     * @return array of {columns, rows}
     */
    public static int[] getConsoleSize(long handle) {
        return WinConsoleNativeJni.getConsoleSize(handle);
    }

    /** Event type constants matching Windows INPUT_RECORD.EventType. */
    public static final int KEY_EVENT = 1;
    /** Mouse event type. */
    public static final int MOUSE_EVENT = 2;
    /** Window buffer size event type. */
    public static final int WINDOW_BUFFER_SIZE_EVENT = 4;

    /** Console mode flag: enable virtual terminal processing on output handle. */
    public static final int ENABLE_VIRTUAL_TERMINAL_PROCESSING = 0x0004;

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
    public static int[] readConsoleInputEvent(long handle) {
        return WinConsoleNativeJni.readConsoleInputEvent(handle);
    }

    /**
     * Writes characters to the console.
     *
     * @param handle the console output handle
     * @param buffer the characters to write
     * @param length the number of characters to write (must be &lt;= buffer.length)
     * @return true if all characters were written successfully
     */
    public static boolean writeConsole(long handle, char[] buffer, int length) {
        return WinConsoleNativeJni.writeConsole(handle, buffer, length);
    }

    /** WaitForSingleObject return: the object was signaled. */
    public static final int WAIT_OBJECT_0 = 0x00000000;
    /** WaitForSingleObject return: the wait timed out. */
    public static final int WAIT_TIMEOUT = 0x00000102;
    /** WaitForSingleObject return: the function failed. */
    public static final int WAIT_FAILED = 0xFFFFFFFF;

    /**
     * Waits for the specified object to be signaled or the timeout to elapse.
     *
     * @param handle the object handle (e.g., console input handle)
     * @param timeoutMs timeout in milliseconds; -1 (0xFFFFFFFF) for infinite
     * @return {@link #WAIT_OBJECT_0} if signaled, {@link #WAIT_TIMEOUT} if timed out,
     *         or {@link #WAIT_FAILED} on error
     */
    public static int waitForSingleObject(long handle, int timeoutMs) {
        return WinConsoleNativeJni.waitForSingleObject(handle, timeoutMs);
    }

    /**
     * Returns the number of unread console input events.
     *
     * @param handle the console input handle
     * @return the number of pending events, or -1 on error
     */
    public static int getNumberOfConsoleInputEvents(long handle) {
        return WinConsoleNativeJni.getNumberOfConsoleInputEvents(handle);
    }

    /**
     * Allocates a new console for the calling process.
     * <p>
     * Test-only surface for live-console CI tests (#290): headless CI
     * runners have no console, so tests allocate one to drive
     * {@code WinSysTerminal} against a true handle. Fails (returns false)
     * when the process already has a console — use {@code freeConsole()}
     * only for consoles this method allocated.
     *
     * @return true if a console was allocated, false otherwise
     */
    public static boolean allocConsole() {
        return WinConsoleNativeJni.allocConsole();
    }

    /**
     * Detaches the calling process from its console.
     * <p>
     * Test-only companion to {@link #allocConsole()}: call only to release
     * a console allocated by {@code allocConsole()}. Detaching an
     * interactive console the process did not allocate discards its output.
     *
     * @return true if detached, false otherwise
     */
    public static boolean freeConsole() {
        return WinConsoleNativeJni.freeConsole();
    }

    /** Constructor. */
    private WinConsoleNative() {
    }
}
