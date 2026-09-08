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

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * Pure-Java bridge to Windows console API (Kernel32) using the
 * Foreign Function &amp; Memory API (JEP 454, Java 22+).
 * <p>
 * This is the multi-release JAR variant that replaces the JNI/DLL
 * implementation on Java 22+. No native compilation or shipped DLL
 * required, and compatible with GraalVM native-image (25+).
 * <p>
 * Requires {@code --enable-native-access=ALL-UNNAMED} at runtime.
 */
public final class WinConsoleNative {

    public static final int STD_INPUT_HANDLE = -10;
    public static final int STD_OUTPUT_HANDLE = -11;
    public static final int STD_ERROR_HANDLE = -12;
    public static final long INVALID_HANDLE = -1L;

    /** Event type constants matching Windows INPUT_RECORD.EventType */
    public static final int KEY_EVENT = 1;
    public static final int MOUSE_EVENT = 2;
    public static final int WINDOW_BUFFER_SIZE_EVENT = 4;

    /** Console mode flag: enable virtual terminal processing on output handle */
    public static final int ENABLE_VIRTUAL_TERMINAL_PROCESSING = 0x0004;

    // CONSOLE_SCREEN_BUFFER_INFO struct layout (22 bytes, 2-byte aligned):
    //   0: dwSize.X            2: dwSize.Y
    //   4: dwCursorPosition.X  6: dwCursorPosition.Y
    //   8: wAttributes
    //  10: srWindow.Left      12: srWindow.Top
    //  14: srWindow.Right     16: srWindow.Bottom
    //  18: dwMaxWindowSize.X  20: dwMaxWindowSize.Y
    private static final long CSBI_SIZE = 22;
    private static final long CSBI_SR_WINDOW_LEFT = 10;
    private static final long CSBI_SR_WINDOW_TOP = 12;
    private static final long CSBI_SR_WINDOW_RIGHT = 14;
    private static final long CSBI_SR_WINDOW_BOTTOM = 16;

    // INPUT_RECORD struct layout (20 bytes, 4-byte aligned):
    //   0: EventType (WORD) + 2 bytes padding
    // KEY_EVENT union (offset 4):
    //   4: bKeyDown (INT)      8: wRepeatCount (SHORT)
    //  10: wVirtualKeyCode    12: wVirtualScanCode
    //  14: UnicodeChar        16: dwControlKeyState (INT)
    // WINDOW_BUFFER_SIZE_EVENT union (offset 4):
    //   4: dwSize.X (SHORT)    6: dwSize.Y (SHORT)
    private static final long IR_SIZE = 20;
    private static final long IR_EVENT_TYPE = 0;
    private static final long IR_KEY_DOWN = 4;
    private static final long IR_REPEAT_COUNT = 8;
    private static final long IR_VIRTUAL_KEY_CODE = 10;
    private static final long IR_UNICODE_CHAR = 14;
    private static final long IR_CONTROL_KEY_STATE = 16;
    private static final long IR_WBSE_X = 4;
    private static final long IR_WBSE_Y = 6;
    // MOUSE_EVENT_RECORD union (offset 4):
    //   4: dwMousePosition.X (SHORT)  6: dwMousePosition.Y (SHORT)
    //   8: dwButtonState (DWORD)     12: dwControlKeyState (DWORD)
    //  16: dwEventFlags (DWORD)
    private static final long IR_MOUSE_X = 4;
    private static final long IR_MOUSE_Y = 6;
    private static final long IR_MOUSE_BUTTON_STATE = 8;
    private static final long IR_MOUSE_CONTROL_KEY_STATE = 12;
    private static final long IR_MOUSE_EVENT_FLAGS = 16;

    /** WaitForSingleObject return: the object was signaled. */
    public static final int WAIT_OBJECT_0 = 0x00000000;
    /** WaitForSingleObject return: the wait timed out. */
    public static final int WAIT_TIMEOUT = 0x00000102;
    /** WaitForSingleObject return: the function failed. */
    public static final int WAIT_FAILED = 0xFFFFFFFF;

    private static final MethodHandle GET_STD_HANDLE;
    private static final MethodHandle GET_CONSOLE_MODE;
    private static final MethodHandle SET_CONSOLE_MODE;
    private static final MethodHandle GET_CONSOLE_OUTPUT_CP;
    private static final MethodHandle GET_CONSOLE_SCREEN_BUFFER_INFO;
    private static final MethodHandle READ_CONSOLE_INPUT_W;
    private static final MethodHandle WRITE_CONSOLE_W;
    private static final MethodHandle WAIT_FOR_SINGLE_OBJECT;
    private static final MethodHandle GET_NUMBER_OF_CONSOLE_INPUT_EVENTS;

    static {
        MethodHandle[] handles = initHandles();
        GET_STD_HANDLE = handles[0];
        GET_CONSOLE_MODE = handles[1];
        SET_CONSOLE_MODE = handles[2];
        GET_CONSOLE_OUTPUT_CP = handles[3];
        GET_CONSOLE_SCREEN_BUFFER_INFO = handles[4];
        READ_CONSOLE_INPUT_W = handles[5];
        WRITE_CONSOLE_W = handles[6];
        WAIT_FOR_SINGLE_OBJECT = handles[7];
        GET_NUMBER_OF_CONSOLE_INPUT_EVENTS = handles[8];
    }

    /**
     * Initialize all kernel32 downcall handles. Returns an array of 9 handles,
     * all null on non-Windows or if FFM init fails (Wine, missing native access,
     * minimal containers). Never throws — callers check for null at invocation.
     */
    private static MethodHandle[] initHandles() {
        MethodHandle[] h = new MethodHandle[9];
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            return h;
        }
        try {
            Linker linker = Linker.nativeLinker();
            SymbolLookup kernel32 = SymbolLookup.libraryLookup("kernel32", Arena.global());

            h[0] = lookup(linker, kernel32, "GetStdHandle",
                    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            h[1] = lookup(linker, kernel32, "GetConsoleMode",
                    FunctionDescriptor.of(ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            h[2] = lookup(linker, kernel32, "SetConsoleMode",
                    FunctionDescriptor.of(ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            h[3] = lookup(linker, kernel32, "GetConsoleOutputCP",
                    FunctionDescriptor.of(ValueLayout.JAVA_INT));
            h[4] = lookup(linker, kernel32, "GetConsoleScreenBufferInfo",
                    FunctionDescriptor.of(ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            h[5] = lookup(linker, kernel32, "ReadConsoleInputW",
                    FunctionDescriptor.of(ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                            ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            h[6] = lookup(linker, kernel32, "WriteConsoleW",
                    FunctionDescriptor.of(ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                            ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
                            ValueLayout.ADDRESS));
            h[7] = lookup(linker, kernel32, "WaitForSingleObject",
                    FunctionDescriptor.of(ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            h[8] = lookup(linker, kernel32, "GetNumberOfConsoleInputEvents",
                    FunctionDescriptor.of(ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        } catch (Throwable t) {
            // Wine, minimal containers, or missing native access — return
            // partially or fully null array. Methods check for null and
            // throw UnsatisfiedLinkError with a descriptive message.
            return h;
        }
        return h;
    }

    /**
     * Look up a kernel32 function and create a downcall handle.
     * Throws if the symbol is not found.
     */
    private static MethodHandle lookup(Linker linker, SymbolLookup kernel32,
            String name, FunctionDescriptor descriptor) {
        return linker.downcallHandle(
                kernel32.find(name).orElseThrow(
                        () -> new UnsatisfiedLinkError("kernel32 function not found: " + name)),
                descriptor);
    }

    public static long getStdHandle(int nStdHandle) {
        if (GET_STD_HANDLE == null) {
            return INVALID_HANDLE;
        }
        try {
            MemorySegment handle = (MemorySegment) GET_STD_HANDLE.invokeExact(nStdHandle);
            long addr = handle.address();
            return (addr == 0L || addr == -1L) ? INVALID_HANDLE : addr;
        } catch (Throwable t) {
            throw new RuntimeException("GetStdHandle failed", t);
        }
    }

    public static int getConsoleMode(long handle) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment modePtr = arena.allocate(ValueLayout.JAVA_INT);
            int ok = (int) GET_CONSOLE_MODE.invokeExact(
                    MemorySegment.ofAddress(handle), modePtr);
            return ok != 0 ? modePtr.get(ValueLayout.JAVA_INT, 0) : -1;
        } catch (Throwable t) {
            throw new RuntimeException("GetConsoleMode failed", t);
        }
    }

    public static boolean setConsoleMode(long handle, int mode) {
        try {
            int ok = (int) SET_CONSOLE_MODE.invokeExact(
                    MemorySegment.ofAddress(handle), mode);
            return ok != 0;
        } catch (Throwable t) {
            throw new RuntimeException("SetConsoleMode failed", t);
        }
    }

    public static int getConsoleOutputCP() {
        if (GET_CONSOLE_OUTPUT_CP == null) {
            return -1;
        }
        try {
            return (int) GET_CONSOLE_OUTPUT_CP.invokeExact();
        } catch (Throwable t) {
            throw new RuntimeException("GetConsoleOutputCP failed", t);
        }
    }

    public static int[] getConsoleSize(long handle) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment info = arena.allocate(CSBI_SIZE, 2);
            int ok = (int) GET_CONSOLE_SCREEN_BUFFER_INFO.invokeExact(
                    MemorySegment.ofAddress(handle), info);
            if (ok == 0) {
                return null;
            }
            short left = info.get(ValueLayout.JAVA_SHORT, CSBI_SR_WINDOW_LEFT);
            short top = info.get(ValueLayout.JAVA_SHORT, CSBI_SR_WINDOW_TOP);
            short right = info.get(ValueLayout.JAVA_SHORT, CSBI_SR_WINDOW_RIGHT);
            short bottom = info.get(ValueLayout.JAVA_SHORT, CSBI_SR_WINDOW_BOTTOM);
            return new int[] { right - left + 1, bottom - top + 1 };
        } catch (Throwable t) {
            throw new RuntimeException("GetConsoleScreenBufferInfo failed", t);
        }
    }

    public static int[] readConsoleInputEvent(long handle) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment record = arena.allocate(IR_SIZE, 4);
            MemorySegment eventsRead = arena.allocate(ValueLayout.JAVA_INT);
            int ok = (int) READ_CONSOLE_INPUT_W.invokeExact(
                    MemorySegment.ofAddress(handle), record, 1, eventsRead);
            if (ok == 0 || eventsRead.get(ValueLayout.JAVA_INT, 0) == 0) {
                return null;
            }
            short eventType = record.get(ValueLayout.JAVA_SHORT, IR_EVENT_TYPE);
            if (eventType == KEY_EVENT) {
                return new int[] {
                        KEY_EVENT,
                        record.get(ValueLayout.JAVA_INT, IR_KEY_DOWN) != 0 ? 1 : 0,
                        record.get(ValueLayout.JAVA_SHORT, IR_REPEAT_COUNT) & 0xFFFF,
                        record.get(ValueLayout.JAVA_SHORT, IR_VIRTUAL_KEY_CODE) & 0xFFFF,
                        record.get(ValueLayout.JAVA_SHORT, IR_UNICODE_CHAR) & 0xFFFF,
                        record.get(ValueLayout.JAVA_INT, IR_CONTROL_KEY_STATE)
                };
            }
            if (eventType == MOUSE_EVENT) {
                return new int[] {
                        MOUSE_EVENT,
                        record.get(ValueLayout.JAVA_SHORT, IR_MOUSE_X) & 0xFFFF,
                        record.get(ValueLayout.JAVA_SHORT, IR_MOUSE_Y) & 0xFFFF,
                        record.get(ValueLayout.JAVA_INT, IR_MOUSE_BUTTON_STATE),
                        record.get(ValueLayout.JAVA_INT, IR_MOUSE_CONTROL_KEY_STATE),
                        record.get(ValueLayout.JAVA_INT, IR_MOUSE_EVENT_FLAGS)
                };
            }
            if (eventType == WINDOW_BUFFER_SIZE_EVENT) {
                return new int[] {
                        WINDOW_BUFFER_SIZE_EVENT,
                        record.get(ValueLayout.JAVA_SHORT, IR_WBSE_X) & 0xFFFF,
                        record.get(ValueLayout.JAVA_SHORT, IR_WBSE_Y) & 0xFFFF
                };
            }
            return null;
        } catch (Throwable t) {
            throw new RuntimeException("ReadConsoleInputW failed", t);
        }
    }

    public static boolean writeConsole(long handle, char[] buffer, int length) {
        if (buffer == null || length < 0 || length > buffer.length) {
            return false;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment nativeBuf = arena.allocate(ValueLayout.JAVA_CHAR, length);
            MemorySegment.copy(buffer, 0, nativeBuf, ValueLayout.JAVA_CHAR, 0, length);
            MemorySegment written = arena.allocate(ValueLayout.JAVA_INT);
            int ok = (int) WRITE_CONSOLE_W.invokeExact(
                    MemorySegment.ofAddress(handle), nativeBuf, length,
                    written, MemorySegment.NULL);
            return ok != 0 && written.get(ValueLayout.JAVA_INT, 0) == length;
        } catch (Throwable t) {
            throw new RuntimeException("WriteConsoleW failed", t);
        }
    }

    /**
     * Waits for the specified object to be signaled or the timeout to elapse.
     *
     * @param handle the object handle (e.g., console input handle)
     * @param timeoutMs timeout in milliseconds; -1 (0xFFFFFFFF) for infinite
     * @return {@link #WAIT_OBJECT_0} if signaled, {@link #WAIT_TIMEOUT} if timed out,
     *         or {@link #WAIT_FAILED} on error
     */
    public static int waitForSingleObject(long handle, int timeoutMs) {
        try {
            return (int) WAIT_FOR_SINGLE_OBJECT.invokeExact(
                    MemorySegment.ofAddress(handle), timeoutMs);
        } catch (Throwable t) {
            throw new RuntimeException("WaitForSingleObject failed", t);
        }
    }

    /**
     * Returns the number of unread console input events.
     *
     * @param handle the console input handle
     * @return the number of pending events, or -1 on error
     */
    public static int getNumberOfConsoleInputEvents(long handle) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment numEvents = arena.allocate(ValueLayout.JAVA_INT);
            int ok = (int) GET_NUMBER_OF_CONSOLE_INPUT_EVENTS.invokeExact(
                    MemorySegment.ofAddress(handle), numEvents);
            if (ok == 0) return -1;
            return numEvents.get(ValueLayout.JAVA_INT, 0);
        } catch (Throwable t) {
            throw new RuntimeException("GetNumberOfConsoleInputEvents failed", t);
        }
    }

    /**
     * Whether this implementation supports non-blocking wait with timeout.
     *
     * @return true — FFM variant has WaitForSingleObject
     */
    public static boolean supportsNonBlockingWait() {
        return true;
    }

    private WinConsoleNative() {
    }
}
