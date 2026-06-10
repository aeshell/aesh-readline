package org.aesh.terminal.tty.split;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.aesh.terminal.tty.ScreenRegion;
import org.aesh.terminal.tty.Size;
import org.aesh.terminal.tty.SplitScreen;
import org.junit.Test;

/**
 * Tests for SplitScreen functionality.
 * <p>
 * These tests verify the API contracts and layout calculations.
 * Tests that require a real terminal are skipped in CI.
 */
public class SplitScreenTest {

    @Test
    public void testMinRegionHeight() {
        assertEquals(3, SplitScreen.MIN_REGION_HEIGHT);
    }

    @Test
    public void testSplitScreenImplLayout() {
        // Test layout calculation with a mock-like approach
        // We can't create a real SplitScreenImpl without a Connection,
        // but we can verify the constants and interface
        assertTrue("MIN_REGION_HEIGHT should be positive",
                SplitScreen.MIN_REGION_HEIGHT > 0);
    }

    @Test
    public void testScreenRegionInterface() {
        // Verify the ScreenRegion interface has the expected methods
        // by creating a minimal implementation
        ScreenRegion region = new ScreenRegion() {
            private Size size = new Size(80, 24);

            @Override
            public void write(String text) {
            }

            @Override
            public Size size() {
                return size;
            }

            @Override
            public void setResizeHandler(java.util.function.Consumer<Size> handler) {
            }

            @Override
            public void clear() {
            }

            @Override
            public void close() {
            }
        };

        assertEquals(80, region.size().getWidth());
        assertEquals(24, region.size().getHeight());
        // writeln should not throw
        region.writeln("test");
    }

    @Test
    public void testScrollbackBufferInRegion() {
        // Verify that scrollback buffer correctly feeds the region
        ScrollbackBuffer buf = new ScrollbackBuffer(5);
        buf.addLine("line 1");
        buf.addLine("line 2");
        buf.addLine("line 3");

        // Get last 2 lines (simulating a 2-row visible area)
        java.util.List<String> visible = buf.getLastLines(2);
        assertEquals(2, visible.size());
        assertEquals("line 2", visible.get(0));
        assertEquals("line 3", visible.get(1));
    }

    @Test
    public void testSplitRatioCalculation() {
        // Verify that ratio calculations produce valid splits
        int termHeight = 24;
        double ratio = 0.67;
        int availableRows = termHeight - 1; // 1 for separator
        int topHeight = Math.max(SplitScreen.MIN_REGION_HEIGHT,
                (int) (availableRows * ratio));
        int bottomHeight = Math.max(SplitScreen.MIN_REGION_HEIGHT,
                availableRows - topHeight);

        assertTrue("Top height should be >= MIN", topHeight >= SplitScreen.MIN_REGION_HEIGHT);
        assertTrue("Bottom height should be >= MIN", bottomHeight >= SplitScreen.MIN_REGION_HEIGHT);
        assertEquals("Top + bottom + separator should equal terminal height",
                termHeight, topHeight + bottomHeight + 1);
    }

    @Test
    public void testSplitRatioHalf() {
        int termHeight = 24;
        double ratio = 0.5;
        int availableRows = termHeight - 1;
        int topHeight = Math.max(SplitScreen.MIN_REGION_HEIGHT,
                (int) (availableRows * ratio));
        int bottomHeight = Math.max(SplitScreen.MIN_REGION_HEIGHT,
                availableRows - topHeight);

        assertTrue("Top height should be roughly half",
                Math.abs(topHeight - bottomHeight) <= 1);
    }

    @Test
    public void testSplitRatioSmallTerminal() {
        // Terminal with exactly enough room for min splits + separator
        int termHeight = SplitScreen.MIN_REGION_HEIGHT * 2 + 1; // 7
        double ratio = 0.5;
        int availableRows = termHeight - 1;
        int topHeight = Math.max(SplitScreen.MIN_REGION_HEIGHT,
                (int) (availableRows * ratio));
        int bottomHeight = Math.max(SplitScreen.MIN_REGION_HEIGHT,
                availableRows - topHeight);

        assertTrue("Top >= MIN", topHeight >= SplitScreen.MIN_REGION_HEIGHT);
        assertTrue("Bottom >= MIN", bottomHeight >= SplitScreen.MIN_REGION_HEIGHT);
    }

    @Test
    public void testSplitRatioTooSmall() {
        // Terminal too small for any split
        int termHeight = SplitScreen.MIN_REGION_HEIGHT * 2; // 6, needs 7
        int availableRows = termHeight - 1; // 5
        int topHeight = (int) (availableRows * 0.5); // 2
        int bottomHeight = availableRows - topHeight; // 3

        // At least one region would be below minimum
        assertTrue("Should fail: top too small",
                topHeight < SplitScreen.MIN_REGION_HEIGHT
                        || bottomHeight < SplitScreen.MIN_REGION_HEIGHT);
    }

    @Test
    public void testSplitRatioExtremes() {
        int termHeight = 30;
        int availableRows = termHeight - 1;

        // Very high ratio — top gets most space
        int topHigh = Math.max(SplitScreen.MIN_REGION_HEIGHT,
                (int) (availableRows * 0.9));
        int bottomHigh = Math.max(SplitScreen.MIN_REGION_HEIGHT,
                availableRows - topHigh);
        assertTrue("Bottom should still be >= MIN", bottomHigh >= SplitScreen.MIN_REGION_HEIGHT);

        // Very low ratio — bottom gets most space
        int topLow = Math.max(SplitScreen.MIN_REGION_HEIGHT,
                (int) (availableRows * 0.1));
        int bottomLow = Math.max(SplitScreen.MIN_REGION_HEIGHT,
                availableRows - topLow);
        assertTrue("Top should still be >= MIN", topLow >= SplitScreen.MIN_REGION_HEIGHT);
    }
}
