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
package org.aesh.terminal.utils;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.aesh.terminal.tty.Capability;
import org.junit.Test;

/**
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class InfoCmpTest {

    @Test
    public void testANSI() {
        Set<Capability> bools = new HashSet<>();
        Map<Capability, Integer> ints = new HashMap<>();
        Map<Capability, String> strings = new HashMap<>();

        String infocmp = InfoCmp.getDefaultInfoCmp("xterm");
        InfoCmp.parseInfoCmp(infocmp, bools, ints, strings);
        assertEquals(24, ints.get(Capability.lines).intValue());
        assertEquals(80, ints.get(Capability.columns).intValue());

        assertEquals(5, ints.size());
        assertEquals(8, bools.size());
        assertEquals(166, strings.size());
        assertTrue(strings.containsKey(Capability.byName("kf29")));
    }

    @Test
    public void testWindows() {
        Set<Capability> bools = new HashSet<>();
        Map<Capability, Integer> ints = new HashMap<>();
        Map<Capability, String> strings = new HashMap<>();

        String infocmp = InfoCmp.getDefaultInfoCmp("Windows");
        InfoCmp.parseInfoCmp(infocmp, bools, ints, strings);
        assertEquals(24, ints.get(Capability.lines).intValue());
        assertEquals(64, ints.get(Capability.max_pairs).intValue());

        assertEquals(6, ints.size());
        assertEquals(4, bools.size());
        assertEquals(58, strings.size());
        assertTrue(strings.containsKey(Capability.byName("smso")));
    }

    @Test
    public void testXterm256Color() {
        Set<Capability> bools = new HashSet<>();
        Map<Capability, Integer> ints = new HashMap<>();
        Map<Capability, String> strings = new HashMap<>();

        String infocmp = InfoCmp.getDefaultInfoCmp("xterm-256color");
        assertNotNull("xterm-256color caps should not be null", infocmp);
        InfoCmp.parseInfoCmp(infocmp, bools, ints, strings);

        assertEquals(256, ints.get(Capability.max_colors).intValue());
        assertTrue(bools.contains(Capability.auto_right_margin));
        assertTrue(bools.contains(Capability.back_color_erase));
        assertNotNull(strings.get(Capability.cursor_address));
        assertNotNull(strings.get(Capability.enter_ca_mode)); // smcup
        assertNotNull(strings.get(Capability.exit_ca_mode)); // rmcup
        assertNotNull(strings.get(Capability.set_a_foreground)); // setaf
        assertNotNull(strings.get(Capability.set_a_background)); // setab
    }

    @Test
    public void testTmux256Color() {
        Set<Capability> bools = new HashSet<>();
        Map<Capability, Integer> ints = new HashMap<>();
        Map<Capability, String> strings = new HashMap<>();

        String infocmp = InfoCmp.getDefaultInfoCmp("tmux-256color");
        assertNotNull("tmux-256color caps should not be null", infocmp);
        InfoCmp.parseInfoCmp(infocmp, bools, ints, strings);

        assertEquals(256, ints.get(Capability.max_colors).intValue());
        assertTrue(bools.contains(Capability.auto_right_margin));
        assertNotNull(strings.get(Capability.cursor_address));
        assertNotNull(strings.get(Capability.enter_ca_mode));
        assertNotNull(strings.get(Capability.exit_ca_mode));
    }

    @Test
    public void testLinux() {
        Set<Capability> bools = new HashSet<>();
        Map<Capability, Integer> ints = new HashMap<>();
        Map<Capability, String> strings = new HashMap<>();

        String infocmp = InfoCmp.getDefaultInfoCmp("linux");
        assertNotNull("linux caps should not be null", infocmp);
        InfoCmp.parseInfoCmp(infocmp, bools, ints, strings);

        assertEquals(8, ints.get(Capability.max_colors).intValue());
        assertTrue(bools.contains(Capability.auto_right_margin));
        assertNotNull(strings.get(Capability.cursor_address));
        assertNotNull(strings.get(Capability.clear_screen));
    }

    @Test
    public void testAlacritty() {
        Set<Capability> bools = new HashSet<>();
        Map<Capability, Integer> ints = new HashMap<>();
        Map<Capability, String> strings = new HashMap<>();

        String infocmp = InfoCmp.getDefaultInfoCmp("alacritty");
        assertNotNull("alacritty caps should not be null", infocmp);
        InfoCmp.parseInfoCmp(infocmp, bools, ints, strings);

        assertEquals(256, ints.get(Capability.max_colors).intValue());
        assertTrue(bools.contains(Capability.auto_right_margin));
        assertTrue(bools.contains(Capability.back_color_erase));
        assertNotNull(strings.get(Capability.cursor_address));
        assertNotNull(strings.get(Capability.enter_ca_mode));
        assertNotNull(strings.get(Capability.exit_ca_mode));
    }

    @Test
    public void testXtermKitty() {
        Set<Capability> bools = new HashSet<>();
        Map<Capability, Integer> ints = new HashMap<>();
        Map<Capability, String> strings = new HashMap<>();

        String infocmp = InfoCmp.getDefaultInfoCmp("xterm-kitty");
        assertNotNull("xterm-kitty caps should not be null", infocmp);
        InfoCmp.parseInfoCmp(infocmp, bools, ints, strings);

        assertEquals(256, ints.get(Capability.max_colors).intValue());
        assertTrue(bools.contains(Capability.auto_right_margin));
        assertNotNull(strings.get(Capability.cursor_address));
        assertNotNull(strings.get(Capability.enter_ca_mode));
        assertNotNull(strings.get(Capability.exit_ca_mode));
    }

    @Test
    public void testScreenColor() {
        Set<Capability> bools = new HashSet<>();
        Map<Capability, Integer> ints = new HashMap<>();
        Map<Capability, String> strings = new HashMap<>();

        String infocmp = InfoCmp.getDefaultInfoCmp("screen-256color");
        assertNotNull("screen-256color caps should not be null", infocmp);
        InfoCmp.parseInfoCmp(infocmp, bools, ints, strings);

        assertEquals(256, ints.get(Capability.max_colors).intValue());
        assertNotNull(strings.get(Capability.cursor_address));
        assertNotNull(strings.get(Capability.enter_ca_mode));
    }

    @Test
    public void testFallbackToDefault() {
        // Unknown terminal type should fall back to ansi
        Set<Capability> bools = new HashSet<>();
        Map<Capability, Integer> ints = new HashMap<>();
        Map<Capability, String> strings = new HashMap<>();

        String infocmp = InfoCmp.getDefaultInfoCmp("some-unknown-terminal");
        assertNotNull("Unknown terminal should fall back to ansi", infocmp);
        InfoCmp.parseInfoCmp(infocmp, bools, ints, strings);

        // ansi should have basic capabilities
        assertTrue(ints.containsKey(Capability.max_colors));
        assertNotNull(strings.get(Capability.cursor_address));
    }

    @Test
    public void testGetInfoCompFallsBackToDefault() throws IOException, InterruptedException {
        // getInfoCmp should return bundled caps even without infocmp binary
        String caps = InfoCmp.getInfoCmp("xterm-256color");
        assertNotNull("getInfoCmp should return caps for xterm-256color", caps);
        assertFalse("caps should not be empty", caps.isEmpty());
    }

    @Test
    public void testNative() throws IOException, InterruptedException {
        if (Config.isOSPOSIXCompatible()) {
            Set<Capability> bools = new HashSet<>();
            Map<Capability, Integer> ints = new HashMap<>();
            Map<Capability, String> strings = new HashMap<>();

            String infocmp = InfoCmp.getInfoCmp("xterm-256color");
            if (infocmp != null) {
                InfoCmp.parseInfoCmp(infocmp, bools, ints, strings);

                assertEquals(256, ints.get(Capability.max_colors).intValue());
                assertTrue(ints.get(Capability.columns) > 0);
                assertTrue(ints.get(Capability.lines) > 0);
                assertTrue(!bools.isEmpty());
                assertTrue(!strings.isEmpty());
                assertNotNull(strings.get(Capability.byName("smcup")));
            }
        }
    }

}
