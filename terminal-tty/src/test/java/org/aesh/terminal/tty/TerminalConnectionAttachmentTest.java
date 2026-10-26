/*
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

import org.aesh.terminal.BaseDevice;
import org.aesh.terminal.Device;
import org.aesh.terminal.Terminal;
import org.aesh.terminal.detect.TerminalCapabilities;
import org.aesh.terminal.detect.TerminalDetection;
import org.aesh.terminal.detect.TerminalTheme;
import org.junit.Test;

/**
 * Connection attachment for local terminals (#381).
 * <p>
 * The first {@code terminal()} access captures the shared default once
 * instead of re-reading the mutable global on every access; an explicit
 * attachment overrides the captured default for getters and seeds.
 * All headless via stub terminals.
 */
public class TerminalConnectionAttachmentTest {

    /** Local connection with an ANSI-capable stub terminal underneath. */
    private static final class LocalTestConnection extends TerminalConnection {
        LocalTestConnection(Terminal terminal) {
            super(terminal);
        }

        @Override
        public boolean supportsAnsi() {
            return true;
        }
    }

    private static Terminal stubTerminal(final Device device) {
        return new TerminalConnectionLegacyLoopTest.StubTerminal(
                new ByteArrayInputStream(new byte[0])) {
            @Override
            public Device device() {
                return device;
            }
        };
    }

    @Test
    public void testExplicitAttachCapturesSharedDefaultOnce() {
        TerminalCapabilities saved = TerminalCapabilities.getInstance();
        TerminalCapabilities first = TerminalCapabilities.detect();
        TerminalCapabilities.setInstance(first);
        LocalTestConnection connection = new LocalTestConnection(stubTerminal(new BaseDevice("xterm-256color")));
        try {
            assertNull(connection.attachedDetection());
            connection.attachDetection(TerminalDetection.managed(first));
            connection.terminal();
            assertSame(first, connection.attachedDetection().capabilities());
            TerminalCapabilities second = TerminalCapabilities.detect();
            TerminalCapabilities.setInstance(second);
            connection.terminal();
            assertSame("the captured owner must not follow global replacement",
                    first, connection.attachedDetection().capabilities());
        } finally {
            connection.close();
            TerminalCapabilities.setInstance(saved);
        }
    }

    @Test
    public void testLateAttachWinsOverCapturedDefault() {
        TerminalCapabilities saved = TerminalCapabilities.getInstance();
        TerminalCapabilities.setInstance(TerminalCapabilities.detect());
        LocalTestConnection connection = new LocalTestConnection(stubTerminal(new BaseDevice("xterm-256color")));
        try {
            connection.terminal();
            assertTrue("first access captures the shared default",
                    connection.attachedDetection() != null);
            Map<String, String> env = new HashMap<>();
            env.put("TERM", "xterm-256color");
            env.put("COLORFGBG", "0;15");
            connection.attachDetection(TerminalDetection.environmentOnly(env));
            assertEquals(TerminalTheme.LIGHT,
                    connection.terminal().colorCapability().getTheme());
        } finally {
            connection.close();
            TerminalCapabilities.setInstance(saved);
        }
    }

    @Test
    public void testExplicitAttachBeatsReplacedGlobal() {
        TerminalCapabilities saved = TerminalCapabilities.getInstance();
        TerminalCapabilities sentinel = TerminalCapabilities.detect();
        TerminalCapabilities.setInstance(sentinel);
        LocalTestConnection connection = new LocalTestConnection(stubTerminal(new BaseDevice("xterm-256color")));
        try {
            Map<String, String> env = new HashMap<>();
            env.put("TERM", "xterm-256color");
            env.put("COLORFGBG", "0;15");
            connection.attachDetection(TerminalDetection.environmentOnly(env));
            assertEquals(TerminalTheme.LIGHT,
                    connection.terminal().colorCapability().getTheme());
            assertSame("attachment must not publish shared state",
                    sentinel, TerminalCapabilities.getInstance());
        } finally {
            connection.close();
            TerminalCapabilities.setInstance(saved);
        }
    }
}
