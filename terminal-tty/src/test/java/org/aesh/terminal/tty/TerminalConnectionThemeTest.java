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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.aesh.terminal.BaseDevice;
import org.aesh.terminal.Device;
import org.aesh.terminal.Terminal;
import org.aesh.terminal.detect.ModeSupport;
import org.aesh.terminal.detect.TerminalCapabilities;
import org.aesh.terminal.detect.TerminalTheme;
import org.junit.Test;

/**
 * Per-connection capability scoping for local terminals (#353).
 * <p>
 * A local {@code TerminalConnection} forwards theme notifications to
 * the shared startup cache and answers mode queries from a seeded
 * snapshot of it. All headless via stub terminals.
 */
public class TerminalConnectionThemeTest {

    private static final int[] DSR_DARK = { 27, 91, 63, 57, 57, 55, 59, 49, 110 };

    /** Local connection with an ANSI-capable stub terminal underneath. */
    private static final class LocalTestConnection extends TerminalConnection {
        LocalTestConnection(Terminal terminal) {
            super(terminal);
        }

        @Override
        public boolean supportsAnsi() {
            return true;
        }

        void feed(int[] input) {
            eventDecoder.accept(input);
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

    private static Device syncDevice() {
        return new BaseDevice("xterm-256color") {
            @Override
            public boolean supportsSynchronizedOutput() {
                return false;
            }
        };
    }

    @Test
    public void testLocalThemeNotificationUpdatesSharedCache() {
        TerminalCapabilities saved = TerminalCapabilities.getInstance();
        TerminalCapabilities caps = TerminalCapabilities.detect();
        List<TerminalTheme> received = new ArrayList<>();
        LocalTestConnection connection = new LocalTestConnection(stubTerminal(syncDevice()));
        try {
            TerminalCapabilities.setInstance(caps);
            connection.setThemeChangeHandler(received::add);
            connection.feed(DSR_DARK);

            assertEquals("local notification must refresh shared defaults",
                    TerminalTheme.DARK, caps.theme());
            assertEquals(Collections.singletonList(TerminalTheme.DARK), received);
            assertSame(caps, TerminalCapabilities.getInstance());
            assertTrue("theme slot must report the app handler, unwrapped",
                    connection.themeChangeHandler() != null);
        } finally {
            connection.close();
            TerminalCapabilities.setInstance(saved);
        }
        assertSame("restore must stick", saved, TerminalCapabilities.getInstance());
    }

    @Test
    public void testLocalModesAnswerFromSeed() throws Exception {
        TerminalCapabilities saved = TerminalCapabilities.getInstance();
        TerminalCapabilities caps = TerminalCapabilities.detect();
        Field mode2026 = TerminalCapabilities.class.getDeclaredField("mode2026Support");
        mode2026.setAccessible(true);
        mode2026.set(caps, ModeSupport.SUPPORTED);
        LocalTestConnection connection = new LocalTestConnection(stubTerminal(syncDevice()));
        try {
            TerminalCapabilities.setInstance(caps);
            assertTrue("local connection must answer from the seeded probe result",
                    connection.terminal().supportsSynchronizedOutput());
        } finally {
            connection.close();
            TerminalCapabilities.setInstance(saved);
        }
    }
}
