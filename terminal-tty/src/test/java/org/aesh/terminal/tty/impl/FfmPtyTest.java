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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.io.IOException;

import org.aesh.terminal.Attributes;
import org.aesh.terminal.Attributes.ControlChar;
import org.aesh.terminal.Attributes.ControlFlag;
import org.aesh.terminal.Attributes.LocalFlag;
import org.aesh.terminal.tty.Size;
import org.aesh.terminal.utils.Config;
import org.junit.Test;

/**
 * Tests for the FFM-based Pty implementation.
 * <p>
 * Tests that require a real TTY (getAttr, setAttr, getSize) are guarded by
 * {@code assumeTrue(hasTty())} and will be skipped in CI environments
 * where no terminal is available.
 * <p>
 * The attribute tests verify that FfmPty produces sane values (known
 * Attributes) are the primary safety net for platform-specific bitmask
 * correctness.
 */
public class FfmPtyTest {

    /**
     * Checks if FfmPty is available (Java 22+ and POSIX).
     */
    private static boolean isFfmAvailable() {
        if (!Config.isOSPOSIXCompatible()) {
            return false;
        }
        try {
            Class.forName("org.aesh.terminal.tty.impl.FfmPty");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Checks if a TTY is available for live terminal tests.
     */
    private static boolean hasTty() {
        if (!isFfmAvailable()) {
            return false;
        }
        try {
            Pty pty = createFfmPty();
            pty.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static Pty createFfmPty() throws Exception {
        Class<?> clazz = Class.forName("org.aesh.terminal.tty.impl.FfmPty");
        return (Pty) clazz.getMethod("current").invoke(null);
    }

    @Test
    public void testFfmPtyClassLoading() {
        assumeTrue("FFM not available (Java < 22 or non-POSIX)", isFfmAvailable());
        try {
            Class<?> clazz = Class.forName("org.aesh.terminal.tty.impl.FfmPty");
            assertNotNull("FfmPty class should be loadable", clazz);
        } catch (ClassNotFoundException e) {
            fail("FfmPty should be available on Java 22+");
        }
    }

    @Test
    public void testGetAttr() throws Exception {
        assumeTrue("No TTY available", hasTty());
        try (Pty pty = createFfmPty()) {
            Attributes attr = pty.getAttr();
            assertNotNull("Attributes should not be null", attr);
            // A normal terminal should have at least some flags set
            assertNotNull("Input flags should not be null", attr.getInputFlags());
            assertNotNull("Output flags should not be null", attr.getOutputFlags());
            assertNotNull("Control flags should not be null", attr.getControlFlags());
            assertNotNull("Local flags should not be null", attr.getLocalFlags());
        }
    }

    @Test
    public void testGetSize() throws Exception {
        assumeTrue("No TTY available", hasTty());
        try (Pty pty = createFfmPty()) {
            Size size = pty.getSize();
            assertNotNull("Size should not be null", size);
            assertTrue("Width should be > 0, was " + size.getWidth(), size.getWidth() > 0);
            assertTrue("Height should be > 0, was " + size.getHeight(), size.getHeight() > 0);
        }
    }

    @Test
    public void testSetAttrRoundTrip() throws Exception {
        assumeTrue("No TTY available", hasTty());
        try (Pty pty = createFfmPty()) {
            // Read current attributes
            Attributes original = pty.getAttr();

            // Modify something — toggle ECHO
            Attributes modified = new Attributes(original);
            boolean wasEcho = modified.getLocalFlag(LocalFlag.ECHO);
            modified.setLocalFlag(LocalFlag.ECHO, !wasEcho);

            // Set modified attributes
            pty.setAttr(modified);

            // Read back
            Attributes readBack = pty.getAttr();
            assertEquals("ECHO flag should have been toggled",
                    !wasEcho, readBack.getLocalFlag(LocalFlag.ECHO));

            // Restore original (close() will also restore, but let's be explicit)
            pty.setAttr(original);

            // Verify restoration
            Attributes restored = pty.getAttr();
            assertEquals("ECHO flag should be restored",
                    wasEcho, restored.getLocalFlag(LocalFlag.ECHO));
        }
    }

    /**
     * Verify that FFM attributes have sane default values.
     * Standard POSIX terminals should have known control character defaults.
     */
    @Test
    public void testAttributesSanity() throws Exception {
        assumeTrue("No TTY available", hasTty());
        Pty ffmPty = null;
        try {
            ffmPty = createFfmPty();
            Attributes attr = ffmPty.getAttr();

            // Standard control characters should have known defaults
            int vintr = attr.getControlChar(ControlChar.VINTR);
            assertTrue("VINTR should be set (typically 3/Ctrl+C)", vintr > 0);

            int veof = attr.getControlChar(ControlChar.VEOF);
            assertTrue("VEOF should be set (typically 4/Ctrl+D)", veof > 0);

            int vsusp = attr.getControlChar(ControlChar.VSUSP);
            assertTrue("VSUSP should be set (typically 26/Ctrl+Z)", vsusp > 0);

            // At least some flags should be set in a normal terminal
            assertFalse("InputFlags should not be empty",
                    attr.getInputFlags().isEmpty());
            assertFalse("OutputFlags should not be empty",
                    attr.getOutputFlags().isEmpty());
            assertFalse("LocalFlags should not be empty",
                    attr.getLocalFlags().isEmpty());

            // Control flags should include a character size setting
            boolean hasCharSize = attr.getControlFlag(ControlFlag.CS5)
                    || attr.getControlFlag(ControlFlag.CS6)
                    || attr.getControlFlag(ControlFlag.CS7)
                    || attr.getControlFlag(ControlFlag.CS8);
            assertTrue("Should have a character size flag set", hasCharSize);
        } finally {
            if (ffmPty != null) {
                try { ffmPty.close(); } catch (IOException e) { /* ignore */ }
            }
        }
    }

    /**
     * Verify that FFM terminal size returns reasonable values.
     */
    @Test
    public void testSizeSanity() throws Exception {
        assumeTrue("No TTY available", hasTty());
        Pty ffmPty = null;
        try {
            ffmPty = createFfmPty();
            Size size = ffmPty.getSize();

            assertTrue("Width should be positive, got: " + size.getWidth(),
                    size.getWidth() > 0);
            assertTrue("Height should be positive, got: " + size.getHeight(),
                    size.getHeight() > 0);
            assertTrue("Width should be reasonable (<= 500), got: " + size.getWidth(),
                    size.getWidth() <= 500);
            assertTrue("Height should be reasonable (<= 500), got: " + size.getHeight(),
                    size.getHeight() <= 500);
        } finally {
            if (ffmPty != null) {
                try { ffmPty.close(); } catch (IOException e) { /* ignore */ }
            }
        }
    }

    @Test
    public void testCloseIsIdempotent() throws Exception {
        assumeTrue("No TTY available", hasTty());
        Pty pty = createFfmPty();
        pty.close();
        // Second close should not throw
        pty.close();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetMasterInputThrows() throws Exception {
        assumeTrue("FFM not available", isFfmAvailable());
        assumeTrue("No TTY available", hasTty());
        try (Pty pty = createFfmPty()) {
            pty.getMasterInput();
        }
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetMasterOutputThrows() throws Exception {
        assumeTrue("FFM not available", isFfmAvailable());
        assumeTrue("No TTY available", hasTty());
        try (Pty pty = createFfmPty()) {
            pty.getMasterOutput();
        }
    }

    @Test
    public void testGetSlaveStreams() throws Exception {
        assumeTrue("No TTY available", hasTty());
        try (Pty pty = createFfmPty()) {
            assertNotNull("Slave input should not be null", pty.getSlaveInput());
            assertNotNull("Slave output should not be null", pty.getSlaveOutput());
        }
    }
}
