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

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * Minimal FFM bindings to POSIX terminal syscalls for probe I/O.
 * <p>
 * Covers only what standalone probing needs: {@code open}, {@code close},
 * {@code read}, {@code write}, {@code tcgetattr}, {@code tcsetattr}.
 * Full PTY implementations (window size via {@code ioctl}, signals) stay
 * in terminal-tty; a future consolidation may move these shared handles
 * down so terminal-tty reuses them (issue #297).
 * <p>
 * Downcall handles live behind lazy initialization so merely loading this
 * class (e.g. an availability check) never pays the {@code Linker}
 * initialization cost and never fails without native access.
 * <p>
 * Requires Java 22+ and {@code --enable-native-access=ALL-UNNAMED}.
 */
final class FfmPosix {

    static final boolean IS_MACOS;
    static {
        String osName = System.getProperty("os.name", "").toLowerCase();
        IS_MACOS = osName.startsWith("mac") || osName.contains("darwin");
    }

    static final boolean IS_WINDOWS;
    static {
        String osName = System.getProperty("os.name", "").toLowerCase();
        IS_WINDOWS = osName.contains("win");
    }

    static final int O_RDWR = 0x0002;

    static final int TCSANOW = 0;
    static final int TCSAFLUSH = 2;

    // termios struct sizes and flag offsets (Linux: 4-byte flags, macOS: 8-byte).
    static final long TERMIOS_SIZE = IS_MACOS ? 72 : 60;
    static final long TERMIOS_IFLAG_OFFSET = 0;
    static final long TERMIOS_LFLAG_OFFSET = IS_MACOS ? 24 : 12;
    static final long TERMIOS_CC_OFFSET = IS_MACOS ? 32 : 17;

    // Raw-query mode clears ECHO and ICANON (lflag) and IXON (iflag),
    // mirroring the stty path ("-echo -icanon -ixon").
    static final long LFLAG_ECHO = 0x00000008L;
    static final long LFLAG_ICANON = IS_MACOS ? 0x00000100L : 0x00000002L;
    static final long IFLAG_IXON = IS_MACOS ? 0x00000200L : 0x00000400L;

    // stty "min 0 time 5": return immediately with available bytes,
    // wait up to 0.5s for the first byte.
    static final int CC_VMIN_INDEX = IS_MACOS ? 16 : 6;
    static final int CC_VTIME_INDEX = IS_MACOS ? 17 : 5;
    static final byte CC_VMIN_VALUE = 0;
    static final byte CC_VTIME_VALUE = 5;

    /**
     * Downcall handles, created on first use. A holder class (instead of a
     * static initializer) keeps class loading cheap and failure-free:
     * availability checks must not trigger {@code Linker} initialization
     * or throw without native access.
     */
    private static final class Handles {
        static final Linker LINKER = Linker.nativeLinker();
        static final SymbolLookup STDLIB = LINKER.defaultLookup();

        static final MethodHandle OPEN = LINKER.downcallHandle(
                STDLIB.find("open").orElseThrow(),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT));

        static final MethodHandle CLOSE = LINKER.downcallHandle(
                STDLIB.find("close").orElseThrow(),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT));

        static final MethodHandle READ = LINKER.downcallHandle(
                STDLIB.find("read").orElseThrow(),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_LONG,
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_LONG));

        static final MethodHandle WRITE = LINKER.downcallHandle(
                STDLIB.find("write").orElseThrow(),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_LONG,
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_LONG));

        static final MethodHandle TCGETATTR = LINKER.downcallHandle(
                STDLIB.find("tcgetattr").orElseThrow(),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS));

        static final MethodHandle TCSETATTR = LINKER.downcallHandle(
                STDLIB.find("tcsetattr").orElseThrow(),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS));
    }

    static boolean isNativeAccessEnabled() {
        return FfmPosix.class.getModule().isNativeAccessEnabled();
    }

    static int open(String pathname, int flags, Arena arena) throws IOException {
        try {
            MemorySegment path = arena.allocateFrom(pathname);
            return (int) Handles.OPEN.invokeExact(path, flags);
        } catch (IOException e) {
            throw e;
        } catch (Throwable t) {
            throw new IOException("open() failed for " + pathname, t);
        }
    }

    static void close(int fd) throws IOException {
        try {
            int rc = (int) Handles.CLOSE.invokeExact(fd);
            if (rc != 0) {
                throw new IOException("close() returned " + rc);
            }
        } catch (IOException e) {
            throw e;
        } catch (Throwable t) {
            throw new IOException("close() failed", t);
        }
    }

    static long read(int fd, MemorySegment buf, long count) throws IOException {
        try {
            return (long) Handles.READ.invokeExact(fd, buf, count);
        } catch (IOException e) {
            throw e;
        } catch (Throwable t) {
            throw new IOException("read() failed", t);
        }
    }

    static long write(int fd, MemorySegment buf, long count) throws IOException {
        try {
            return (long) Handles.WRITE.invokeExact(fd, buf, count);
        } catch (IOException e) {
            throw e;
        } catch (Throwable t) {
            throw new IOException("write() failed", t);
        }
    }

    static void tcgetattr(int fd, MemorySegment termios) throws IOException {
        try {
            int rc = (int) Handles.TCGETATTR.invokeExact(fd, termios);
            if (rc != 0) {
                throw new IOException("tcgetattr() returned " + rc);
            }
        } catch (IOException e) {
            throw e;
        } catch (Throwable t) {
            throw new IOException("tcgetattr() failed", t);
        }
    }

    static void tcsetattr(int fd, int action, MemorySegment termios) throws IOException {
        try {
            int rc = (int) Handles.TCSETATTR.invokeExact(fd, action, termios);
            if (rc != 0) {
                throw new IOException("tcsetattr() returned " + rc);
            }
        } catch (IOException e) {
            throw e;
        } catch (Throwable t) {
            throw new IOException("tcsetattr() failed", t);
        }
    }

    static long getFlag(MemorySegment termios, long offset) {
        if (IS_MACOS) {
            return termios.get(ValueLayout.JAVA_LONG, offset);
        }
        return Integer.toUnsignedLong(termios.get(ValueLayout.JAVA_INT, offset));
    }

    static void setFlag(MemorySegment termios, long offset, long value) {
        if (IS_MACOS) {
            termios.set(ValueLayout.JAVA_LONG, offset, value);
        } else {
            termios.set(ValueLayout.JAVA_INT, offset, (int) value);
        }
    }

    /**
     * Switch a termios struct (already populated by {@code tcgetattr}) to
     * probe query mode: no echo, no canonical mode, no XON/XOFF flow
     * control, non-blocking reads with a 0.5s timeout. Mirrors the stty
     * path ({@code stty -echo -icanon -ixon min 0 time 5}).
     *
     * @param termios the termios struct to modify in place
     */
    static void setRawQueryMode(MemorySegment termios) {
        long lflag = getFlag(termios, TERMIOS_LFLAG_OFFSET);
        setFlag(termios, TERMIOS_LFLAG_OFFSET, lflag & ~(LFLAG_ECHO | LFLAG_ICANON));
        long iflag = getFlag(termios, TERMIOS_IFLAG_OFFSET);
        setFlag(termios, TERMIOS_IFLAG_OFFSET, iflag & ~IFLAG_IXON);
        termios.set(ValueLayout.JAVA_BYTE, TERMIOS_CC_OFFSET + CC_VMIN_INDEX, CC_VMIN_VALUE);
        termios.set(ValueLayout.JAVA_BYTE, TERMIOS_CC_OFFSET + CC_VTIME_INDEX, CC_VTIME_VALUE);
    }

    private FfmPosix() {
    }
}
