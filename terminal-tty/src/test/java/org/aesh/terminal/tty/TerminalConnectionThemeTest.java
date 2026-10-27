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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.aesh.terminal.BaseDevice;
import org.aesh.terminal.Device;
import org.aesh.terminal.EndpointContext;
import org.aesh.terminal.Terminal;
import org.aesh.terminal.TerminalFeatures;
import org.aesh.terminal.detect.ModeSupport;
import org.aesh.terminal.detect.TerminalCapabilities;
import org.aesh.terminal.detect.TerminalDetection;
import org.aesh.terminal.detect.TerminalSnapshot;
import org.aesh.terminal.detect.TerminalSubscription;
import org.aesh.terminal.detect.TerminalTheme;
import org.aesh.terminal.utils.ColorDepth;
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

    private static EndpointContext localContext() {
        return EndpointContext.builder()
                .locality(EndpointContext.Locality.LOCAL)
                .build();
    }

    @Test
    public void testUnknownOriginDoesNotForwardToSharedCache() {
        Map<String, String> env = new HashMap<>();
        env.put("TERM", "xterm-256color");
        env.put("COLORFGBG", "0;15");
        TerminalCapabilities saved = TerminalCapabilities.getInstance();
        TerminalCapabilities caps = TerminalDetection.environmentOnly(env).capabilities();
        List<TerminalTheme> received = new ArrayList<>();
        // A stub-wrapped terminal is externally supplied: its origin stays
        // unknown unless the creator declares it.
        LocalTestConnection connection = new LocalTestConnection(stubTerminal(syncDevice()));
        try {
            TerminalCapabilities.setInstance(caps);
            assertEquals(EndpointContext.Locality.UNKNOWN, connection.endpointContext().locality());
            connection.setThemeChangeHandler(received::add);
            connection.feed(DSR_DARK);

            assertEquals("the application callback still runs",
                    Collections.singletonList(TerminalTheme.DARK), received);
            assertEquals("an unknown origin must not rewrite the shared default",
                    TerminalTheme.LIGHT, caps.theme());
        } finally {
            connection.close();
            TerminalCapabilities.setInstance(saved);
        }
    }

    @Test
    public void testRemoteThemeEventNeverRewritesLocalResult() {
        Map<String, String> env = new HashMap<>();
        env.put("TERM", "xterm-256color");
        env.put("COLORFGBG", "0;15");
        TerminalCapabilities saved = TerminalCapabilities.getInstance();
        TerminalCapabilities local = TerminalDetection.environmentOnly(env).capabilities();
        TerminalCapabilities remote = TerminalCapabilities.detect();
        List<TerminalSnapshot> remoteReceived = new ArrayList<>();
        TerminalSubscription remoteSubscription = remote.subscribe(remoteReceived::add);
        try {
            TerminalCapabilities.setInstance(local);
            remote.applyThemeChange(TerminalTheme.DARK);

            assertEquals("a remote theme event stays on its own result",
                    TerminalTheme.LIGHT, local.theme());
            assertEquals(2, remoteReceived.size());
            assertEquals(TerminalTheme.DARK, remoteReceived.get(1).theme());
            assertSame(local, TerminalCapabilities.getInstance());
        } finally {
            remoteSubscription.close();
            TerminalCapabilities.setInstance(saved);
        }
    }

    @Test
    public void testLocalThemeNotificationUpdatesSharedCache() {
        TerminalCapabilities saved = TerminalCapabilities.getInstance();
        TerminalCapabilities caps = TerminalCapabilities.detect();
        List<TerminalTheme> received = new ArrayList<>();
        LocalTestConnection connection = new LocalTestConnection(stubTerminal(syncDevice()));
        try {
            TerminalCapabilities.setInstance(caps);
            connection.setEndpointContext(localContext());
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
    public void testThemeNotificationPublishesWithoutDisplacingLegacyHandler() {
        TerminalCapabilities saved = TerminalCapabilities.getInstance();
        TerminalCapabilities caps = TerminalCapabilities.detect();
        List<TerminalSnapshot> first = new ArrayList<>();
        List<TerminalSnapshot> second = new ArrayList<>();
        List<TerminalTheme> legacy = new ArrayList<>();
        List<Integer> keyboard = new ArrayList<>();
        TerminalSubscription one = caps.subscribe(first::add);
        TerminalSubscription two = caps.subscribe(second::add);
        LocalTestConnection connection = new LocalTestConnection(stubTerminal(syncDevice()));
        try {
            TerminalCapabilities.setInstance(caps);
            connection.setEndpointContext(localContext());
            connection.setThemeChangeHandler(legacy::add);
            connection.setStdinHandler(input -> {
                for (int codePoint : input) {
                    keyboard.add(codePoint);
                }
            });
            int[] input = Arrays.copyOf(DSR_DARK, DSR_DARK.length + 1);
            input[input.length - 1] = 'x';
            connection.feed(input);

            assertEquals(2, first.size());
            assertEquals(2, second.size());
            assertSame("one coherent revision reaches both subscribers",
                    first.get(1), second.get(1));
            assertEquals(1L, first.get(1).revision());
            assertEquals(TerminalTheme.DARK, first.get(1).theme());
            assertNull(first.get(1).backgroundRGB());
            assertNull(first.get(1).foregroundRGB());
            assertEquals("the independent legacy callback still runs",
                    Collections.singletonList(TerminalTheme.DARK), legacy);
            assertEquals("unrelated keyboard input survives the same chunk",
                    Collections.singletonList((int) 'x'), keyboard);
            assertFalse("passive subscription must not start a reader", connection.reading());
        } finally {
            one.close();
            two.close();
            connection.close();
            TerminalCapabilities.setInstance(saved);
        }
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
            connection.attachDetection(TerminalDetection.managed(caps));
            assertTrue("local connection must answer from the seeded probe result",
                    connection.terminal().supportsSynchronizedOutput());
        } finally {
            connection.close();
            TerminalCapabilities.setInstance(saved);
        }
    }

    @Test
    public void testDetectedColorDepthUpgradesLegacyWindowsTerminfo() throws Exception {
        TerminalCapabilities saved = TerminalCapabilities.getInstance();
        TerminalCapabilities caps = TerminalCapabilities.detect();
        Field background = TerminalCapabilities.class.getDeclaredField("backgroundRGB");
        background.setAccessible(true);
        background.set(caps, new int[] { 16, 32, 48 });
        Device device = DeviceBuilder.builder().name("windows").build();
        assertEquals(Integer.valueOf(8), device.getNumericCapability(Capability.max_colors));
        LocalTestConnection connection = new LocalTestConnection(stubTerminal(device));
        try {
            TerminalCapabilities.setInstance(caps);
            connection.attachDetection(TerminalDetection.managed(caps));
            assertTrue("The probe fixture must establish true color", caps.supportsTrueColor());
            TerminalFeatures features = connection.terminal();
            assertEquals("Legacy terminfo must not downgrade detection", ColorDepth.TRUE_COLOR,
                    features.colorDepth());
            assertEquals(ColorDepth.TRUE_COLOR, features.colorCapability().getColorDepth());

            TerminalCapabilities.setInstance(saved);
            assertEquals("Retained features must keep their connection-owned snapshot", ColorDepth.TRUE_COLOR,
                    features.colorDepth());
        } finally {
            connection.close();
            TerminalCapabilities.setInstance(saved);
        }
    }
}
