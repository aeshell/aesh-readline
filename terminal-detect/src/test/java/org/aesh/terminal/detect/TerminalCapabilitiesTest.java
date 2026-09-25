package org.aesh.terminal.detect;

import static org.junit.Assert.*;

import org.junit.Test;

public class TerminalCapabilitiesTest {

    @Test
    public void testDetectReturnsNonNull() {
        TerminalCapabilities caps = TerminalCapabilities.detect();
        assertNotNull(caps);
        assertNotNull(caps.terminalName());
        assertNotNull(caps.imageProtocol());
        assertNotNull(caps.theme());
    }

    @Test
    public void testDetectColorConsistency() {
        TerminalCapabilities caps = TerminalCapabilities.detect();
        if (caps.supportsTrueColor()) {
            assertTrue(caps.supports256Colors());
            assertTrue(caps.supportsColor());
        }
        if (caps.supports256Colors()) {
            assertTrue(caps.supportsColor());
        }
    }

    @Test
    public void testSingleton() {
        TerminalCapabilities a = TerminalCapabilities.getInstance();
        TerminalCapabilities b = TerminalCapabilities.getInstance();
        assertSame(a, b);
    }

    @Test
    public void testSetInstance() {
        TerminalCapabilities custom = TerminalCapabilities.detect();
        TerminalCapabilities.setInstance(custom);
        assertSame(custom, TerminalCapabilities.getInstance());
        // Reset for other tests
        TerminalCapabilities.setInstance(null);
    }

    @Test
    public void testToString() {
        TerminalCapabilities caps = TerminalCapabilities.detect();
        String str = caps.toString();
        assertNotNull(str);
        assertTrue(str.contains("terminal="));
        assertTrue(str.contains("trueColor="));
        assertTrue(str.contains("imageProtocol="));
    }

    @Test
    public void testPaletteColorsEmptyBeforeQuery() {
        TerminalCapabilities caps = TerminalCapabilities.detect();
        assertNotNull(caps.paletteColors());
        assertTrue(caps.paletteColors().isEmpty());
        assertNull(caps.foregroundRGB());
        assertNull(caps.backgroundRGB());
    }

    @Test
    public void testImageProtocolValues() {
        assertEquals(4, ImageProtocol.values().length);
        assertNotNull(ImageProtocol.valueOf("NONE"));
        assertNotNull(ImageProtocol.valueOf("KITTY"));
        assertNotNull(ImageProtocol.valueOf("ITERM2"));
        assertNotNull(ImageProtocol.valueOf("SIXEL"));
    }

    @Test
    public void testDetectFullCachesInstance() {
        TerminalCapabilities saved = TerminalCapabilities.getInstance();
        try {
            TerminalCapabilities.invalidate();
            TerminalCapabilities a = TerminalCapabilities.detectFull();
            TerminalCapabilities b = TerminalCapabilities.detectFull();
            assertSame("Repeat detectFull must return cached capabilities, not re-probe", a, b);
        } finally {
            TerminalCapabilities.setInstance(saved);
        }
    }

    @Test
    public void testDetectAsyncSharesInstance() {
        TerminalCapabilities saved = TerminalCapabilities.getInstance();
        try {
            TerminalCapabilities.invalidate();
            TerminalCapabilities a = TerminalCapabilities.detectAsync();
            TerminalCapabilities b = TerminalCapabilities.detectAsync();
            assertSame("Repeat detectAsync must share one background query", a, b);
        } finally {
            TerminalCapabilities.setInstance(saved);
        }
    }

    @Test
    public void testInvalidateForcesReprobe() {
        TerminalCapabilities saved = TerminalCapabilities.getInstance();
        try {
            TerminalCapabilities.invalidate();
            TerminalCapabilities a = TerminalCapabilities.detectFull();
            TerminalCapabilities.invalidate();
            TerminalCapabilities b = TerminalCapabilities.detectFull();
            assertNotSame("Post-invalidate detectFull must re-probe", a, b);
        } finally {
            TerminalCapabilities.setInstance(saved);
        }
    }
}
