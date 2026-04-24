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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for the JNI native library on Windows.
 * These tests are skipped on non-Windows platforms.
 */
public class WinConsoleNativeTest {

    @Before
    public void windowsOnly() {
        Assume.assumeTrue("Windows only", System.getProperty("os.name", "").toLowerCase().contains("win"));
        try {
            // Force class loading which triggers native library load
            Class.forName("org.aesh.terminal.tty.impl.WinConsoleNative");
        } catch (UnsatisfiedLinkError | ExceptionInInitializerError | NoClassDefFoundError e) {
            // Class.forName wraps static-init failures in ExceptionInInitializerError;
            // subsequent attempts throw NoClassDefFoundError
            Assume.assumeNoException("Native library not available", e);
        } catch (ClassNotFoundException e) {
            Assume.assumeNoException("WinConsoleNative class not found", e);
        }
    }

    @Test
    public void testGetConsoleOutputCP() {
        int cp = WinConsoleNative.getConsoleOutputCP();
        assertTrue("Code page should be positive, got: " + cp, cp > 0);
    }

    @Test
    public void testGetStdHandleInput() {
        long handle = WinConsoleNative.getStdHandle(WinConsoleNative.STD_INPUT_HANDLE);
        // In CI the handle may be invalid (pipe), but should not crash
        assertNotEquals("Handle should not be zero", 0L, handle);
    }

    @Test
    public void testGetStdHandleOutput() {
        long handle = WinConsoleNative.getStdHandle(WinConsoleNative.STD_OUTPUT_HANDLE);
        assertNotEquals("Handle should not be zero", 0L, handle);
    }

    @Test
    public void testGetConsoleModeGracefulOnInvalidHandle() {
        // Should return -1 on an invalid handle, not crash
        int mode = WinConsoleNative.getConsoleMode(WinConsoleNative.INVALID_HANDLE);
        assertEquals("Should return -1 for invalid handle", -1, mode);
    }

    @Test
    public void testSetConsoleModeGracefulOnInvalidHandle() {
        // Should return false on an invalid handle, not crash
        boolean result = WinConsoleNative.setConsoleMode(WinConsoleNative.INVALID_HANDLE, 0);
        assertFalse("Should return false for invalid handle", result);
    }

    @Test
    public void testGetConsoleSizeGracefulOnInvalidHandle() {
        // Should return null on an invalid handle, not crash
        int[] size = WinConsoleNative.getConsoleSize(WinConsoleNative.INVALID_HANDLE);
        // null is acceptable for invalid handle
        assertNull("Should return null for invalid handle", size);
    }

    @Test
    public void testWriteConsoleGracefulOnInvalidHandle() {
        // Should return false on an invalid handle, not crash
        boolean result = WinConsoleNative.writeConsole(WinConsoleNative.INVALID_HANDLE, new char[] { 'x' }, 1);
        assertFalse("Should return false for invalid handle", result);
    }

    @Test
    public void testGetConsoleModeWithRealHandle() {
        long handle = WinConsoleNative.getStdHandle(WinConsoleNative.STD_INPUT_HANDLE);
        if (handle != WinConsoleNative.INVALID_HANDLE) {
            int mode = WinConsoleNative.getConsoleMode(handle);
            // In CI with pipes, mode may be -1; with a real console, it should be >= 0
            // Either way, the call should not crash
            assertTrue("Mode should be -1 (pipe) or >= 0 (console)", mode >= -1);
        }
    }

    @Test
    public void testGetConsoleSizeWithRealHandle() {
        long handle = WinConsoleNative.getStdHandle(WinConsoleNative.STD_OUTPUT_HANDLE);
        if (handle != WinConsoleNative.INVALID_HANDLE) {
            int[] size = WinConsoleNative.getConsoleSize(handle);
            // In CI, size may be null if stdout is a pipe
            if (size != null) {
                assertTrue("Width should be positive", size[0] > 0);
                assertTrue("Height should be positive", size[1] > 0);
            }
        }
    }
}
