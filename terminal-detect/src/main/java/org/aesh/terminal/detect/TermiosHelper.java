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

/**
 * Platform-specific termios struct manipulation for the JNI path.
 * <p>
 * Modifies the raw termios byte array in place to set raw mode for
 * terminal queries: clears ECHO, ICANON, IXON and sets VMIN=0, VTIME=5.
 */
final class TermiosHelper {

    private static final boolean IS_MACOS;
    static {
        String osName = System.getProperty("os.name", "").toLowerCase();
        IS_MACOS = osName.startsWith("mac") || osName.contains("darwin");
    }

    // Flag field sizes and offsets
    private static final int FLAG_SIZE = IS_MACOS ? 8 : 4;
    private static final int LFLAG_OFFSET = IS_MACOS ? 24 : 12;
    private static final int IFLAG_OFFSET = 0;
    private static final int CC_OFFSET = IS_MACOS ? 32 : 17;

    // Local flag bitmasks
    private static final long ECHO = 0x00000008L; // same on both platforms
    private static final long ICANON = IS_MACOS ? 0x00000100L : 0x00000002L;

    // Input flag bitmasks
    private static final long IXON = IS_MACOS ? 0x00000200L : 0x00000400L;

    // c_cc indices
    private static final int VMIN_INDEX = IS_MACOS ? 16 : 6;
    private static final int VTIME_INDEX = IS_MACOS ? 17 : 5;

    /**
     * Modifies a raw termios byte array in place to set raw query mode:
     * -echo -icanon -ixon min 0 time 5
     *
     * @param termios the raw termios bytes (modified in place)
     */
    static void setRawQueryMode(byte[] termios) {
        // Clear ECHO and ICANON in c_lflag
        long lflag = getFlag(termios, LFLAG_OFFSET);
        lflag &= ~(ECHO | ICANON);
        setFlag(termios, LFLAG_OFFSET, lflag);

        // Clear IXON in c_iflag
        long iflag = getFlag(termios, IFLAG_OFFSET);
        iflag &= ~IXON;
        setFlag(termios, IFLAG_OFFSET, iflag);

        // Set VMIN=0, VTIME=5 (500ms timeout)
        termios[CC_OFFSET + VMIN_INDEX] = 0;
        termios[CC_OFFSET + VTIME_INDEX] = 5;
    }

    private static long getFlag(byte[] buf, int offset) {
        if (FLAG_SIZE == 8) {
            // Little-endian 8-byte read (macOS)
            return (buf[offset] & 0xFFL)
                    | ((buf[offset + 1] & 0xFFL) << 8)
                    | ((buf[offset + 2] & 0xFFL) << 16)
                    | ((buf[offset + 3] & 0xFFL) << 24)
                    | ((buf[offset + 4] & 0xFFL) << 32)
                    | ((buf[offset + 5] & 0xFFL) << 40)
                    | ((buf[offset + 6] & 0xFFL) << 48)
                    | ((buf[offset + 7] & 0xFFL) << 56);
        } else {
            // Little-endian 4-byte read (Linux)
            return (buf[offset] & 0xFFL)
                    | ((buf[offset + 1] & 0xFFL) << 8)
                    | ((buf[offset + 2] & 0xFFL) << 16)
                    | ((buf[offset + 3] & 0xFFL) << 24);
        }
    }

    private static void setFlag(byte[] buf, int offset, long value) {
        if (FLAG_SIZE == 8) {
            // Little-endian 8-byte write (macOS)
            buf[offset] = (byte) (value);
            buf[offset + 1] = (byte) (value >> 8);
            buf[offset + 2] = (byte) (value >> 16);
            buf[offset + 3] = (byte) (value >> 24);
            buf[offset + 4] = (byte) (value >> 32);
            buf[offset + 5] = (byte) (value >> 40);
            buf[offset + 6] = (byte) (value >> 48);
            buf[offset + 7] = (byte) (value >> 56);
        } else {
            // Little-endian 4-byte write (Linux)
            buf[offset] = (byte) (value);
            buf[offset + 1] = (byte) (value >> 8);
            buf[offset + 2] = (byte) (value >> 16);
            buf[offset + 3] = (byte) (value >> 24);
        }
    }

    private TermiosHelper() {
    }
}
