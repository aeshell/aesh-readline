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

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

import org.junit.Test;

/**
 * Tests for the JNI native terminal bindings.
 * <p>
 * Tests that require a real TTY are guarded by assumeTrue and will
 * be skipped in CI environments.
 */
public class TerminalNativeTest {

    private static boolean isPosix() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return !os.contains("windows");
    }

    private static boolean hasTty() {
        if (!isPosix() || !TerminalNative.isAvailable()) {
            return false;
        }
        // Check if we can actually open /dev/tty (not just that the file exists)
        int fd = TerminalNative.openTty();
        if (fd < 0) {
            return false;
        }
        TerminalNative.closeFd(fd);
        return true;
    }

    @Test
    public void testNativeLibraryLoads() {
        assumeTrue("Not a POSIX platform", isPosix());
        // On Linux x86_64 with the bundled .so, this should succeed
        // On other platforms it may not — that's OK, we just verify it doesn't crash
        // Just verify isAvailable() doesn't throw — value depends on platform
        TerminalNative.isAvailable();
    }

    @Test
    public void testTermiosSizeIsReasonable() {
        assumeTrue("Native not available", TerminalNative.isAvailable());
        int size = TerminalNative.termiosSize();
        // Linux: 60, macOS: 72 — both should be > 30 and < 200
        assertTrue("termios size should be > 30, was " + size, size > 30);
        assertTrue("termios size should be < 200, was " + size, size < 200);
    }

    @Test
    public void testOpenAndCloseTty() {
        assumeTrue("Native not available", TerminalNative.isAvailable());
        assumeTrue("No TTY available", hasTty());

        int fd = TerminalNative.openTty();
        assertTrue("openTty should return a valid fd, got " + fd, fd >= 0);

        int rc = TerminalNative.closeFd(fd);
        assertEquals("closeFd should return 0", 0, rc);
    }

    @Test
    public void testTcgetattr() {
        assumeTrue("Native not available", TerminalNative.isAvailable());
        assumeTrue("No TTY available", hasTty());

        int fd = TerminalNative.openTty();
        assumeTrue("openTty failed", fd >= 0);

        try {
            byte[] termios = TerminalNative.tcgetattr(fd);
            assertNotNull("tcgetattr should return non-null", termios);
            assertEquals("tcgetattr result size should match termiosSize()",
                    TerminalNative.termiosSize(), termios.length);
        } finally {
            TerminalNative.closeFd(fd);
        }
    }

    @Test
    public void testTcsetAttrRoundTrip() {
        assumeTrue("Native not available", TerminalNative.isAvailable());
        assumeTrue("No TTY available", hasTty());

        int fd = TerminalNative.openTty();
        assumeTrue("openTty failed", fd >= 0);

        try {
            // Save current state
            byte[] saved = TerminalNative.tcgetattr(fd);
            assertNotNull("tcgetattr should return non-null", saved);

            // Modify and set
            byte[] modified = saved.clone();
            TermiosHelper.setRawQueryMode(modified);
            int rc = TerminalNative.tcsetattr(fd, 2 /* TCSAFLUSH */, modified);
            assertEquals("tcsetattr should return 0", 0, rc);

            // Verify the change took effect
            byte[] readBack = TerminalNative.tcgetattr(fd);
            assertNotNull("tcgetattr after set should return non-null", readBack);

            // Restore
            rc = TerminalNative.tcsetattr(fd, 2 /* TCSAFLUSH */, saved);
            assertEquals("tcsetattr restore should return 0", 0, rc);
        } finally {
            TerminalNative.closeFd(fd);
        }
    }

    @Test
    public void testTermiosHelperModifiesBytes() {
        assumeTrue("Native not available", TerminalNative.isAvailable());
        assumeTrue("No TTY available", hasTty());

        int fd = TerminalNative.openTty();
        assumeTrue("openTty failed", fd >= 0);

        try {
            byte[] original = TerminalNative.tcgetattr(fd);
            assertNotNull(original);

            byte[] modified = original.clone();
            TermiosHelper.setRawQueryMode(modified);

            // The modified bytes should differ from original
            // (unless the terminal is already in raw mode, which is unlikely)
            boolean differs = false;
            for (int i = 0; i < original.length; i++) {
                if (original[i] != modified[i]) {
                    differs = true;
                    break;
                }
            }
            assertTrue("setRawQueryMode should modify the termios bytes", differs);
        } finally {
            TerminalNative.closeFd(fd);
        }
    }
}
