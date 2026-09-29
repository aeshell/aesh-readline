package org.aesh.terminal.detect;

import static org.junit.Assert.*;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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

    @Test
    public void testThemeEventWinsOverLateAsyncColorResponse() {
        TerminalCapabilities saved = TerminalCapabilities.getInstance();
        try {
            TerminalCapabilities caps = TerminalCapabilities.detect();
            TerminalCapabilities.setInstance(caps);
            TerminalCapabilities.onThemeChanged(TerminalTheme.DARK);

            TerminalColorQuery late = new TerminalColorQuery();
            late.foreground = new int[] { 0, 0, 0 };
            late.background = new int[] { 255, 255, 255 };
            late.palette = Collections.singletonMap(0, new int[] { 20, 20, 20 });
            caps.applyColorResult(late);

            assertSame(caps, TerminalCapabilities.getInstance());
            assertEquals(TerminalTheme.DARK, caps.theme());
            assertNull("Old foreground must not reappear", caps.foregroundRGB());
            assertNull("Old background must not reappear", caps.backgroundRGB());
            assertEquals("Non-theme capabilities still update", 1, caps.paletteColors().size());
        } finally {
            TerminalCapabilities.setInstance(saved);
        }
    }

    @Test
    public void testMeasuredWhiteOverridesDarkHint() throws Exception {
        TerminalCapabilities caps = TerminalCapabilities.detect();
        setResolvedTheme(caps, TerminalTheme.DARK);

        TerminalColorQuery measured = new TerminalColorQuery();
        measured.background = new int[] { 255, 255, 255 };
        caps.applyColorResult(measured);

        assertEquals(TerminalTheme.LIGHT, caps.theme());
        assertArrayEquals(new int[] { 255, 255, 255 }, caps.backgroundRGB());
    }

    @Test
    public void testMeasuredBlackOverridesLightHint() throws Exception {
        TerminalCapabilities caps = TerminalCapabilities.detect();
        setResolvedTheme(caps, TerminalTheme.LIGHT);

        TerminalColorQuery measured = new TerminalColorQuery();
        measured.background = new int[] { 0, 0, 0 };
        caps.applyColorResult(measured);

        assertEquals(TerminalTheme.DARK, caps.theme());
        assertArrayEquals(new int[] { 0, 0, 0 }, caps.backgroundRGB());
    }

    @Test
    public void testAbsentReplyKeepsEarlierHint() throws Exception {
        TerminalCapabilities caps = TerminalCapabilities.detect();
        setResolvedTheme(caps, TerminalTheme.DARK);

        TerminalColorQuery partial = new TerminalColorQuery();
        partial.foreground = new int[] { 255, 255, 255 };
        caps.applyColorResult(partial);

        assertEquals(TerminalTheme.DARK, caps.theme());
        assertNull(caps.backgroundRGB());
        assertArrayEquals(new int[] { 255, 255, 255 }, caps.foregroundRGB());
    }

    /**
     * Preset a previously resolved theme, simulating an earlier
     * environment/config hint. Reflection mirrors the existing
     * theme-event test: the field has no other injectable seam.
     */
    private static void setResolvedTheme(TerminalCapabilities caps, TerminalTheme theme)
            throws Exception {
        Field field = TerminalCapabilities.class.getDeclaredField("resolvedTheme");
        field.setAccessible(true);
        field.set(caps, theme);
    }

    @Test
    public void testRgbReplyImpliesFullLadder() {
        // A terminal answering OSC color queries supports 24-bit output,
        // which includes the 256 palette and basic color by definition.
        TerminalCapabilities caps = TerminalCapabilities.detect();

        TerminalColorQuery measured = new TerminalColorQuery();
        measured.foreground = new int[] { 255, 255, 255 };
        caps.applyColorResult(measured);

        assertTrue(caps.supportsTrueColor());
        assertTrue(caps.supports256Colors());
        assertTrue(caps.supportsColor());
        assertNull("unprobed grapheme clustering must stay null, not false",
                caps.nativeGraphemeClustering());
    }

    @Test
    public void testLadderHoldsAcrossThemeEvent() {
        // Relations must hold in every state, independent of the real
        // environment this test happens to run in.
        TerminalCapabilities caps = TerminalCapabilities.detect();
        assertLadder(caps);

        TerminalColorQuery measured = new TerminalColorQuery();
        measured.background = new int[] { 0, 0, 0 };
        caps.applyColorResult(measured);
        assertLadder(caps);

        TerminalCapabilities.setInstance(caps);
        try {
            TerminalCapabilities.onThemeChanged(TerminalTheme.LIGHT);
            assertLadder(caps);
        } finally {
            TerminalCapabilities.setInstance(null);
        }
    }

    private static void assertLadder(TerminalCapabilities caps) {
        if (caps.supportsTrueColor()) {
            assertTrue("true color implies 256 colors", caps.supports256Colors());
        }
        if (caps.supports256Colors()) {
            assertTrue("256 colors imply basic color", caps.supportsColor());
        }
    }

    @Test
    public void testGetInstanceNeverReturnsNullDuringInvalidation() throws Exception {
        // Readers race an invalidator: the returned reference is captured
        // once, so clearing the shared field mid-call cannot null it.
        TerminalCapabilities saved = TerminalCapabilities.getInstance();
        try {
            AtomicReference<Throwable> failure = new AtomicReference<>();
            AtomicBoolean stop = new AtomicBoolean();
            Thread[] readers = new Thread[4];
            for (int i = 0; i < readers.length; i++) {
                readers[i] = new Reader(stop, failure);
                readers[i].start();
            }
            for (int i = 0; i < 200000 && failure.get() == null; i++) {
                TerminalCapabilities.invalidate();
            }
            stop.set(true);
            for (Thread reader : readers) {
                reader.join(10000);
            }
            assertNull("getInstance() returned null under invalidation",
                    failure.getAndSet(null));
        } finally {
            TerminalCapabilities.setInstance(saved);
        }
    }

    private static final class Reader extends Thread {
        private final AtomicBoolean stop;
        private final AtomicReference<Throwable> failure;

        Reader(AtomicBoolean stop, AtomicReference<Throwable> failure) {
            this.stop = stop;
            this.failure = failure;
        }

        @Override
        public void run() {
            try {
                while (!stop.get() && failure.get() == null) {
                    if (TerminalCapabilities.getInstance() == null) {
                        throw new AssertionError("getInstance() returned null");
                    }
                }
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            }
        }
    }
}
