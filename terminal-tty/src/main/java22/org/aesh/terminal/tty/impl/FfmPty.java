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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.EnumMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.aesh.terminal.Attributes;
import org.aesh.terminal.Attributes.ControlChar;
import org.aesh.terminal.Attributes.ControlFlag;
import org.aesh.terminal.Attributes.InputFlag;
import org.aesh.terminal.Attributes.LocalFlag;
import org.aesh.terminal.Attributes.OutputFlag;
import org.aesh.terminal.tty.Size;
import org.aesh.terminal.utils.LoggerUtil;

/**
 * FFM-based {@link Pty} implementation for POSIX systems (Linux and macOS).
 * <p>
 * Uses {@code tcgetattr}/{@code tcsetattr} for terminal attributes,
 * {@code ioctl(TIOCGWINSZ)} for terminal size, and direct file descriptor
 * I/O for input/output. No external processes are spawned.
 * <p>
 * On macOS, uses {@code STDIN_FILENO} (fd 0) because {@code poll()} does
 * not work reliably with a separately opened {@code /dev/tty}. On Linux,
 * opens {@code /dev/tty} with {@code O_RDWR} to bypass stdin/stdout
 * redirection.
 * <p>
 * Requires Java 22+ and {@code --enable-native-access=ALL-UNNAMED} at runtime.
 */
public class FfmPty implements Pty {

    private static final Logger LOGGER = LoggerUtil.getLogger(FfmPty.class.getName());

    /** The long-lived arena for this PTY instance. All native memory is freed on close(). */
    private final Arena arena;

    /** The terminal file descriptor. */
    private final int ttyFd;

    /** Whether we opened the fd ourselves (Linux) vs using stdin (macOS). */
    private final boolean ownsFd;

    /** The saved termios struct from construction time, for restoration on close(). */
    private final MemorySegment savedTermios;

    /** The TTY device name (e.g., "/dev/tty" or "stdin"). */
    private final String ttyName;

    /** Lazy-created InputStream wrapping the tty fd. */
    private volatile InputStream slaveInput;

    /** Lazy-created OutputStream wrapping the tty fd. */
    private volatile OutputStream slaveOutput;

    /** Whether this PTY has been closed. */
    private final java.util.concurrent.atomic.AtomicBoolean closed = new java.util.concurrent.atomic.AtomicBoolean(false);

    /** Pre-allocated pollfd struct for non-blocking read/peek operations. */
    private final MemorySegment nbPollfd;

    /** Pre-allocated single-byte read buffer for non-blocking operations. */
    private final MemorySegment nbReadBuf;

    /** Peeked byte for non-blocking peek support. -2 means no peeked byte. */
    private int peekedByte = -2;

    /**
     * Creates an FfmPty for the current terminal.
     *
     * @return a new FfmPty instance
     * @throws IOException if the terminal cannot be opened or queried
     */
    public static Pty current() throws IOException {
        return new FfmPty();
    }

    private FfmPty() throws IOException {
        this.arena = Arena.ofShared();

        try {
            if (PosixConstants.IS_MACOS) {
                // macOS: use stdin fd because poll() doesn't work with
                // a separately opened /dev/tty
                this.ttyFd = 0; // STDIN_FILENO
                this.ownsFd = false;
                this.ttyName = "stdin";
            } else {
                // Linux: open /dev/tty to get an independent fd
                int fd = LibC.open(arena, "/dev/tty", PosixConstants.O_RDWR);
                if (fd < 0) {
                    throw new IOException("Failed to open /dev/tty");
                }
                this.ttyFd = fd;
                this.ownsFd = true;
                this.ttyName = "/dev/tty";
            }

            // Snapshot the current terminal state for restoration on close()
            this.savedTermios = arena.allocate(PosixConstants.TERMIOS_SIZE, 8);
            int rc = LibC.tcgetattr(ttyFd, savedTermios);
            if (rc != 0) {
                if (ownsFd) {
                    LibC.close(ttyFd);
                }
                throw new IOException("tcgetattr() failed on " + ttyName);
            }

            // Allocate pollfd and read buffer for non-blocking operations
            this.nbPollfd = arena.allocate(PosixConstants.POLLFD_SIZE, 4);
            nbPollfd.set(ValueLayout.JAVA_INT, PosixConstants.POLLFD_FD_OFFSET, ttyFd);
            nbPollfd.set(ValueLayout.JAVA_SHORT, PosixConstants.POLLFD_EVENTS_OFFSET, PosixConstants.POLLIN);
            this.nbReadBuf = arena.allocate(1);

            LOGGER.log(Level.FINE, "FfmPty created for {0} (fd={1})", new Object[] { ttyName, ttyFd });

        } catch (Throwable t) {
            arena.close();
            if (t instanceof IOException) {
                throw (IOException) t;
            }
            throw new IOException("Failed to initialize FfmPty", t);
        }
    }

    // =========================================================================
    // Pty interface — stream access
    // =========================================================================

    @Override
    public InputStream getMasterInput() {
        throw new UnsupportedOperationException("Master I/O not supported");
    }

    @Override
    public OutputStream getMasterOutput() {
        throw new UnsupportedOperationException("Master I/O not supported");
    }

    @Override
    public InputStream getSlaveInput() {
        if (slaveInput == null) {
            synchronized (this) {
                if (slaveInput == null) {
                    slaveInput = new FfmInputStream();
                }
            }
        }
        return slaveInput;
    }

    @Override
    public OutputStream getSlaveOutput() {
        if (slaveOutput == null) {
            synchronized (this) {
                if (slaveOutput == null) {
                    slaveOutput = new FfmOutputStream();
                }
            }
        }
        return slaveOutput;
    }

    // =========================================================================
    // Pty interface — attributes
    // =========================================================================

    @Override
    public Attributes getAttr() throws IOException {
        checkNotClosed();
        try (Arena local = Arena.ofConfined()) {
            MemorySegment termios = local.allocate(PosixConstants.TERMIOS_SIZE, 8);
            int rc = LibC.tcgetattr(ttyFd, termios);
            if (rc != 0) {
                throw new IOException("tcgetattr() failed");
            }
            return termiosToAttributes(termios);
        }
    }

    @Override
    public void setAttr(Attributes attr) throws IOException {
        checkNotClosed();
        try (Arena local = Arena.ofConfined()) {
            // Read current raw termios first to preserve fields not modeled
            // by Attributes (baud rates, c_line, etc.)
            MemorySegment termios = local.allocate(PosixConstants.TERMIOS_SIZE, 8);
            int rc = LibC.tcgetattr(ttyFd, termios);
            if (rc != 0) {
                throw new IOException("tcgetattr() failed");
            }

            // Apply the Attributes onto the raw termios struct
            attributesToTermios(attr, termios);

            rc = LibC.tcsetattr(ttyFd, PosixConstants.TCSAFLUSH, termios);
            if (rc != 0) {
                throw new IOException("tcsetattr() failed");
            }
        }
    }

    // =========================================================================
    // Pty interface — size
    // =========================================================================

    @Override
    public Size getSize() throws IOException {
        checkNotClosed();
        try (Arena local = Arena.ofConfined()) {
            MemorySegment winsize = local.allocate(PosixConstants.WINSIZE_SIZE, 2);
            int rc = LibC.ioctl(ttyFd, PosixConstants.TIOCGWINSZ, winsize);
            if (rc != 0) {
                throw new IOException("ioctl(TIOCGWINSZ) failed");
            }
            short rows = winsize.get(ValueLayout.JAVA_SHORT, PosixConstants.WINSIZE_ROW_OFFSET);
            short cols = winsize.get(ValueLayout.JAVA_SHORT, PosixConstants.WINSIZE_COL_OFFSET);
            return new Size(Short.toUnsignedInt(cols), Short.toUnsignedInt(rows));
        }
    }

    // =========================================================================
    // Pty interface — lifecycle
    // =========================================================================

    @Override
    public void close() throws IOException {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        try {
            // Restore original terminal attributes
            // Use TCSANOW for immediate restore on close (TCSAFLUSH can delay)
            int rc = LibC.tcsetattr(ttyFd, PosixConstants.TCSANOW, savedTermios);
            if (rc != 0) {
                LOGGER.log(Level.WARNING, "Failed to restore terminal attributes on close");
            }
        } finally {
            try {
                // Close stream arenas to free pollfd/readBuf native memory
                // and unblock any in-flight poll() calls
                if (slaveInput instanceof FfmInputStream) {
                    try { ((FfmInputStream) slaveInput).close(); } catch (Exception e) { /* ignore */ }
                }
                if (slaveOutput instanceof FfmOutputStream) {
                    try { ((FfmOutputStream) slaveOutput).close(); } catch (Exception e) { /* ignore */ }
                }
            } finally {
                try {
                    // Close the fd if we opened it (Linux)
                    if (ownsFd) {
                        LibC.close(ttyFd);
                    }
                } finally {
                    // Free all native memory.
                    // The poll-based read loop may still have an active poll() call
                    // using nbPollfd/nbReadBuf from this arena. Wait briefly for it
                    // to notice the closed flag and return.
                    for (int i = 0; i < 5; i++) {
                        try {
                            arena.close();
                            break;
                        } catch (IllegalStateException e) {
                            if (i < 4) {
                                try { Thread.sleep(50); } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                    break;
                                }
                            } else {
                                LOGGER.log(Level.FINE, "Arena still in use after retries, ignoring", e);
                            }
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // Non-blocking read/peek operations
    // =========================================================================

    @Override
    public boolean supportsNonBlockingRead() {
        return true;
    }

    @Override
    public int read(long timeoutMs) throws IOException {
        checkNotClosed();

        // Return peeked byte if available
        if (peekedByte != -2) {
            int b = peekedByte;
            peekedByte = -2;
            return b;
        }

        return pollAndRead(timeoutMs);
    }

    @Override
    public int peek(long timeoutMs) throws IOException {
        checkNotClosed();

        if (peekedByte != -2) {
            return peekedByte;
        }

        peekedByte = pollAndRead(timeoutMs);
        return peekedByte;
    }

    @Override
    public int read(byte[] b, int off, int len, long timeoutMs) throws IOException {
        if (b == null) throw new NullPointerException();
        if (off < 0 || len < 0 || len > b.length - off) throw new IndexOutOfBoundsException();
        if (len == 0) return 0;
        checkNotClosed();

        // First byte: use timeout
        int first = read(timeoutMs);
        if (first < 0) return first; // EOF or READ_EXPIRED
        b[off] = (byte) first;

        // Remaining bytes: non-blocking (timeout=0)
        int count = 1;
        while (count < len && !closed.get()) {
            int next = pollAndRead(0);
            if (next < 0) break; // no more data immediately available
            b[off + count] = (byte) next;
            count++;
        }
        return count;
    }

    /**
     * Performs a poll() + read() on the tty fd with the specified timeout.
     *
     * @param timeoutMs timeout in milliseconds; 0 for non-blocking, negative for infinite
     * @return the byte read (0-255), -1 for EOF, or READ_EXPIRED (-2) for timeout
     */
    private int pollAndRead(long timeoutMs) throws IOException {
        // Clamp timeout for poll(): negative -> -1 (infinite), otherwise use as-is
        int pollTimeout = timeoutMs < 0 ? -1 : (int) Math.min(timeoutMs, Integer.MAX_VALUE);

        // Reset revents
        nbPollfd.set(ValueLayout.JAVA_SHORT, PosixConstants.POLLFD_REVENTS_OFFSET, (short) 0);

        int result = LibC.poll(nbPollfd, 1, pollTimeout);

        if (result < 0) {
            // EINTR (signal interrupted poll) — treat as timeout
            // The caller can retry if desired
            if (closed.get()) return -1;
            return -2;
        }

        if (result == 0) {
            return -2; // Timeout
        }

        short revents = nbPollfd.get(ValueLayout.JAVA_SHORT, PosixConstants.POLLFD_REVENTS_OFFSET);
        if ((revents & (PosixConstants.POLLHUP | PosixConstants.POLLERR)) != 0) {
            return -1; // EOF or error
        }

        if ((revents & PosixConstants.POLLIN) != 0) {
            long bytesRead = LibC.read(ttyFd, nbReadBuf, 1);
            if (bytesRead <= 0) {
                if (bytesRead < 0 && !closed.get()) {
                    // Likely EINTR between poll and read — treat as timeout
                    return -2;
                }
                return -1; // EOF
            }
            return Byte.toUnsignedInt(nbReadBuf.get(ValueLayout.JAVA_BYTE, 0));
        }

        return -2; // No data available
    }

    // =========================================================================
    // Termios <-> Attributes conversion
    // =========================================================================

    /**
     * Reads the raw termios struct fields into an Attributes object.
     */
    private Attributes termiosToAttributes(MemorySegment termios) {
        Attributes attr = new Attributes();

        long iflag = getTermiosFlag(termios, PosixConstants.TERMIOS_IFLAG_OFFSET);
        long oflag = getTermiosFlag(termios, PosixConstants.TERMIOS_OFLAG_OFFSET);
        long cflag = getTermiosFlag(termios, PosixConstants.TERMIOS_CFLAG_OFFSET);
        long lflag = getTermiosFlag(termios, PosixConstants.TERMIOS_LFLAG_OFFSET);

        // Input flags
        for (InputFlag flag : InputFlag.values()) {
            long bit = PosixConstants.inputFlagBit(flag);
            if (bit >= 0) {
                attr.setInputFlag(flag, (iflag & bit) != 0);
            }
        }

        // Output flags
        for (OutputFlag flag : OutputFlag.values()) {
            long bit = PosixConstants.outputFlagBit(flag);
            if (bit >= 0) {
                attr.setOutputFlag(flag, (oflag & bit) != 0);
            }
        }

        // Control flags — special handling for CSIZE (CS5-CS8) and CIGNORE
        for (ControlFlag flag : ControlFlag.values()) {
            if (flag == ControlFlag.CIGNORE) {
                continue; // no termios equivalent
            }
            long bit = PosixConstants.controlFlagBit(flag);
            if (bit < 0) {
                continue;
            }
            // CS5-CS8 are multi-bit fields, not single-bit flags
            if (flag == ControlFlag.CS5 || flag == ControlFlag.CS6
                    || flag == ControlFlag.CS7 || flag == ControlFlag.CS8) {
                attr.setControlFlag(flag, (cflag & PosixConstants.CSIZE_MASK) == bit);
            } else {
                attr.setControlFlag(flag, (cflag & bit) != 0);
            }
        }

        // Local flags
        for (LocalFlag flag : LocalFlag.values()) {
            long bit = PosixConstants.localFlagBit(flag);
            if (bit >= 0) {
                attr.setLocalFlag(flag, (lflag & bit) != 0);
            }
        }

        // Control characters
        for (ControlChar cc : ControlChar.values()) {
            int idx = PosixConstants.ccIndex(cc);
            if (idx >= 0 && idx < PosixConstants.TERMIOS_CC_COUNT) {
                int value = Byte.toUnsignedInt(
                        termios.get(ValueLayout.JAVA_BYTE,
                                PosixConstants.TERMIOS_CC_OFFSET + idx));
                attr.setControlChar(cc, value);
            }
        }

        return attr;
    }

    /**
     * Writes the Attributes values into a raw termios struct.
     * The struct should have been pre-populated with tcgetattr() to preserve
     * fields not modeled by Attributes.
     */
    private void attributesToTermios(Attributes attr, MemorySegment termios) {
        // Input flags: preserve bits not modeled by Attributes
        long iflag = getTermiosFlag(termios, PosixConstants.TERMIOS_IFLAG_OFFSET);
        long iflagMask = 0;
        for (InputFlag flag : InputFlag.values()) {
            long bit = PosixConstants.inputFlagBit(flag);
            if (bit >= 0) {
                iflagMask |= bit;
            }
        }
        iflag &= ~iflagMask;
        for (InputFlag flag : InputFlag.values()) {
            long bit = PosixConstants.inputFlagBit(flag);
            if (bit >= 0 && attr.getInputFlag(flag)) {
                iflag |= bit;
            }
        }
        setTermiosFlag(termios, PosixConstants.TERMIOS_IFLAG_OFFSET, iflag);

        // Output flags: preserve bits not modeled by Attributes
        long oflag = getTermiosFlag(termios, PosixConstants.TERMIOS_OFLAG_OFFSET);
        long oflagMask = 0;
        for (OutputFlag flag : OutputFlag.values()) {
            long bit = PosixConstants.outputFlagBit(flag);
            if (bit >= 0) {
                oflagMask |= bit;
            }
        }
        oflag &= ~oflagMask;
        for (OutputFlag flag : OutputFlag.values()) {
            long bit = PosixConstants.outputFlagBit(flag);
            if (bit >= 0 && attr.getOutputFlag(flag)) {
                oflag |= bit;
            }
        }
        setTermiosFlag(termios, PosixConstants.TERMIOS_OFLAG_OFFSET, oflag);

        // Control flags: preserve bits not modeled by Attributes
        long cflag = getTermiosFlag(termios, PosixConstants.TERMIOS_CFLAG_OFFSET);
        long cflagMask = PosixConstants.CSIZE_MASK;
        for (ControlFlag flag : ControlFlag.values()) {
            if (flag == ControlFlag.CIGNORE) continue;
            long bit = PosixConstants.controlFlagBit(flag);
            if (bit >= 0 && flag != ControlFlag.CS5 && flag != ControlFlag.CS6
                    && flag != ControlFlag.CS7 && flag != ControlFlag.CS8) {
                cflagMask |= bit;
            }
        }
        cflag &= ~cflagMask;
        for (ControlFlag flag : ControlFlag.values()) {
            if (flag == ControlFlag.CIGNORE) continue;
            long bit = PosixConstants.controlFlagBit(flag);
            if (bit >= 0 && attr.getControlFlag(flag)) {
                cflag |= bit;
            }
        }
        setTermiosFlag(termios, PosixConstants.TERMIOS_CFLAG_OFFSET, cflag);

        // Local flags: preserve bits not modeled by Attributes
        long lflag = getTermiosFlag(termios, PosixConstants.TERMIOS_LFLAG_OFFSET);
        long lflagMask = 0;
        for (LocalFlag flag : LocalFlag.values()) {
            long bit = PosixConstants.localFlagBit(flag);
            if (bit >= 0) {
                lflagMask |= bit;
            }
        }
        lflag &= ~lflagMask;
        for (LocalFlag flag : LocalFlag.values()) {
            long bit = PosixConstants.localFlagBit(flag);
            if (bit >= 0 && attr.getLocalFlag(flag)) {
                lflag |= bit;
            }
        }
        setTermiosFlag(termios, PosixConstants.TERMIOS_LFLAG_OFFSET, lflag);

        // Control characters
        EnumMap<ControlChar, Integer> cchars = attr.getControlChars();
        for (ControlChar cc : ControlChar.values()) {
            int idx = PosixConstants.ccIndex(cc);
            if (idx >= 0 && idx < PosixConstants.TERMIOS_CC_COUNT) {
                int value = cchars.getOrDefault(cc, 0);
                if (value >= 0) {
                    termios.set(ValueLayout.JAVA_BYTE,
                            PosixConstants.TERMIOS_CC_OFFSET + idx,
                            (byte) value);
                }
            }
        }
    }

    /**
     * Reads a tcflag_t from the termios struct, handling the different
     * sizes on Linux (4 bytes) vs macOS (8 bytes).
     */
    private long getTermiosFlag(MemorySegment termios, long offset) {
        if (PosixConstants.IS_MACOS) {
            return termios.get(ValueLayout.JAVA_LONG, offset);
        } else {
            return Integer.toUnsignedLong(termios.get(ValueLayout.JAVA_INT, offset));
        }
    }

    /**
     * Writes a tcflag_t into the termios struct.
     */
    private void setTermiosFlag(MemorySegment termios, long offset, long value) {
        if (PosixConstants.IS_MACOS) {
            termios.set(ValueLayout.JAVA_LONG, offset, value);
        } else {
            termios.set(ValueLayout.JAVA_INT, offset, (int) value);
        }
    }

    // =========================================================================
    // Internal InputStream/OutputStream implementations
    // =========================================================================

    /**
     * InputStream that reads from the tty file descriptor using poll() + read().
     * This provides blocking reads that can be interrupted on close().
     */
    private class FfmInputStream extends InputStream {

        private final MemorySegment pollfd;
        private final MemorySegment readBuf;
        private final Arena streamArena;

        FfmInputStream() {
            this.streamArena = Arena.ofShared();
            this.pollfd = streamArena.allocate(PosixConstants.POLLFD_SIZE, 4);
            this.readBuf = streamArena.allocate(1);
            // Pre-populate pollfd with fd and events
            pollfd.set(ValueLayout.JAVA_INT, PosixConstants.POLLFD_FD_OFFSET, ttyFd);
            pollfd.set(ValueLayout.JAVA_SHORT, PosixConstants.POLLFD_EVENTS_OFFSET, PosixConstants.POLLIN);
        }

        @Override
        public int read() throws IOException {
            if (closed.get()) {
                return -1;
            }
            while (!closed.get()) {
                // Reset revents
                pollfd.set(ValueLayout.JAVA_SHORT, PosixConstants.POLLFD_REVENTS_OFFSET, (short) 0);

                // Poll with 100ms timeout to remain responsive to close()
                int result = LibC.poll(pollfd, 1, 100);
                if (result < 0) {
                    if (closed.get()) {
                        return -1;
                    }
                    // Likely EINTR — signal interrupted poll, retry
                    continue;
                }
                if (result == 0) {
                    // Timeout — check if closed and retry
                    continue;
                }

                short revents = pollfd.get(ValueLayout.JAVA_SHORT, PosixConstants.POLLFD_REVENTS_OFFSET);
                if ((revents & (PosixConstants.POLLHUP | PosixConstants.POLLERR)) != 0) {
                    return -1; // EOF or error
                }
                if ((revents & PosixConstants.POLLIN) != 0) {
                    long bytesRead = LibC.read(ttyFd, readBuf, 1);
                    if (bytesRead == 0) {
                        return -1; // EOF
                    }
                    if (bytesRead < 0) {
                        // Likely EINTR (signal between poll and read), retry
                        continue;
                    }
                    return Byte.toUnsignedInt(readBuf.get(ValueLayout.JAVA_BYTE, 0));
                }
            }
            return -1;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (b == null) throw new NullPointerException();
            if (off < 0 || len < 0 || len > b.length - off) throw new IndexOutOfBoundsException();
            if (len == 0) return 0;

            // Read first byte (blocking)
            int first = read();
            if (first < 0) return -1;
            b[off] = (byte) first;

            // Try to read more if available (non-blocking)
            int count = 1;
            while (count < len && !closed.get()) {
                // Non-blocking poll
                pollfd.set(ValueLayout.JAVA_SHORT, PosixConstants.POLLFD_REVENTS_OFFSET, (short) 0);
                int result = LibC.poll(pollfd, 1, 0);
                if (result <= 0) break;

                short revents = pollfd.get(ValueLayout.JAVA_SHORT, PosixConstants.POLLFD_REVENTS_OFFSET);
                if ((revents & PosixConstants.POLLIN) == 0) break;

                long bytesRead = LibC.read(ttyFd, readBuf, 1);
                if (bytesRead <= 0) break;
                b[off + count] = readBuf.get(ValueLayout.JAVA_BYTE, 0);
                count++;
            }
            return count;
        }

        @Override
        public void close() {
            streamArena.close();
        }
    }

    /**
     * OutputStream that writes to the terminal.
     * On Linux, opens a separate FileOutputStream to /dev/tty (matching
     * ExecPty's approach of opening the tty device for output).
     * On macOS (where we use stdin fd), delegates to System.out.
     */
    private class FfmOutputStream extends OutputStream {

        private final OutputStream delegate;

        FfmOutputStream() {
            if (ownsFd) {
                // Linux: open /dev/tty for output, same as ExecPty.getSlaveOutput()
                try {
                    this.delegate = new java.io.FileOutputStream(ttyName);
                } catch (java.io.FileNotFoundException e) {
                    throw new RuntimeException("Failed to open " + ttyName + " for output", e);
                }
            } else {
                // macOS: use System.out since we're using stdin fd for input
                this.delegate = System.out;
            }
        }

        @Override
        public void write(int b) throws IOException {
            delegate.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            delegate.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            if (delegate != System.out) {
                delegate.close();
            }
        }
    }

    // =========================================================================
    // Internal utilities
    // =========================================================================

    private void checkNotClosed() throws IOException {
        if (closed.get()) {
            throw new IOException("FfmPty is closed");
        }
    }

    /**
     * Returns the TTY device name for this PTY.
     */
    public String getName() {
        return ttyName;
    }
}
