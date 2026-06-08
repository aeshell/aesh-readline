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
 * FFM bindings to POSIX C library functions for terminal access.
 * <p>
 * Provides thin wrappers around {@code tcgetattr}, {@code tcsetattr},
 * {@code ioctl}, {@code open}, {@code close}, {@code read}, and {@code poll}.
 * <p>
 * All functions use captured errno for error reporting.
 * Requires {@code --enable-native-access=ALL-UNNAMED} at runtime.
 */
final class LibC {

    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup STDLIB = LINKER.defaultLookup();

    private static final Linker.Option ERRNO_STATE =
            Linker.Option.captureCallState("errno");

    private static final MethodHandle ISATTY;
    private static final MethodHandle OPEN;
    private static final MethodHandle CLOSE;
    private static final MethodHandle READ;
    private static final MethodHandle POLL;
    private static final MethodHandle TCGETATTR;
    private static final MethodHandle TCSETATTR;
    private static final MethodHandle IOCTL;

    static {
        // int isatty(int fd)
        ISATTY = LINKER.downcallHandle(
                STDLIB.find("isatty").orElseThrow(),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,       // return
                        ValueLayout.JAVA_INT));     // fd

        // int open(const char *pathname, int flags)
        OPEN = LINKER.downcallHandle(
                STDLIB.find("open").orElseThrow(),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,       // return
                        ValueLayout.ADDRESS,        // pathname
                        ValueLayout.JAVA_INT),      // flags
                ERRNO_STATE);

        // int close(int fd)
        CLOSE = LINKER.downcallHandle(
                STDLIB.find("close").orElseThrow(),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,       // return
                        ValueLayout.JAVA_INT),      // fd
                ERRNO_STATE);

        // ssize_t read(int fd, void *buf, size_t count)
        READ = LINKER.downcallHandle(
                STDLIB.find("read").orElseThrow(),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_LONG,      // return (ssize_t)
                        ValueLayout.JAVA_INT,       // fd
                        ValueLayout.ADDRESS,        // buf
                        ValueLayout.JAVA_LONG),     // count (size_t)
                ERRNO_STATE);

        // int poll(struct pollfd *fds, nfds_t nfds, int timeout)
        // nfds_t is unsigned long (8 bytes) on Linux, unsigned int (4 bytes) on macOS
        POLL = LINKER.downcallHandle(
                STDLIB.find("poll").orElseThrow(),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,       // return
                        ValueLayout.ADDRESS,        // fds
                        PosixConstants.IS_MACOS
                                ? ValueLayout.JAVA_INT      // nfds_t = unsigned int on macOS
                                : ValueLayout.JAVA_LONG,    // nfds_t = unsigned long on Linux
                        ValueLayout.JAVA_INT),      // timeout
                ERRNO_STATE);

        // int tcgetattr(int fd, struct termios *termios_p)
        TCGETATTR = LINKER.downcallHandle(
                STDLIB.find("tcgetattr").orElseThrow(),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,       // return
                        ValueLayout.JAVA_INT,       // fd
                        ValueLayout.ADDRESS),       // termios_p
                ERRNO_STATE);

        // int tcsetattr(int fd, int optional_actions, const struct termios *termios_p)
        TCSETATTR = LINKER.downcallHandle(
                STDLIB.find("tcsetattr").orElseThrow(),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,       // return
                        ValueLayout.JAVA_INT,       // fd
                        ValueLayout.JAVA_INT,       // optional_actions
                        ValueLayout.ADDRESS),       // termios_p
                ERRNO_STATE);

        // int ioctl(int fd, unsigned long request, ...)
        // We bind the variadic form with one pointer arg for TIOCGWINSZ
        IOCTL = LINKER.downcallHandle(
                STDLIB.find("ioctl").orElseThrow(),
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,       // return
                        ValueLayout.JAVA_INT,       // fd
                        ValueLayout.JAVA_LONG,      // request
                        ValueLayout.ADDRESS),       // arg (struct winsize *)
                ERRNO_STATE,
                Linker.Option.firstVariadicArg(2));
    }

    /**
     * Tests whether a file descriptor refers to a terminal.
     *
     * @param fd the file descriptor to check (0=stdin, 1=stdout, 2=stderr)
     * @return true if the file descriptor is connected to a terminal
     */
    static boolean isatty(int fd) {
        try {
            return (int) ISATTY.invokeExact(fd) == 1;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Opens a file and returns the file descriptor.
     *
     * @param arena the arena for allocating the pathname string
     * @param pathname the file path to open
     * @param flags the open flags (e.g., O_RDWR)
     * @return the file descriptor, or -1 on error
     */
    static int open(Arena arena, String pathname, int flags) {
        try {
            MemorySegment path = arena.allocateFrom(pathname);
            MemorySegment capturedState = arena.allocate(Linker.Option.captureStateLayout());
            return (int) OPEN.invokeExact(capturedState, path, flags);
        } catch (Throwable t) {
            throw new RuntimeException("open() failed for " + pathname, t);
        }
    }

    /**
     * Closes a file descriptor.
     *
     * @param fd the file descriptor to close
     * @return 0 on success, -1 on error
     */
    static int close(int fd) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment capturedState = arena.allocate(Linker.Option.captureStateLayout());
            return (int) CLOSE.invokeExact(capturedState, fd);
        } catch (Throwable t) {
            throw new RuntimeException("close() failed", t);
        }
    }

    /**
     * Reads from a file descriptor into a buffer.
     *
     * @param fd the file descriptor
     * @param buf the buffer to read into
     * @param count the maximum number of bytes to read
     * @return the number of bytes read, 0 for EOF, or -1 on error
     */
    static long read(int fd, MemorySegment buf, long count) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment capturedState = arena.allocate(Linker.Option.captureStateLayout());
            return (long) READ.invokeExact(capturedState, fd, buf, count);
        } catch (Throwable t) {
            throw new RuntimeException("read() failed", t);
        }
    }

    /**
     * Waits for events on a file descriptor.
     *
     * @param pollfd the pollfd struct (already populated with fd and events)
     * @param nfds the number of file descriptors
     * @param timeout timeout in milliseconds (-1 = infinite, 0 = non-blocking)
     * @return the number of fds with events, 0 on timeout, -1 on error
     */
    static int poll(MemorySegment pollfd, int nfds, int timeout) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment capturedState = arena.allocate(Linker.Option.captureStateLayout());
            if (PosixConstants.IS_MACOS) {
                return (int) POLL.invokeExact(capturedState, pollfd, nfds, timeout);
            } else {
                return (int) POLL.invokeExact(capturedState, pollfd, (long) nfds, timeout);
            }
        } catch (Throwable t) {
            throw new RuntimeException("poll() failed", t);
        }
    }

    /**
     * Gets the current terminal attributes.
     *
     * @param fd the terminal file descriptor
     * @param termios the termios struct to fill
     * @return 0 on success, -1 on error
     */
    static int tcgetattr(int fd, MemorySegment termios) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment capturedState = arena.allocate(Linker.Option.captureStateLayout());
            return (int) TCGETATTR.invokeExact(capturedState, fd, termios);
        } catch (Throwable t) {
            throw new RuntimeException("tcgetattr() failed", t);
        }
    }

    /**
     * Sets the terminal attributes.
     *
     * @param fd the terminal file descriptor
     * @param optionalActions when to apply (TCSANOW, TCSADRAIN, TCSAFLUSH)
     * @param termios the termios struct with the new attributes
     * @return 0 on success, -1 on error
     */
    static int tcsetattr(int fd, int optionalActions, MemorySegment termios) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment capturedState = arena.allocate(Linker.Option.captureStateLayout());
            return (int) TCSETATTR.invokeExact(capturedState, fd, optionalActions, termios);
        } catch (Throwable t) {
            throw new RuntimeException("tcsetattr() failed", t);
        }
    }

    /**
     * Performs an ioctl operation on a file descriptor.
     *
     * @param fd the file descriptor
     * @param request the ioctl request code
     * @param arg the argument (typically a struct pointer)
     * @return 0 on success, -1 on error
     */
    static int ioctl(int fd, long request, MemorySegment arg) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment capturedState = arena.allocate(Linker.Option.captureStateLayout());
            return (int) IOCTL.invokeExact(capturedState, fd, request, arg);
        } catch (Throwable t) {
            throw new RuntimeException("ioctl() failed", t);
        }
    }

    private LibC() {
    }
}
