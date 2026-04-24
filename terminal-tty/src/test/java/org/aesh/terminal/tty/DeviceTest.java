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
package org.aesh.terminal.tty;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.function.Consumer;

import org.aesh.terminal.Device;
import org.junit.Test;

/**
 * Tests for Device and DeviceBuilder functionality.
 * Tests that require readline dependencies are in the readline module.
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class DeviceTest {

    @Test
    public void testAnsiCapabilities() {
        Device device = DeviceBuilder.builder().name("ansi").build();

        assertNotNull(device.getStringCapability(Capability.enter_alt_charset_mode));

        assertTrue(device.getBooleanCapability(Capability.auto_right_margin));
        assertFalse(device.getBooleanCapability(Capability.auto_left_margin));

        assertEquals(8, device.getNumericCapability(Capability.max_colors).intValue());
        assertEquals(24, device.getNumericCapability(Capability.lines).intValue());

        assertEquals("^M", device.getStringCapability(Capability.carriage_return));

        ArrayList<int[]> out = new ArrayList<>();
        Consumer<int[]> capabilityConsumer = out::add;
        device.puts(capabilityConsumer, Capability.carriage_return);
        assertArrayEquals(new int[] { 13 }, out.get(0));

        //home
        assertArrayEquals(new int[] { 27, 91, 72 }, device.getStringCapabilityAsInts(Capability.key_home));
    }

    @Test
    public void testWindowsCapabilities() {
        Device device = DeviceBuilder.builder().name("windows").build();
        assertTrue(device.getBooleanCapability(Capability.move_standout_mode));
        assertEquals(8, device.getNumericCapability(Capability.max_colors).intValue());
        assertEquals(64, device.getNumericCapability(Capability.max_pairs).intValue());

        assertArrayEquals(new int[] { 10 }, device.getStringCapabilityAsInts(Capability.scroll_forward));
    }

    @Test
    public void testXTermCapabilities() {
        Device device = DeviceBuilder.builder().name("xterm-256color").build();
        String deviceCap = device.getStringCapability(Capability.enter_ca_mode);
        assertNotNull(deviceCap);
        // Translate the device capability like ANSI does
        ArrayList<int[]> out = new ArrayList<>();
        Consumer<int[]> capabilityConsumer = out::add;
        device.puts(capabilityConsumer, Capability.enter_ca_mode);
        // Also check the out variable against a hardcoded value \u001B[?1049h
        assertArrayEquals(new int[] { 27, 91, 63, 49, 48, 52, 57, 104 }, out.get(0));
    }

    @Test
    public void testScreen256Capabilities() {
        Device device = DeviceBuilder.builder().name("screen-256color").build();
        String deviceCap = device.getStringCapability(Capability.enter_ca_mode);
        assertNotNull(deviceCap);
        // Translate the device capability like ANSI does
        ArrayList<int[]> out = new ArrayList<>();
        Consumer<int[]> capabilityConsumer = out::add;
        device.puts(capabilityConsumer, Capability.enter_ca_mode);
        // Also check the out variable against a hardcoded value \u001B[?1049h
        assertArrayEquals(new int[] { 27, 91, 63, 49, 48, 52, 57, 104 }, out.get(0));
    }

    @Test
    public void testMovementCapabilities() {
        Device device = DeviceBuilder.builder().name("xterm-256color").build();

        String cup = device.getStringCapability(Capability.cursor_address);
        assertNotNull(cup);

        ArrayList<int[]> out = new ArrayList<>();

        Consumer<int[]> capabilityConsumer = out::add;
        device.puts(capabilityConsumer, Capability.cursor_address, 10, 20);

        // ESC [ 11 ; 21 H (parameters are 1-based, %i increments them)
        assertArrayEquals(new int[] { 27, 91, 49, 49, 59, 50, 49, 72 }, out.get(0));
    }
}
