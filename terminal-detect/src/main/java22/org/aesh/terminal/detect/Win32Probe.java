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

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * Minimal FFM bindings to the Win32 Console API (kernel32) for probe I/O.
 * <p>
 * Covers only what standalone probing needs: console handles and modes,
 * {@code WriteConsoleW} for query bytes, {@code WaitForSingleObject} plus
 * {@code ReadConsoleInputW} for responses with timeout. Full console
 * interaction (mouse, screen-buffer info, AllocConsole) stays in
 * terminal-tty's {@code WinConsoleNative}; struct offsets mirror it
 * exactly.
 * <p>
 * Handles resolve lazily on first use and stay null off-Windows (or
 * without native access), so availability checks never throw. Every
 * method fails gracefully ({@code false}, {@code -1}, or
 * {@code INVALID_HANDLE}) instead of throwing, except linkage failures
 * which surface as {@code UnsatisfiedLinkError}.
 * <p>
 * Requires Java 22+ and {@code --enable-native-access=ALL-UNNAMED}.
 */
final class Win32Probe {

    static final boolean IS_WINDOWS;
    static {
        IS_WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    static final int STD_INPUT_HANDLE = -10;
    static final int STD_OUTPUT_HANDLE = -11;
    static final long INVALID_HANDLE = -1L;

    static final int KEY_EVENT = 1;

    static final int ENABLE_WINDOW_INPUT = 0x0008;
    static final int ENABLE_VIRTUAL_TERMINAL_PROCESSING = 0x0004;

    static final int WAIT_OBJECT_0 = 0x00000000;
    static final int WAIT_TIMEOUT = 0x00000102;
    static final int WAIT_FAILED = 0xFFFFFFFF;

    // INPUT_RECORD layout (20 bytes, 4-byte aligned), KEY_EVENT union:
    static final long IR_SIZE = 20;
    static final long IR_EVENT_TYPE = 0;
    static final long IR_KEY_DOWN = 4;
    static final long IR_UNICODE_CHAR = 14;

    /** Max console input records consumed per read call. */
    static final int MAX_DRAIN_EVENTS = 64;

    /**
     * Downcall handles, resolved on first use. Lazy (not a static
     * initializer on this class) so availability checks stay cheap and
     * failure-free off-Windows.
     */
    private static final class Handles {
        static final MethodHandle GET_STD_HANDLE;
        static final MethodHandle GET_CONSOLE_MODE;
        static final MethodHandle SET_CONSOLE_MODE;
        static final MethodHandle WRITE_CONSOLE_W;
        static final MethodHandle READ_CONSOLE_INPUT_W;
        static final MethodHandle WAIT_FOR_SINGLE_OBJECT;
        static final MethodHandle GET_NUMBER_OF_CONSOLE_INPUT_EVENTS;

        static {
            MethodHandle getStdHandle = null;
            MethodHandle getConsoleMode = null;
            MethodHandle setConsoleMode = null;
            MethodHandle writeConsoleW = null;
            MethodHandle readConsoleInputW = null;
            MethodHandle waitForSingleObject = null;
            MethodHandle getNumberOfConsoleInputEvents = null;
            if (IS_WINDOWS) {
                try {
                    Linker linker = Linker.nativeLinker();
                    SymbolLookup kernel32 = SymbolLookup.libraryLookup("kernel32", Arena.global());
                    getStdHandle = lookup(linker, kernel32, "GetStdHandle",
                            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
                    getConsoleMode = lookup(linker, kernel32, "GetConsoleMode",
                            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                                    ValueLayout.ADDRESS, ValueLayout.ADDRESS));
                    setConsoleMode = lookup(linker, kernel32, "SetConsoleMode",
                            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
                    writeConsoleW = lookup(linker, kernel32, "WriteConsoleW",
                            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                                    ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                                    ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
                                    ValueLayout.ADDRESS));
                    readConsoleInputW = lookup(linker, kernel32, "ReadConsoleInputW",
                            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                                    ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                                    ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
                    waitForSingleObject = lookup(linker, kernel32, "WaitForSingleObject",
                            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
                    getNumberOfConsoleInputEvents = lookup(linker, kernel32,
                            "GetNumberOfConsoleInputEvents",
                            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                                    ValueLayout.ADDRESS, ValueLayout.ADDRESS));
                } catch (Throwable ignored) {
                    // Missing native access or unexpected lookup failure:
                    // entries stay null and methods fail gracefully.
                }
            }
            GET_STD_HANDLE = getStdHandle;
            GET_CONSOLE_MODE = getConsoleMode;
            SET_CONSOLE_MODE = setConsoleMode;
            WRITE_CONSOLE_W = writeConsoleW;
            READ_CONSOLE_INPUT_W = readConsoleInputW;
            WAIT_FOR_SINGLE_OBJECT = waitForSingleObject;
            GET_NUMBER_OF_CONSOLE_INPUT_EVENTS = getNumberOfConsoleInputEvents;
        }

        private static MethodHandle lookup(Linker linker, SymbolLookup kernel32,
                String name, FunctionDescriptor descriptor) {
            MemorySegment address = kernel32.find(name).orElse(null);
            if (address == null) {
                throw new UnsatisfiedLinkError("kernel32 function not found: " + name);
            }
            return linker.downcallHandle(address, descriptor);
        }
    }

    static boolean isNativeAccessEnabled() {
        return Win32Probe.class.getModule().isNativeAccessEnabled();
    }

    static long getStdHandle(int nStdHandle) {
        if (Handles.GET_STD_HANDLE == null) {
            return INVALID_HANDLE;
        }
        try {
            MemorySegment handle = (MemorySegment) Handles.GET_STD_HANDLE.invokeExact(nStdHandle);
            long addr = handle.address();
            return (addr == 0L || addr == -1L) ? INVALID_HANDLE : addr;
        } catch (Throwable t) {
            return INVALID_HANDLE;
        }
    }

    /**
     * Reads the console mode for a handle.
     *
     * @param handle the console handle (address)
     * @return the mode flags, or -1 when unavailable
     */
    static int getConsoleMode(long handle) {
        if (Handles.GET_CONSOLE_MODE == null) {
            return -1;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment modePtr = arena.allocate(ValueLayout.JAVA_INT);
            int ok = (int) Handles.GET_CONSOLE_MODE.invokeExact(
                    MemorySegment.ofAddress(handle), modePtr);
            return ok != 0 ? modePtr.get(ValueLayout.JAVA_INT, 0) : -1;
        } catch (Throwable t) {
            return -1;
        }
    }

    static boolean setConsoleMode(long handle, int mode) {
        if (Handles.SET_CONSOLE_MODE == null) {
            return false;
        }
        try {
            int ok = (int) Handles.SET_CONSOLE_MODE.invokeExact(
                    MemorySegment.ofAddress(handle), mode);
            return ok != 0;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Writes UTF-16 characters to the console output handle.
     *
     * @param handle the console output handle (address)
     * @param chars the characters to write
     * @return the number of characters written, or -1 on failure
     */
    static int writeConsole(long handle, char[] chars) {
        if (Handles.WRITE_CONSOLE_W == null || chars.length == 0) {
            return chars.length == 0 ? 0 : -1;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment nativeBuf = arena.allocate(ValueLayout.JAVA_CHAR, chars.length);
            MemorySegment.copy(chars, 0, nativeBuf, ValueLayout.JAVA_CHAR, 0, chars.length);
            MemorySegment written = arena.allocate(ValueLayout.JAVA_INT);
            int ok = (int) Handles.WRITE_CONSOLE_W.invokeExact(
                    MemorySegment.ofAddress(handle), nativeBuf, chars.length,
                    written, MemorySegment.NULL);
            return ok != 0 ? written.get(ValueLayout.JAVA_INT, 0) : -1;
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * Waits for console input to become available.
     *
     * @param handle the console input handle (address)
     * @param timeoutMs timeout in milliseconds
     * @return {@code WAIT_OBJECT_0} when input is pending,
     *         {@code WAIT_TIMEOUT} on timeout, {@code WAIT_FAILED} on error
     */
    static int waitForInput(long handle, int timeoutMs) {
        if (Handles.WAIT_FOR_SINGLE_OBJECT == null) {
            return WAIT_FAILED;
        }
        try {
            return (int) Handles.WAIT_FOR_SINGLE_OBJECT.invokeExact(
                    MemorySegment.ofAddress(handle), timeoutMs);
        } catch (Throwable t) {
            return WAIT_FAILED;
        }
    }

    static int pendingInputEvents(long handle) {
        if (Handles.GET_NUMBER_OF_CONSOLE_INPUT_EVENTS == null) {
            return -1;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment numEvents = arena.allocate(ValueLayout.JAVA_INT);
            int ok = (int) Handles.GET_NUMBER_OF_CONSOLE_INPUT_EVENTS.invokeExact(
                    MemorySegment.ofAddress(handle), numEvents);
            return ok != 0 ? numEvents.get(ValueLayout.JAVA_INT, 0) : -1;
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * Reads one console input record and appends its character (if any) to
     * the sink. Only key-down {@code KEY_EVENT} records with a non-zero
     * character contribute bytes — the same filter terminal responses rely
     * on in the interactive pump. Key-up events, virtual-key-only presses,
     * mouse and window-size events are consumed and ignored.
     *
     * @param handle the console input handle (address)
     * @param sink collects response characters
     * @return true when a record was consumed, false on read failure
     */
    static boolean readRecordChars(long handle, StringBuilder sink) {
        if (Handles.READ_CONSOLE_INPUT_W == null) {
            return false;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment record = arena.allocate(IR_SIZE, 4);
            MemorySegment eventsRead = arena.allocate(ValueLayout.JAVA_INT);
            int ok = (int) Handles.READ_CONSOLE_INPUT_W.invokeExact(
                    MemorySegment.ofAddress(handle), record, 1, eventsRead);
            if (ok == 0 || eventsRead.get(ValueLayout.JAVA_INT, 0) == 0) {
                return false;
            }
            short eventType = record.get(ValueLayout.JAVA_SHORT, IR_EVENT_TYPE);
            if (eventType == KEY_EVENT
                    && record.get(ValueLayout.JAVA_INT, IR_KEY_DOWN) != 0) {
                char c = record.get(ValueLayout.JAVA_CHAR, IR_UNICODE_CHAR);
                if (c != 0) {
                    sink.append(c);
                }
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private Win32Probe() {
    }
}
