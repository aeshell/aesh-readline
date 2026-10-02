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
package org.aesh.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.aesh.terminal.Device.TerminalType;
import org.aesh.terminal.detect.ImageProtocol;
import org.aesh.terminal.utils.ColorDepth;
import org.junit.Test;

public class DeviceCapabilityIsolationTest {

    @Test
    public void testEachDeviceUsesItsOwnTerminalIdentifier() {
        for (TerminalType type : TerminalType.values()) {
            assertEquals(type.getIdentifier(), type,
                    new BaseDevice(type.getIdentifier()).detectTerminalType());
        }
        assertEquals(TerminalType.KITTY, new BaseDevice("xterm-kitty").detectTerminalType());
        assertEquals(TerminalType.XTERM, new BaseDevice("xterm-256color").detectTerminalType());
        assertEquals(TerminalType.TMUX, new BaseDevice("tmux-256color").detectTerminalType());
        assertEquals(TerminalType.SCREEN, new BaseDevice("screen.xterm-256color").detectTerminalType());
        assertEquals(TerminalType.WINDOWS_TERMINAL, new BaseDevice("windows-terminal").detectTerminalType());
        assertEquals(TerminalType.UNKNOWN, new BaseDevice(null).detectTerminalType());
    }

    @Test
    public void testOscAndModeChecksStayWithTheDevice() {
        Device kitty = new BaseDevice("kitty");
        Device linux = new BaseDevice("linux");
        assertTrue(kitty.supportsOscQueries());
        assertTrue(kitty.supportsThemeQuery());
        assertTrue(kitty.supportsGraphemeClusterMode());
        assertTrue(kitty.supportsSynchronizedOutput());
        assertTrue(kitty.supportsShellIntegration());
        assertTrue(kitty.supportsHyperlinks());
        assertFalse(linux.supportsOscQueries());
        assertFalse(linux.supportsThemeQuery());
        assertFalse(linux.supportsGraphemeClusterMode());
        assertFalse(linux.supportsSynchronizedOutput());
        assertFalse(linux.supportsShellIntegration());
        assertFalse(linux.supportsHyperlinks());
        assertFalse(new BaseDevice("JetBrains-JediTerm").supportsOscQueries());
        assertTrue(new BaseDevice("JetBrains-JediTerm").isJetBrainsTerminal());
        assertFalse(new BaseDevice("dumb").supportsOscQueries());
    }

    @Test
    public void testMultiplexerFactsDoNotComeFromTheLocalShell() {
        Device tmux = new BaseDevice("tmux-256color");
        Device screen = new BaseDevice("screen-256color");
        Device kitty = new BaseDevice("kitty");
        assertTrue(tmux.isMultiplexer());
        assertTrue(screen.isMultiplexer());
        assertFalse(kitty.isMultiplexer());
        assertFalse(tmux.isTmuxPassthroughEnabled());
        assertFalse(tmux.supportsOscQueries());
        assertFalse(screen.supportsOscQueries());
    }

    @Test
    public void testColorAndImageFallbacksDoNotUseLocalDetection() {
        Device linux = new BaseDevice("linux");
        Device kitty = new BaseDevice("kitty");
        assertEquals(ColorDepth.COLORS_8, linux.getColorDepth());
        assertEquals(ColorDepth.TRUE_COLOR, kitty.getColorDepth());
        assertEquals(ColorDepth.COLORS_8, new BaseDevice("unknown").getColorDepth());
        assertEquals(ColorDepth.NO_COLOR, new BaseDevice("dumb").getColorDepth());
        assertEquals(ImageProtocol.NONE, linux.getImageProtocol());
        assertEquals(ImageProtocol.KITTY, kitty.getImageProtocol());
        assertEquals(ImageProtocol.SIXEL, new BaseDevice("mlterm").getImageProtocol());
        assertEquals(ImageProtocol.NONE, new BaseDevice(null).getImageProtocol());
        assertFalse(linux.supportsImages());
        assertTrue(kitty.supportsImages());
    }

    private static StreamConnection remote(final Device device) {
        return new StreamConnection(StandardCharsets.UTF_8,
                new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream()) {
            @Override
            public Device device() {
                return device;
            }

            @Override
            public boolean supportsAnsi() {
                return true;
            }
        };
    }

    @Test
    public void testRemoteConnectionFeaturesUseTheRemoteDevice() {
        StreamConnection linux = remote(new BaseDevice("linux"));
        StreamConnection kitty = remote(new BaseDevice("kitty"));
        try {
            assertEquals(TerminalType.LINUX_CONSOLE, linux.terminal().getTerminalType());
            assertFalse(linux.terminal().supportsOscQueries());
            assertFalse(linux.terminal().supportsSynchronizedOutput());
            assertFalse(linux.terminal().supportsGraphemeClusterMode());
            assertEquals(ColorDepth.COLORS_8, linux.terminal().colorDepth());
            assertEquals(ImageProtocol.NONE, linux.terminal().getImageProtocol());
            assertEquals(TerminalType.KITTY, kitty.terminal().getTerminalType());
            assertTrue(kitty.terminal().supportsOscQueries());
            assertTrue(kitty.terminal().supportsSynchronizedOutput());
            assertTrue(kitty.terminal().supportsGraphemeClusterMode());
            assertEquals(ImageProtocol.KITTY, kitty.terminal().getImageProtocol());
        } finally {
            linux.close();
            kitty.close();
        }
    }

    @Test
    public void testMissingDeviceDoesNotBorrowLocalOscSupport() {
        StreamConnection connection = remote(null);
        try {
            assertFalse(connection.terminal().supportsOscQueries());
            assertEquals(ImageProtocol.NONE, connection.terminal().getImageProtocol());
        } finally {
            connection.close();
        }
    }
}
