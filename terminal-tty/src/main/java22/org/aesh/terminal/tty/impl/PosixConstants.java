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

import java.util.Arrays;

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
    static final short POLLNVAL = 0x0020;

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
    // Cached enum value arrays (avoid cloning values() per getAttr/setAttr)
    // =========================================================================

    static final InputFlag[] INPUT_FLAGS = InputFlag.values();
    static final OutputFlag[] OUTPUT_FLAGS = OutputFlag.values();
    static final ControlFlag[] CONTROL_FLAGS = ControlFlag.values();
    static final LocalFlag[] LOCAL_FLAGS = LocalFlag.values();
    static final ControlChar[] CONTROL_CHARS = ControlChar.values();

    // =========================================================================
    // InputFlag bitmasks
    // =========================================================================

    private static final long[] INPUT_FLAG_BITS = new long[INPUT_FLAGS.length];
    static final long INPUT_FLAG_MASK;
    static {
        Arrays.fill(INPUT_FLAG_BITS, -1L);
        if (IS_MACOS) {
            setBit(INPUT_FLAG_BITS, InputFlag.IGNBRK,  0x00000001L);
            setBit(INPUT_FLAG_BITS, InputFlag.BRKINT,  0x00000002L);
            setBit(INPUT_FLAG_BITS, InputFlag.IGNPAR,  0x00000004L);
            setBit(INPUT_FLAG_BITS, InputFlag.PARMRK,  0x00000008L);
            setBit(INPUT_FLAG_BITS, InputFlag.INPCK,   0x00000010L);
            setBit(INPUT_FLAG_BITS, InputFlag.ISTRIP,  0x00000020L);
            setBit(INPUT_FLAG_BITS, InputFlag.INLCR,   0x00000040L);
            setBit(INPUT_FLAG_BITS, InputFlag.IGNCR,   0x00000080L);
            setBit(INPUT_FLAG_BITS, InputFlag.ICRNL,   0x00000100L);
            setBit(INPUT_FLAG_BITS, InputFlag.IXON,    0x00000200L);
            setBit(INPUT_FLAG_BITS, InputFlag.IXOFF,   0x00000400L);
            setBit(INPUT_FLAG_BITS, InputFlag.IXANY,   0x00000800L);
            setBit(INPUT_FLAG_BITS, InputFlag.IMAXBEL, 0x00002000L);
            setBit(INPUT_FLAG_BITS, InputFlag.IUTF8,   0x00004000L);
        } else {
            setBit(INPUT_FLAG_BITS, InputFlag.IGNBRK,  0x00000001L);
            setBit(INPUT_FLAG_BITS, InputFlag.BRKINT,  0x00000002L);
            setBit(INPUT_FLAG_BITS, InputFlag.IGNPAR,  0x00000004L);
            setBit(INPUT_FLAG_BITS, InputFlag.PARMRK,  0x00000008L);
            setBit(INPUT_FLAG_BITS, InputFlag.INPCK,   0x00000010L);
            setBit(INPUT_FLAG_BITS, InputFlag.ISTRIP,  0x00000020L);
            setBit(INPUT_FLAG_BITS, InputFlag.INLCR,   0x00000040L);
            setBit(INPUT_FLAG_BITS, InputFlag.IGNCR,   0x00000080L);
            setBit(INPUT_FLAG_BITS, InputFlag.ICRNL,   0x00000100L);
            setBit(INPUT_FLAG_BITS, InputFlag.IXON,    0x00000400L);
            setBit(INPUT_FLAG_BITS, InputFlag.IXOFF,   0x00001000L);
            setBit(INPUT_FLAG_BITS, InputFlag.IXANY,   0x00000800L);
            setBit(INPUT_FLAG_BITS, InputFlag.IMAXBEL, 0x00002000L);
            setBit(INPUT_FLAG_BITS, InputFlag.IUTF8,   0x00004000L);
        }
        INPUT_FLAG_MASK = computeMask(INPUT_FLAG_BITS);
    }

    // =========================================================================
    // OutputFlag bitmasks
    // =========================================================================

    private static final long[] OUTPUT_FLAG_BITS = new long[OUTPUT_FLAGS.length];
    static final long OUTPUT_FLAG_MASK;
    static {
        Arrays.fill(OUTPUT_FLAG_BITS, -1L);
        if (IS_MACOS) {
            setBit(OUTPUT_FLAG_BITS, OutputFlag.OPOST,  0x00000001L);
            setBit(OUTPUT_FLAG_BITS, OutputFlag.ONLCR,  0x00000002L);
            setBit(OUTPUT_FLAG_BITS, OutputFlag.OXTABS, 0x00000004L);
            setBit(OUTPUT_FLAG_BITS, OutputFlag.ONOEOT, 0x00000008L);
            setBit(OUTPUT_FLAG_BITS, OutputFlag.OCRNL,  0x00000010L);
            setBit(OUTPUT_FLAG_BITS, OutputFlag.ONOCR,  0x00000020L);
            setBit(OUTPUT_FLAG_BITS, OutputFlag.ONLRET, 0x00000040L);
            // NLDLY, CRDLY, FFDLY, BSDLY, VTDLY, TABDLY, OFILL, OFDEL: not on macOS
        } else {
            setBit(OUTPUT_FLAG_BITS, OutputFlag.OPOST,  0x00000001L);
            setBit(OUTPUT_FLAG_BITS, OutputFlag.ONLCR,  0x00000004L);
            setBit(OUTPUT_FLAG_BITS, OutputFlag.OCRNL,  0x00000008L);
            setBit(OUTPUT_FLAG_BITS, OutputFlag.ONOCR,  0x00000010L);
            setBit(OUTPUT_FLAG_BITS, OutputFlag.ONLRET, 0x00000020L);
            setBit(OUTPUT_FLAG_BITS, OutputFlag.OFILL,  0x00000040L);
            setBit(OUTPUT_FLAG_BITS, OutputFlag.OFDEL,  0x00000080L);
            setBit(OUTPUT_FLAG_BITS, OutputFlag.NLDLY,  0x00000100L);
            setBit(OUTPUT_FLAG_BITS, OutputFlag.CRDLY,  0x00000600L);
            setBit(OUTPUT_FLAG_BITS, OutputFlag.TABDLY, 0x00001800L);
            setBit(OUTPUT_FLAG_BITS, OutputFlag.BSDLY,  0x00002000L);
            setBit(OUTPUT_FLAG_BITS, OutputFlag.VTDLY,  0x00004000L);
            setBit(OUTPUT_FLAG_BITS, OutputFlag.FFDLY,  0x00008000L);
            // OXTABS and ONOEOT do not exist on Linux
        }
        OUTPUT_FLAG_MASK = computeMask(OUTPUT_FLAG_BITS);
    }

    // =========================================================================
    // ControlFlag bitmasks
    // =========================================================================

    // Bitmask for CSIZE field (char size bits that must be cleared before setting CS5-CS8)
    static final long CSIZE_MASK = IS_MACOS ? 0x00000300L : 0x00000030L;

    private static final long[] CONTROL_FLAG_BITS = new long[CONTROL_FLAGS.length];
    static final long CONTROL_FLAG_MASK;
    static {
        Arrays.fill(CONTROL_FLAG_BITS, -1L);
        if (IS_MACOS) {
            // CIGNORE does not have a direct termios mapping; skip
            setBit(CONTROL_FLAG_BITS, ControlFlag.CS5,         0x00000000L);
            setBit(CONTROL_FLAG_BITS, ControlFlag.CS6,         0x00000100L);
            setBit(CONTROL_FLAG_BITS, ControlFlag.CS7,         0x00000200L);
            setBit(CONTROL_FLAG_BITS, ControlFlag.CS8,         0x00000300L);
            setBit(CONTROL_FLAG_BITS, ControlFlag.CSTOPB,      0x00000400L);
            setBit(CONTROL_FLAG_BITS, ControlFlag.CREAD,       0x00000800L);
            setBit(CONTROL_FLAG_BITS, ControlFlag.PARENB,      0x00001000L);
            setBit(CONTROL_FLAG_BITS, ControlFlag.PARODD,      0x00002000L);
            setBit(CONTROL_FLAG_BITS, ControlFlag.HUPCL,       0x00004000L);
            setBit(CONTROL_FLAG_BITS, ControlFlag.CLOCAL,      0x00008000L);
            setBit(CONTROL_FLAG_BITS, ControlFlag.CCTS_OFLOW,  0x00010000L);
            setBit(CONTROL_FLAG_BITS, ControlFlag.CRTS_IFLOW,  0x00020000L);
            setBit(CONTROL_FLAG_BITS, ControlFlag.CDTR_IFLOW,  0x00040000L);
            setBit(CONTROL_FLAG_BITS, ControlFlag.CDSR_OFLOW,  0x00080000L);
            setBit(CONTROL_FLAG_BITS, ControlFlag.CCAR_OFLOW,  0x00100000L);
        } else {
            setBit(CONTROL_FLAG_BITS, ControlFlag.CS5,         0x00000000L);
            setBit(CONTROL_FLAG_BITS, ControlFlag.CS6,         0x00000010L);
            setBit(CONTROL_FLAG_BITS, ControlFlag.CS7,         0x00000020L);
            setBit(CONTROL_FLAG_BITS, ControlFlag.CS8,         0x00000030L);
            setBit(CONTROL_FLAG_BITS, ControlFlag.CSTOPB,      0x00000040L);
            setBit(CONTROL_FLAG_BITS, ControlFlag.CREAD,       0x00000080L);
            setBit(CONTROL_FLAG_BITS, ControlFlag.PARENB,      0x00000100L);
            setBit(CONTROL_FLAG_BITS, ControlFlag.PARODD,      0x00000200L);
            setBit(CONTROL_FLAG_BITS, ControlFlag.HUPCL,       0x00000400L);
            setBit(CONTROL_FLAG_BITS, ControlFlag.CLOCAL,      0x00000800L);
            // Linux uses CRTSCTS (0x80000000) for both CTS output and RTS input flow control.
            // Map CCTS_OFLOW and CRTS_IFLOW to the same bit; others don't exist.
            setBit(CONTROL_FLAG_BITS, ControlFlag.CCTS_OFLOW,  0x80000000L);
            setBit(CONTROL_FLAG_BITS, ControlFlag.CRTS_IFLOW,  0x80000000L);
            // CDTR_IFLOW, CDSR_OFLOW, CCAR_OFLOW: not on Linux
        }
        long cMask = CSIZE_MASK;
        for (ControlFlag flag : CONTROL_FLAGS) {
            if (flag == ControlFlag.CIGNORE || flag == ControlFlag.CS5 || flag == ControlFlag.CS6
                    || flag == ControlFlag.CS7 || flag == ControlFlag.CS8) {
                continue;
            }
            long bit = CONTROL_FLAG_BITS[flag.ordinal()];
            if (bit >= 0) {
                cMask |= bit;
            }
        }
        CONTROL_FLAG_MASK = cMask;
    }

    // =========================================================================
    // LocalFlag bitmasks
    // =========================================================================

    private static final long[] LOCAL_FLAG_BITS = new long[LOCAL_FLAGS.length];
    static final long LOCAL_FLAG_MASK;
    static {
        Arrays.fill(LOCAL_FLAG_BITS, -1L);
        if (IS_MACOS) {
            setBit(LOCAL_FLAG_BITS, LocalFlag.ECHOKE,     0x00000001L);
            setBit(LOCAL_FLAG_BITS, LocalFlag.ECHOE,      0x00000002L);
            setBit(LOCAL_FLAG_BITS, LocalFlag.ECHOK,      0x00000004L);
            setBit(LOCAL_FLAG_BITS, LocalFlag.ECHO,       0x00000008L);
            setBit(LOCAL_FLAG_BITS, LocalFlag.ECHONL,     0x00000010L);
            setBit(LOCAL_FLAG_BITS, LocalFlag.ECHOPRT,    0x00000020L);
            setBit(LOCAL_FLAG_BITS, LocalFlag.ECHOCTL,    0x00000040L);
            setBit(LOCAL_FLAG_BITS, LocalFlag.ISIG,       0x00000080L);
            setBit(LOCAL_FLAG_BITS, LocalFlag.ICANON,     0x00000100L);
            setBit(LOCAL_FLAG_BITS, LocalFlag.ALTWERASE,  0x00000200L);
            setBit(LOCAL_FLAG_BITS, LocalFlag.IEXTEN,     0x00000400L);
            setBit(LOCAL_FLAG_BITS, LocalFlag.EXTPROC,    0x00000800L);
            setBit(LOCAL_FLAG_BITS, LocalFlag.TOSTOP,     0x00400000L);
            setBit(LOCAL_FLAG_BITS, LocalFlag.FLUSHO,     0x00800000L);
            setBit(LOCAL_FLAG_BITS, LocalFlag.NOKERNINFO, 0x02000000L);
            setBit(LOCAL_FLAG_BITS, LocalFlag.PENDIN,     0x20000000L);
            setBit(LOCAL_FLAG_BITS, LocalFlag.NOFLSH,     0x80000000L);
        } else {
            setBit(LOCAL_FLAG_BITS, LocalFlag.ISIG,    0x00000001L);
            setBit(LOCAL_FLAG_BITS, LocalFlag.ICANON,  0x00000002L);
            setBit(LOCAL_FLAG_BITS, LocalFlag.ECHO,    0x00000008L);
            setBit(LOCAL_FLAG_BITS, LocalFlag.ECHOE,   0x00000010L);
            setBit(LOCAL_FLAG_BITS, LocalFlag.ECHOK,   0x00000020L);
            setBit(LOCAL_FLAG_BITS, LocalFlag.ECHONL,  0x00000040L);
            setBit(LOCAL_FLAG_BITS, LocalFlag.NOFLSH,  0x00000080L);
            setBit(LOCAL_FLAG_BITS, LocalFlag.TOSTOP,  0x00000100L);
            setBit(LOCAL_FLAG_BITS, LocalFlag.ECHOCTL, 0x00000200L);
            setBit(LOCAL_FLAG_BITS, LocalFlag.ECHOPRT, 0x00000400L);
            setBit(LOCAL_FLAG_BITS, LocalFlag.ECHOKE,  0x00000800L);
            setBit(LOCAL_FLAG_BITS, LocalFlag.FLUSHO,  0x00001000L);
            setBit(LOCAL_FLAG_BITS, LocalFlag.PENDIN,  0x00004000L);
            setBit(LOCAL_FLAG_BITS, LocalFlag.IEXTEN,  0x00008000L);
            setBit(LOCAL_FLAG_BITS, LocalFlag.EXTPROC, 0x00010000L);
            // ALTWERASE, NOKERNINFO: not on Linux
        }
        LOCAL_FLAG_MASK = computeMask(LOCAL_FLAG_BITS);
    }

    // =========================================================================
    // ControlChar c_cc indices
    // =========================================================================

    private static final int[] CC_INDEX = new int[CONTROL_CHARS.length];
    static {
        Arrays.fill(CC_INDEX, -1);
        if (IS_MACOS) {
            CC_INDEX[ControlChar.VEOF.ordinal()]     =  0;
            CC_INDEX[ControlChar.VEOL.ordinal()]     =  1;
            CC_INDEX[ControlChar.VEOL2.ordinal()]    =  2;
            CC_INDEX[ControlChar.VERASE.ordinal()]   =  3;
            CC_INDEX[ControlChar.VWERASE.ordinal()]  =  4;
            CC_INDEX[ControlChar.VKILL.ordinal()]    =  5;
            CC_INDEX[ControlChar.VREPRINT.ordinal()] =  6;
            CC_INDEX[ControlChar.VINTR.ordinal()]    =  8;
            CC_INDEX[ControlChar.VQUIT.ordinal()]    =  9;
            CC_INDEX[ControlChar.VSUSP.ordinal()]    = 10;
            CC_INDEX[ControlChar.VDSUSP.ordinal()]   = 11;
            CC_INDEX[ControlChar.VSTART.ordinal()]   = 12;
            CC_INDEX[ControlChar.VSTOP.ordinal()]    = 13;
            CC_INDEX[ControlChar.VLNEXT.ordinal()]   = 14;
            CC_INDEX[ControlChar.VDISCARD.ordinal()] = 15;
            CC_INDEX[ControlChar.VMIN.ordinal()]     = 16;
            CC_INDEX[ControlChar.VTIME.ordinal()]    = 17;
            CC_INDEX[ControlChar.VSTATUS.ordinal()]  = 18;
        } else {
            CC_INDEX[ControlChar.VINTR.ordinal()]    =  0;
            CC_INDEX[ControlChar.VQUIT.ordinal()]    =  1;
            CC_INDEX[ControlChar.VERASE.ordinal()]   =  2;
            CC_INDEX[ControlChar.VKILL.ordinal()]    =  3;
            CC_INDEX[ControlChar.VEOF.ordinal()]     =  4;
            CC_INDEX[ControlChar.VTIME.ordinal()]    =  5;
            CC_INDEX[ControlChar.VMIN.ordinal()]     =  6;
            CC_INDEX[ControlChar.VSTART.ordinal()]   =  8;
            CC_INDEX[ControlChar.VSTOP.ordinal()]    =  9;
            CC_INDEX[ControlChar.VSUSP.ordinal()]    = 10;
            CC_INDEX[ControlChar.VEOL.ordinal()]     = 11;
            CC_INDEX[ControlChar.VREPRINT.ordinal()] = 12;
            CC_INDEX[ControlChar.VDISCARD.ordinal()] = 13;
            CC_INDEX[ControlChar.VWERASE.ordinal()]  = 14;
            CC_INDEX[ControlChar.VLNEXT.ordinal()]   = 15;
            CC_INDEX[ControlChar.VEOL2.ordinal()]    = 16;
            // VDSUSP, VSTATUS: not on Linux
        }
    }

    private static void setBit(long[] bits, Enum<?> flag, long bit) {
        bits[flag.ordinal()] = bit;
    }

    private static long computeMask(long[] bits) {
        long mask = 0;
        for (long bit : bits) {
            if (bit >= 0) {
                mask |= bit;
            }
        }
        return mask;
    }

    // =========================================================================
    // Public lookup methods
    // =========================================================================

    /**
     * Returns the platform-specific bitmask for the given InputFlag, or -1 if
     * the flag does not exist on this platform.
     */
    static long inputFlagBit(InputFlag flag) {
        return INPUT_FLAG_BITS[flag.ordinal()];
    }

    /**
     * Returns the platform-specific bitmask for the given OutputFlag, or -1 if
     * the flag does not exist on this platform.
     */
    static long outputFlagBit(OutputFlag flag) {
        return OUTPUT_FLAG_BITS[flag.ordinal()];
    }

    /**
     * Returns the platform-specific bitmask for the given ControlFlag, or -1 if
     * the flag does not exist on this platform.
     */
    static long controlFlagBit(ControlFlag flag) {
        return CONTROL_FLAG_BITS[flag.ordinal()];
    }

    /**
     * Returns the platform-specific bitmask for the given LocalFlag, or -1 if
     * the flag does not exist on this platform.
     */
    static long localFlagBit(LocalFlag flag) {
        return LOCAL_FLAG_BITS[flag.ordinal()];
    }

    /**
     * Returns the c_cc array index for the given ControlChar, or -1 if
     * it does not exist on this platform.
     */
    static int ccIndex(ControlChar cc) {
        return CC_INDEX[cc.ordinal()];
    }

    private PosixConstants() {
    }
}
