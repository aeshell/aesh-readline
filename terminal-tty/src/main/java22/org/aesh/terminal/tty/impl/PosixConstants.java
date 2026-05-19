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

import java.util.EnumMap;

import org.aesh.terminal.Attributes;
import org.aesh.terminal.Attributes.ControlChar;
import org.aesh.terminal.Attributes.ControlFlag;
import org.aesh.terminal.Attributes.InputFlag;
import org.aesh.terminal.Attributes.LocalFlag;
import org.aesh.terminal.Attributes.OutputFlag;

/**
 * Platform-specific constants for POSIX terminal access via FFM.
 * <p>
 * Covers struct layouts, flag bitmasks, and c_cc indices for
 * Linux (x86_64/aarch64) and macOS (x86_64/aarch64).
 */
final class PosixConstants {

    static final boolean IS_MACOS;
    static {
        String osName = System.getProperty("os.name", "").toLowerCase();
        IS_MACOS = osName.startsWith("mac") || osName.contains("darwin");
    }

    // =========================================================================
    // open() flags
    // =========================================================================
    static final int O_RDWR = 0x0002;

    // =========================================================================
    // poll() constants
    // =========================================================================
    static final short POLLIN  = 0x0001;
    static final short POLLHUP = 0x0010;
    static final short POLLERR = 0x0008;

    // struct pollfd layout: { int fd; short events; short revents; } = 8 bytes
    static final long POLLFD_SIZE = 8;
    static final long POLLFD_FD_OFFSET = 0;
    static final long POLLFD_EVENTS_OFFSET = 4;
    static final long POLLFD_REVENTS_OFFSET = 6;

    // =========================================================================
    // tcsetattr() action constants
    // =========================================================================
    static final int TCSANOW   = 0;
    static final int TCSADRAIN = 1;
    static final int TCSAFLUSH = 2;

    // =========================================================================
    // ioctl requests
    // =========================================================================
    static final long TIOCGWINSZ = IS_MACOS ? 0x40087468L : 0x00005413L;

    // struct winsize: { unsigned short ws_row, ws_col, ws_xpixel, ws_ypixel; } = 8 bytes
    static final long WINSIZE_SIZE = 8;
    static final long WINSIZE_ROW_OFFSET = 0;
    static final long WINSIZE_COL_OFFSET = 2;

    // =========================================================================
    // Signals
    // =========================================================================
    static final int SIGWINCH = 28; // same on Linux and macOS

    // =========================================================================
    // termios struct layout
    // =========================================================================
    //
    // Linux x86_64/aarch64:
    //   struct termios {
    //     tcflag_t c_iflag;    // unsigned int, 4 bytes, offset 0
    //     tcflag_t c_oflag;    // unsigned int, 4 bytes, offset 4
    //     tcflag_t c_cflag;    // unsigned int, 4 bytes, offset 8
    //     tcflag_t c_lflag;    // unsigned int, 4 bytes, offset 12
    //     cc_t     c_line;     // unsigned char, 1 byte, offset 16
    //     cc_t     c_cc[32];   // unsigned char[32], offset 17
    //     speed_t  c_ispeed;   // unsigned int, 4 bytes, offset 52 (aligned)
    //     speed_t  c_ospeed;   // unsigned int, 4 bytes, offset 56
    //   };
    //   Total: 60 bytes
    //
    // macOS x86_64/aarch64:
    //   struct termios {
    //     tcflag_t c_iflag;    // unsigned long, 8 bytes, offset 0
    //     tcflag_t c_oflag;    // unsigned long, 8 bytes, offset 8
    //     tcflag_t c_cflag;    // unsigned long, 8 bytes, offset 16
    //     tcflag_t c_lflag;    // unsigned long, 8 bytes, offset 24
    //     cc_t     c_cc[20];   // unsigned char[20], offset 32
    //     speed_t  c_ispeed;   // unsigned long, 8 bytes, offset 56 (aligned)
    //     speed_t  c_ospeed;   // unsigned long, 8 bytes, offset 64
    //   };
    //   Total: 72 bytes

    /** Size of struct termios in bytes. */
    static final long TERMIOS_SIZE = IS_MACOS ? 72 : 60;

    /** Size of a single tcflag_t field in bytes (4 on Linux, 8 on macOS). */
    static final int FLAG_SIZE = IS_MACOS ? 8 : 4;

    static final long TERMIOS_IFLAG_OFFSET = 0;
    static final long TERMIOS_OFLAG_OFFSET = IS_MACOS ? 8 : 4;
    static final long TERMIOS_CFLAG_OFFSET = IS_MACOS ? 16 : 8;
    static final long TERMIOS_LFLAG_OFFSET = IS_MACOS ? 24 : 12;
    static final long TERMIOS_CC_OFFSET    = IS_MACOS ? 32 : 17;
    static final int  TERMIOS_CC_COUNT     = IS_MACOS ? 20 : 32;

    // =========================================================================
    // InputFlag bitmasks
    // =========================================================================

    private static final EnumMap<InputFlag, Long> INPUT_FLAG_BITS = new EnumMap<>(InputFlag.class);
    static {
        if (IS_MACOS) {
            INPUT_FLAG_BITS.put(InputFlag.IGNBRK,  0x00000001L);
            INPUT_FLAG_BITS.put(InputFlag.BRKINT,  0x00000002L);
            INPUT_FLAG_BITS.put(InputFlag.IGNPAR,  0x00000004L);
            INPUT_FLAG_BITS.put(InputFlag.PARMRK,  0x00000008L);
            INPUT_FLAG_BITS.put(InputFlag.INPCK,   0x00000010L);
            INPUT_FLAG_BITS.put(InputFlag.ISTRIP,  0x00000020L);
            INPUT_FLAG_BITS.put(InputFlag.INLCR,   0x00000040L);
            INPUT_FLAG_BITS.put(InputFlag.IGNCR,   0x00000080L);
            INPUT_FLAG_BITS.put(InputFlag.ICRNL,   0x00000100L);
            INPUT_FLAG_BITS.put(InputFlag.IXON,    0x00000200L);
            INPUT_FLAG_BITS.put(InputFlag.IXOFF,   0x00000400L);
            INPUT_FLAG_BITS.put(InputFlag.IXANY,   0x00000800L);
            INPUT_FLAG_BITS.put(InputFlag.IMAXBEL, 0x00002000L);
            // IUTF8 does not exist on macOS
        } else {
            INPUT_FLAG_BITS.put(InputFlag.IGNBRK,  0x00000001L);
            INPUT_FLAG_BITS.put(InputFlag.BRKINT,  0x00000002L);
            INPUT_FLAG_BITS.put(InputFlag.IGNPAR,  0x00000004L);
            INPUT_FLAG_BITS.put(InputFlag.PARMRK,  0x00000008L);
            INPUT_FLAG_BITS.put(InputFlag.INPCK,   0x00000010L);
            INPUT_FLAG_BITS.put(InputFlag.ISTRIP,  0x00000020L);
            INPUT_FLAG_BITS.put(InputFlag.INLCR,   0x00000040L);
            INPUT_FLAG_BITS.put(InputFlag.IGNCR,   0x00000080L);
            INPUT_FLAG_BITS.put(InputFlag.ICRNL,   0x00000100L);
            INPUT_FLAG_BITS.put(InputFlag.IXON,    0x00000400L);
            INPUT_FLAG_BITS.put(InputFlag.IXOFF,   0x00001000L);
            INPUT_FLAG_BITS.put(InputFlag.IXANY,   0x00000800L);
            INPUT_FLAG_BITS.put(InputFlag.IMAXBEL, 0x00002000L);
            INPUT_FLAG_BITS.put(InputFlag.IUTF8,   0x00004000L);
        }
    }

    // =========================================================================
    // OutputFlag bitmasks
    // =========================================================================

    private static final EnumMap<OutputFlag, Long> OUTPUT_FLAG_BITS = new EnumMap<>(OutputFlag.class);
    static {
        if (IS_MACOS) {
            OUTPUT_FLAG_BITS.put(OutputFlag.OPOST,  0x00000001L);
            OUTPUT_FLAG_BITS.put(OutputFlag.ONLCR,  0x00000002L);
            OUTPUT_FLAG_BITS.put(OutputFlag.OXTABS, 0x00000004L);
            OUTPUT_FLAG_BITS.put(OutputFlag.ONOEOT, 0x00000008L);
            OUTPUT_FLAG_BITS.put(OutputFlag.OCRNL,  0x00000010L);
            OUTPUT_FLAG_BITS.put(OutputFlag.ONOCR,  0x00000020L);
            OUTPUT_FLAG_BITS.put(OutputFlag.ONLRET, 0x00000040L);
            // NLDLY, CRDLY, FFDLY, BSDLY, VTDLY, TABDLY, OFILL, OFDEL: not on macOS
        } else {
            OUTPUT_FLAG_BITS.put(OutputFlag.OPOST,  0x00000001L);
            OUTPUT_FLAG_BITS.put(OutputFlag.ONLCR,  0x00000004L);
            OUTPUT_FLAG_BITS.put(OutputFlag.OCRNL,  0x00000008L);
            OUTPUT_FLAG_BITS.put(OutputFlag.ONOCR,  0x00000010L);
            OUTPUT_FLAG_BITS.put(OutputFlag.ONLRET, 0x00000020L);
            OUTPUT_FLAG_BITS.put(OutputFlag.OFILL,  0x00000040L);
            OUTPUT_FLAG_BITS.put(OutputFlag.OFDEL,  0x00000080L);
            OUTPUT_FLAG_BITS.put(OutputFlag.NLDLY,  0x00000100L);
            OUTPUT_FLAG_BITS.put(OutputFlag.CRDLY,  0x00000600L);
            OUTPUT_FLAG_BITS.put(OutputFlag.TABDLY, 0x00001800L);
            OUTPUT_FLAG_BITS.put(OutputFlag.BSDLY,  0x00002000L);
            OUTPUT_FLAG_BITS.put(OutputFlag.VTDLY,  0x00004000L);
            OUTPUT_FLAG_BITS.put(OutputFlag.FFDLY,  0x00008000L);
            // OXTABS and ONOEOT do not exist on Linux
        }
    }

    // =========================================================================
    // ControlFlag bitmasks
    // =========================================================================

    private static final EnumMap<ControlFlag, Long> CONTROL_FLAG_BITS = new EnumMap<>(ControlFlag.class);
    static {
        if (IS_MACOS) {
            // CIGNORE does not have a direct termios mapping; skip
            CONTROL_FLAG_BITS.put(ControlFlag.CS5,         0x00000000L);
            CONTROL_FLAG_BITS.put(ControlFlag.CS6,         0x00000100L);
            CONTROL_FLAG_BITS.put(ControlFlag.CS7,         0x00000200L);
            CONTROL_FLAG_BITS.put(ControlFlag.CS8,         0x00000300L);
            CONTROL_FLAG_BITS.put(ControlFlag.CSTOPB,      0x00000400L);
            CONTROL_FLAG_BITS.put(ControlFlag.CREAD,       0x00000800L);
            CONTROL_FLAG_BITS.put(ControlFlag.PARENB,      0x00001000L);
            CONTROL_FLAG_BITS.put(ControlFlag.PARODD,      0x00002000L);
            CONTROL_FLAG_BITS.put(ControlFlag.HUPCL,       0x00004000L);
            CONTROL_FLAG_BITS.put(ControlFlag.CLOCAL,      0x00008000L);
            CONTROL_FLAG_BITS.put(ControlFlag.CCTS_OFLOW,  0x00010000L);
            CONTROL_FLAG_BITS.put(ControlFlag.CRTS_IFLOW,  0x00020000L);
            CONTROL_FLAG_BITS.put(ControlFlag.CDTR_IFLOW,  0x00040000L);
            CONTROL_FLAG_BITS.put(ControlFlag.CDSR_OFLOW,  0x00080000L);
            CONTROL_FLAG_BITS.put(ControlFlag.CCAR_OFLOW,  0x00100000L);
        } else {
            CONTROL_FLAG_BITS.put(ControlFlag.CS5,         0x00000000L);
            CONTROL_FLAG_BITS.put(ControlFlag.CS6,         0x00000010L);
            CONTROL_FLAG_BITS.put(ControlFlag.CS7,         0x00000020L);
            CONTROL_FLAG_BITS.put(ControlFlag.CS8,         0x00000030L);
            CONTROL_FLAG_BITS.put(ControlFlag.CSTOPB,      0x00000040L);
            CONTROL_FLAG_BITS.put(ControlFlag.CREAD,       0x00000080L);
            CONTROL_FLAG_BITS.put(ControlFlag.PARENB,      0x00000100L);
            CONTROL_FLAG_BITS.put(ControlFlag.PARODD,      0x00000200L);
            CONTROL_FLAG_BITS.put(ControlFlag.HUPCL,       0x00000400L);
            CONTROL_FLAG_BITS.put(ControlFlag.CLOCAL,      0x00000800L);
            // Linux uses CRTSCTS (0x80000000) for both CTS output and RTS input flow control.
            // Map CCTS_OFLOW and CRTS_IFLOW to the same bit; others don't exist.
            CONTROL_FLAG_BITS.put(ControlFlag.CCTS_OFLOW,  0x80000000L);
            CONTROL_FLAG_BITS.put(ControlFlag.CRTS_IFLOW,  0x80000000L);
            // CDTR_IFLOW, CDSR_OFLOW, CCAR_OFLOW: not on Linux
        }
    }

    // Bitmask for CSIZE field (char size bits that must be cleared before setting CS5-CS8)
    static final long CSIZE_MASK = IS_MACOS ? 0x00000300L : 0x00000030L;

    // =========================================================================
    // LocalFlag bitmasks
    // =========================================================================

    private static final EnumMap<LocalFlag, Long> LOCAL_FLAG_BITS = new EnumMap<>(LocalFlag.class);
    static {
        if (IS_MACOS) {
            LOCAL_FLAG_BITS.put(LocalFlag.ECHOKE,     0x00000001L);
            LOCAL_FLAG_BITS.put(LocalFlag.ECHOE,      0x00000002L);
            LOCAL_FLAG_BITS.put(LocalFlag.ECHOK,      0x00000004L);
            LOCAL_FLAG_BITS.put(LocalFlag.ECHO,       0x00000008L);
            LOCAL_FLAG_BITS.put(LocalFlag.ECHONL,     0x00000010L);
            LOCAL_FLAG_BITS.put(LocalFlag.ECHOPRT,    0x00000020L);
            LOCAL_FLAG_BITS.put(LocalFlag.ECHOCTL,    0x00000040L);
            LOCAL_FLAG_BITS.put(LocalFlag.ISIG,       0x00000080L);
            LOCAL_FLAG_BITS.put(LocalFlag.ICANON,     0x00000100L);
            LOCAL_FLAG_BITS.put(LocalFlag.ALTWERASE,  0x00000200L);
            LOCAL_FLAG_BITS.put(LocalFlag.IEXTEN,     0x00000400L);
            LOCAL_FLAG_BITS.put(LocalFlag.EXTPROC,    0x00000800L);
            LOCAL_FLAG_BITS.put(LocalFlag.TOSTOP,     0x00400000L);
            LOCAL_FLAG_BITS.put(LocalFlag.FLUSHO,     0x00800000L);
            LOCAL_FLAG_BITS.put(LocalFlag.NOKERNINFO, 0x02000000L);
            LOCAL_FLAG_BITS.put(LocalFlag.PENDIN,     0x20000000L);
            LOCAL_FLAG_BITS.put(LocalFlag.NOFLSH,     0x80000000L);
        } else {
            LOCAL_FLAG_BITS.put(LocalFlag.ISIG,    0x00000001L);
            LOCAL_FLAG_BITS.put(LocalFlag.ICANON,  0x00000002L);
            LOCAL_FLAG_BITS.put(LocalFlag.ECHO,    0x00000008L);
            LOCAL_FLAG_BITS.put(LocalFlag.ECHOE,   0x00000010L);
            LOCAL_FLAG_BITS.put(LocalFlag.ECHOK,   0x00000020L);
            LOCAL_FLAG_BITS.put(LocalFlag.ECHONL,  0x00000040L);
            LOCAL_FLAG_BITS.put(LocalFlag.NOFLSH,  0x00000080L);
            LOCAL_FLAG_BITS.put(LocalFlag.TOSTOP,  0x00000100L);
            LOCAL_FLAG_BITS.put(LocalFlag.ECHOCTL, 0x00000200L);
            LOCAL_FLAG_BITS.put(LocalFlag.ECHOPRT, 0x00000400L);
            LOCAL_FLAG_BITS.put(LocalFlag.ECHOKE,  0x00000800L);
            LOCAL_FLAG_BITS.put(LocalFlag.FLUSHO,  0x00001000L);
            LOCAL_FLAG_BITS.put(LocalFlag.PENDIN,  0x00004000L);
            LOCAL_FLAG_BITS.put(LocalFlag.IEXTEN,  0x00008000L);
            LOCAL_FLAG_BITS.put(LocalFlag.EXTPROC, 0x00010000L);
            // ALTWERASE, NOKERNINFO: not on Linux
        }
    }

    // =========================================================================
    // ControlChar c_cc indices
    // =========================================================================

    private static final EnumMap<ControlChar, Integer> CC_INDEX = new EnumMap<>(ControlChar.class);
    static {
        if (IS_MACOS) {
            CC_INDEX.put(ControlChar.VEOF,     0);
            CC_INDEX.put(ControlChar.VEOL,     1);
            CC_INDEX.put(ControlChar.VEOL2,    2);
            CC_INDEX.put(ControlChar.VERASE,   3);
            CC_INDEX.put(ControlChar.VWERASE,  4);
            CC_INDEX.put(ControlChar.VKILL,    5);
            CC_INDEX.put(ControlChar.VREPRINT, 6);
            CC_INDEX.put(ControlChar.VINTR,    8);
            CC_INDEX.put(ControlChar.VQUIT,    9);
            CC_INDEX.put(ControlChar.VSUSP,   10);
            CC_INDEX.put(ControlChar.VDSUSP,  11);
            CC_INDEX.put(ControlChar.VSTART,  12);
            CC_INDEX.put(ControlChar.VSTOP,   13);
            CC_INDEX.put(ControlChar.VLNEXT,  14);
            CC_INDEX.put(ControlChar.VDISCARD,15);
            CC_INDEX.put(ControlChar.VMIN,    16);
            CC_INDEX.put(ControlChar.VTIME,   17);
            CC_INDEX.put(ControlChar.VSTATUS, 18);
        } else {
            CC_INDEX.put(ControlChar.VINTR,    0);
            CC_INDEX.put(ControlChar.VQUIT,    1);
            CC_INDEX.put(ControlChar.VERASE,   2);
            CC_INDEX.put(ControlChar.VKILL,    3);
            CC_INDEX.put(ControlChar.VEOF,     4);
            CC_INDEX.put(ControlChar.VTIME,    5);
            CC_INDEX.put(ControlChar.VMIN,     6);
            CC_INDEX.put(ControlChar.VSTART,   8);
            CC_INDEX.put(ControlChar.VSTOP,    9);
            CC_INDEX.put(ControlChar.VSUSP,   10);
            CC_INDEX.put(ControlChar.VEOL,    11);
            CC_INDEX.put(ControlChar.VREPRINT,12);
            CC_INDEX.put(ControlChar.VDISCARD,13);
            CC_INDEX.put(ControlChar.VWERASE, 14);
            CC_INDEX.put(ControlChar.VLNEXT,  15);
            CC_INDEX.put(ControlChar.VEOL2,   16);
            // VDSUSP, VSTATUS: not on Linux
        }
    }

    // =========================================================================
    // Public lookup methods
    // =========================================================================

    /**
     * Returns the platform-specific bitmask for the given InputFlag, or -1 if
     * the flag does not exist on this platform.
     */
    static long inputFlagBit(InputFlag flag) {
        Long bit = INPUT_FLAG_BITS.get(flag);
        return bit != null ? bit : -1L;
    }

    /**
     * Returns the platform-specific bitmask for the given OutputFlag, or -1 if
     * the flag does not exist on this platform.
     */
    static long outputFlagBit(OutputFlag flag) {
        Long bit = OUTPUT_FLAG_BITS.get(flag);
        return bit != null ? bit : -1L;
    }

    /**
     * Returns the platform-specific bitmask for the given ControlFlag, or -1 if
     * the flag does not exist on this platform.
     */
    static long controlFlagBit(ControlFlag flag) {
        Long bit = CONTROL_FLAG_BITS.get(flag);
        return bit != null ? bit : -1L;
    }

    /**
     * Returns the platform-specific bitmask for the given LocalFlag, or -1 if
     * the flag does not exist on this platform.
     */
    static long localFlagBit(LocalFlag flag) {
        Long bit = LOCAL_FLAG_BITS.get(flag);
        return bit != null ? bit : -1L;
    }

    /**
     * Returns the c_cc array index for the given ControlChar, or -1 if
     * it does not exist on this platform.
     */
    static int ccIndex(ControlChar cc) {
        Integer idx = CC_INDEX.get(cc);
        return idx != null ? idx : -1;
    }

    private PosixConstants() {
    }
}
