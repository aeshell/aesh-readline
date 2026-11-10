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
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * In-process terminal size read via {@code ioctl(TIOCGWINSZ)}.
 * <p>
 * Opens {@code /dev/tty} read-only, queries the window size, and
 * closes the descriptor. Passive: no raw mode, no mode flips, no
 * session. Returns null whenever the terminal cannot be measured.
 * <p>
 * Package-private by design: the versioned overlay must not add
 * public classes beyond the base API (MRJAR validation). Loaded
 * reflectively by {@link TerminalSizeQuery} so Java 8 runtimes
 * fall back to the stty path without any linkage failure.
 * <p>
 * Requires Java 22+ and {@code --enable-native-access=ALL-UNNAMED}.
 *
 * @since 3.18.8
 */
final class FfmTerminalSize {

    private FfmTerminalSize() {
    }

    /**
     * Query the controlling terminal size in-process.
     *
     * @return {@code [width, height]}, or null when unavailable
     */
    static int[] query() {
        if (!FfmPosix.IS_SUPPORTED_ABI) {
            return null;
        }
        if (!FfmPosix.isNativeAccessEnabled()) {
            return null;
        }
        try (Arena arena = Arena.ofConfined()) {
            int fd = FfmPosix.open("/dev/tty", FfmPosix.O_RDONLY, arena);
            if (fd < 0) {
                return null;
            }
            try {
                MemorySegment winsize = arena.allocate(
                        FfmPosix.WINSIZE_SIZE, 2);
                int rc;
                try {
                    rc = FfmPosix.ioctl(fd, FfmPosix.TIOCGWINSZ, winsize);
                } catch (IOException e) {
                    return null;
                }
                if (rc != 0) {
                    return null;
                }
                short rows = winsize.get(ValueLayout.JAVA_SHORT,
                        FfmPosix.WINSIZE_ROW_OFFSET);
                short cols = winsize.get(ValueLayout.JAVA_SHORT,
                        FfmPosix.WINSIZE_COL_OFFSET);
                if (rows <= 0 || cols <= 0) {
                    return null;
                }
                return new int[] { cols, rows };
            } finally {
                try {
                    FfmPosix.close(fd);
                } catch (IOException ignored) {
                }
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
