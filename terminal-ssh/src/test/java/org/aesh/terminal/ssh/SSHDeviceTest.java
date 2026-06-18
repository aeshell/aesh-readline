/*
 * JBoss, Home of Professional Open Source
 * Copyright 2017 Red Hat Inc. and/or its affiliates and other contributors
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
package org.aesh.terminal.ssh;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.aesh.terminal.tty.Capability;
import org.junit.Test;

/**
 * Tests for SSHDevice terminal type and capability reporting.
 */
public class SSHDeviceTest {

    @Test
    public void testXtermType() {
        SSHDevice device = new SSHDevice("xterm");
        assertEquals("xterm", device.type());
    }

    @Test
    public void testXterm256ColorType() {
        SSHDevice device = new SSHDevice("xterm-256color");
        assertEquals("xterm-256color", device.type());
    }

    @Test
    public void testVt100Type() {
        SSHDevice device = new SSHDevice("vt100");
        assertEquals("vt100", device.type());
    }

    @Test
    public void testXtermHasAutoRightMargin() {
        SSHDevice device = new SSHDevice("xterm");
        assertTrue(device.getBooleanCapability(Capability.auto_right_margin));
    }

    @Test
    public void testXtermHasCursorAddress() {
        SSHDevice device = new SSHDevice("xterm");
        String cup = device.getStringCapability(Capability.cursor_address);
        assertNotNull("xterm should have cursor_address capability", cup);
    }

    @Test
    public void testXtermHasColumns() {
        SSHDevice device = new SSHDevice("xterm");
        Integer cols = device.getNumericCapability(Capability.columns);
        assertNotNull("xterm should have columns capability", cols);
        assertEquals(Integer.valueOf(80), cols);
    }

    @Test
    public void testXtermMaxColors() {
        SSHDevice device = new SSHDevice("xterm");
        Integer maxColors = device.getNumericCapability(Capability.max_colors);
        assertNotNull("xterm should have max_colors", maxColors);
    }

    @Test
    public void testXterm256ColorHasMaxColors() {
        SSHDevice device = new SSHDevice("xterm-256color");
        Integer maxColors = device.getNumericCapability(Capability.max_colors);
        assertNotNull("xterm-256color should have max_colors", maxColors);
        assertEquals(Integer.valueOf(256), maxColors);
    }

    @Test
    public void testDumbTerminal() {
        SSHDevice device = new SSHDevice("dumb");
        assertEquals("dumb", device.type());
    }
}
