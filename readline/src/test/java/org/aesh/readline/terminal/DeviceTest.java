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
package org.aesh.readline.terminal;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.aesh.readline.editing.EditMode;
import org.aesh.readline.editing.EditModeBuilder;
import org.aesh.readline.editing.Variable;
import org.aesh.readline.tty.terminal.TestReadlineConnection;
import org.aesh.terminal.Device;
import org.aesh.terminal.Key;
import org.aesh.terminal.tty.Capability;
import org.aesh.terminal.tty.DeviceBuilder;
import org.junit.Test;

/**
 * Tests for Device that require readline dependencies.
 * Pure Device/Capability tests are in terminal-tty module.
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class DeviceTest {

    @Test
    public void testEmacsKeyUpdates() {
        Device device = DeviceBuilder.builder().name("ansi").build();

        EditMode emacs = EditModeBuilder.builder()
                .addVariable(Variable.EDITING_MODE, "emacs")
                .device(device).build();

        //by default only Key.HOME is set to beginning-of-line, but the ansi
        //device should remap it to Key.HOME_2
        assertEquals("beginning-of-line", emacs.parse(Key.HOME_2).name());
    }

    @Test
    public void testCapabilityIsPushedToTestConnection() throws Exception {
        TestReadlineConnection connection = new TestReadlineConnection(false);
        connection.clearOutputBuffer();
        connection.put(Capability.enter_ca_mode);
        // ESC [ ? 1 0 4 9 h
        assertArrayEquals(new int[] { 27, 91, 63, 49, 48, 52, 57, 104 }, connection.getOutputBuffer().codePoints().toArray());
    }

}
