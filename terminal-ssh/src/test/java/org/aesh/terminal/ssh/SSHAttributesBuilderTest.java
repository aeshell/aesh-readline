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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import org.aesh.terminal.Attributes;
import org.apache.sshd.common.channel.PtyMode;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.StandardEnvironment;
import org.junit.Test;

/**
 * Tests for SSHAttributesBuilder PtyMode-to-Attributes mapping.
 */
public class SSHAttributesBuilderTest {

    private Environment createEnvironment(Map<PtyMode, Integer> ptyModes) {
        StandardEnvironment env = new StandardEnvironment();
        env.getPtyModes().putAll(ptyModes);
        return env;
    }

    @Test
    public void testControlCharMappings() {
        Map<PtyMode, Integer> modes = new EnumMap<>(PtyMode.class);
        modes.put(PtyMode.VINTR, 3); // Ctrl-C
        modes.put(PtyMode.VEOF, 4); // Ctrl-D
        modes.put(PtyMode.VSUSP, 26); // Ctrl-Z
        modes.put(PtyMode.VQUIT, 28); // Ctrl-\
        modes.put(PtyMode.VERASE, 127); // DEL
        modes.put(PtyMode.VKILL, 21); // Ctrl-U

        Attributes attrs = SSHAttributesBuilder.builder()
                .environment(createEnvironment(modes))
                .build();

        assertEquals(3, attrs.getControlChar(Attributes.ControlChar.VINTR));
        assertEquals(4, attrs.getControlChar(Attributes.ControlChar.VEOF));
        assertEquals(26, attrs.getControlChar(Attributes.ControlChar.VSUSP));
        assertEquals(28, attrs.getControlChar(Attributes.ControlChar.VQUIT));
        assertEquals(127, attrs.getControlChar(Attributes.ControlChar.VERASE));
        assertEquals(21, attrs.getControlChar(Attributes.ControlChar.VKILL));
    }

    @Test
    public void testLocalFlagMappings() {
        Map<PtyMode, Integer> modes = new EnumMap<>(PtyMode.class);
        modes.put(PtyMode.ECHO, 1);
        modes.put(PtyMode.ICANON, 1);
        modes.put(PtyMode.ISIG, 0);

        Attributes attrs = SSHAttributesBuilder.builder()
                .environment(createEnvironment(modes))
                .build();

        assertTrue(attrs.getLocalFlag(Attributes.LocalFlag.ECHO));
        assertTrue(attrs.getLocalFlag(Attributes.LocalFlag.ICANON));
        assertFalse(attrs.getLocalFlag(Attributes.LocalFlag.ISIG));
    }

    @Test
    public void testInputFlagMappings() {
        Map<PtyMode, Integer> modes = new EnumMap<>(PtyMode.class);
        modes.put(PtyMode.ICRNL, 1);
        modes.put(PtyMode.INLCR, 0);
        modes.put(PtyMode.IGNCR, 0);

        Attributes attrs = SSHAttributesBuilder.builder()
                .environment(createEnvironment(modes))
                .build();

        assertTrue(attrs.getInputFlag(Attributes.InputFlag.ICRNL));
        assertFalse(attrs.getInputFlag(Attributes.InputFlag.INLCR));
        assertFalse(attrs.getInputFlag(Attributes.InputFlag.IGNCR));
    }

    @Test
    public void testOutputFlagMappings() {
        Map<PtyMode, Integer> modes = new EnumMap<>(PtyMode.class);
        modes.put(PtyMode.ONLCR, 1);
        modes.put(PtyMode.OPOST, 1);
        modes.put(PtyMode.OCRNL, 0);
        modes.put(PtyMode.ONLRET, 0);

        Attributes attrs = SSHAttributesBuilder.builder()
                .environment(createEnvironment(modes))
                .build();

        assertTrue(attrs.getOutputFlag(Attributes.OutputFlag.ONLCR));
        assertTrue(attrs.getOutputFlag(Attributes.OutputFlag.OPOST));
        assertFalse(attrs.getOutputFlag(Attributes.OutputFlag.OCRNL));
        assertFalse(attrs.getOutputFlag(Attributes.OutputFlag.ONLRET));
    }

    @Test
    public void testEmptyEnvironment() {
        Attributes attrs = SSHAttributesBuilder.builder()
                .environment(createEnvironment(Collections.emptyMap()))
                .build();

        // Should produce valid Attributes with defaults
        assertFalse(attrs.getLocalFlag(Attributes.LocalFlag.ECHO));
    }

    @Test
    public void testAllControlChars() {
        Map<PtyMode, Integer> modes = new EnumMap<>(PtyMode.class);
        modes.put(PtyMode.VEOL, 0);
        modes.put(PtyMode.VEOL2, 0);
        modes.put(PtyMode.VSTART, 17); // Ctrl-Q
        modes.put(PtyMode.VSTOP, 19); // Ctrl-S
        modes.put(PtyMode.VDSUSP, 25); // Ctrl-Y
        modes.put(PtyMode.VREPRINT, 18); // Ctrl-R
        modes.put(PtyMode.VWERASE, 23); // Ctrl-W
        modes.put(PtyMode.VLNEXT, 22); // Ctrl-V
        modes.put(PtyMode.VSTATUS, 20); // Ctrl-T
        modes.put(PtyMode.VDISCARD, 15); // Ctrl-O

        Attributes attrs = SSHAttributesBuilder.builder()
                .environment(createEnvironment(modes))
                .build();

        assertEquals(0, attrs.getControlChar(Attributes.ControlChar.VEOL));
        assertEquals(0, attrs.getControlChar(Attributes.ControlChar.VEOL2));
        assertEquals(17, attrs.getControlChar(Attributes.ControlChar.VSTART));
        assertEquals(19, attrs.getControlChar(Attributes.ControlChar.VSTOP));
        assertEquals(25, attrs.getControlChar(Attributes.ControlChar.VDSUSP));
        assertEquals(18, attrs.getControlChar(Attributes.ControlChar.VREPRINT));
        assertEquals(23, attrs.getControlChar(Attributes.ControlChar.VWERASE));
        assertEquals(22, attrs.getControlChar(Attributes.ControlChar.VLNEXT));
        assertEquals(20, attrs.getControlChar(Attributes.ControlChar.VSTATUS));
        assertEquals(15, attrs.getControlChar(Attributes.ControlChar.VDISCARD));
    }
}
