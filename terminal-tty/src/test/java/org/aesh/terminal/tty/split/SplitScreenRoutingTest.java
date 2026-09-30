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
package org.aesh.terminal.tty.split;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.aesh.terminal.Attributes;
import org.aesh.terminal.Connection;
import org.aesh.terminal.Device;
import org.aesh.terminal.Terminal;
import org.aesh.terminal.tty.Capability;
import org.aesh.terminal.tty.Signal;
import org.aesh.terminal.tty.Size;
import org.aesh.terminal.tty.SplitScreen;
import org.aesh.terminal.tty.TerminalConnection;
import org.junit.Test;

/**
 * Routing tests for split-screen writes (#339).
 * <p>
 * TerminalConnection.write() dispatches to the current region, but both
 * region implementations and every renderer escape block used to call back
 * into Connection.write() — bottom-region writes recursed without bound
 * (StackOverflowError) and renderer escapes re-entered routing instead of
 * reaching the terminal. Renderer and bottom-region output must bypass
 * region dispatch via the raw sink, serialized so concurrent region
 * writes cannot interleave cursor save/restore sequences.
 */
public class SplitScreenRoutingTest {

    /** ESC built programmatically: raw escape bytes must not appear in source. */
    private static final String ESC = String.valueOf((char) 27);
    private static final String SAVE = ESC + "7";
    private static final String RESTORE = ESC + "8";
    private static final String RESET = ESC + "[r" + ESC + "[2J" + ESC + "[1;1H";

    /**
     * Minimal Terminal stub with one shared capture stream.
     */
    static class StubTerminal implements Terminal {
        final ByteArrayOutputStream captured = new ByteArrayOutputStream();
        final InputStream input = new ByteArrayInputStream(new byte[0]);
        volatile int width = 80;
        volatile int height = 24;

        String delta(int mark) {
            byte[] bytes = captured.toByteArray();
            return new String(bytes, mark, bytes.length - mark, StandardCharsets.UTF_8);
        }

        @Override
        public String getName() {
            return "test";
        }

        @Override
        public Terminal.SignalHandler handle(Signal signal, Terminal.SignalHandler handler) {
            return null;
        }

        @Override
        public void raise(Signal signal) {
        }

        @Override
        public InputStream input() {
            return input;
        }

        @Override
        public OutputStream output() {
            return captured;
        }

        @Override
        public boolean echo() {
            return false;
        }

        @Override
        public boolean echo(boolean echo) {
            return false;
        }

        @Override
        public Attributes getAttributes() {
            return new Attributes();
        }

        @Override
        public void setAttributes(Attributes attr) {
        }

        @Override
        public Size getSize() {
            return new Size(width, height);
        }

        @Override
        public Device device() {
            return null;
        }

        @Override
        public void close() {
        }
    }

    /**
     * Minimal Connection fake capturing routed writes. Covers the
     * non-TerminalConnection fallback in the renderer sink.
     */
    static class FakeConnection implements Connection {
        final StringBuilder sunk = new StringBuilder();
        final Size size = new Size(80, 24);

        synchronized String delta(int mark) {
            return sunk.substring(mark);
        }

        synchronized int mark() {
            return sunk.length();
        }

        @Override
        public Device device() {
            return null;
        }

        @Override
        public Size size() {
            return size;
        }

        @Override
        public Consumer<int[]> stdinHandler() {
            return null;
        }

        @Override
        public void setStdinHandler(Consumer<int[]> handler) {
        }

        @Override
        public Consumer<int[]> stdoutHandler() {
            return null;
        }

        @Override
        public synchronized Connection write(String s) {
            sunk.append(s);
            return this;
        }

        @Override
        public boolean put(Capability capability, Object... params) {
            return false;
        }

        @Override
        public Consumer<Signal> signalHandler() {
            return null;
        }

        @Override
        public void setSignalHandler(Consumer<Signal> handler) {
        }

        @Override
        public Consumer<Size> sizeHandler() {
            return null;
        }

        @Override
        public void setSizeHandler(Consumer<Size> handler) {
        }

        @Override
        public Consumer<Void> closeHandler() {
            return null;
        }

        @Override
        public void setCloseHandler(Consumer<Void> handler) {
        }

        @Override
        public void openBlocking() {
        }

        @Override
        public void openNonBlocking() {
        }

        @Override
        public void close() {
        }

        @Override
        public Attributes attributes() {
            return new Attributes();
        }

        @Override
        public void setAttributes(Attributes attr) {
        }

        @Override
        public boolean supportsAnsi() {
            return true;
        }

        @Override
        public Charset inputEncoding() {
            return StandardCharsets.UTF_8;
        }

        @Override
        public Charset outputEncoding() {
            return StandardCharsets.UTF_8;
        }
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int from = 0;
        while (true) {
            int at = haystack.indexOf(needle, from);
            if (at < 0) {
                return count;
            }
            count++;
            from = at + needle.length();
        }
    }

    @Test
    public void testBottomWriteWithBottomCurrentReachesSinkOnce() throws Exception {
        StubTerminal stub = new StubTerminal();
        TerminalConnection conn = new TerminalConnection(stub);
        try {
            SplitScreen split = conn.splitScreen(0.5);
            conn.setCurrentRegion(split.bottomRegion());
            int mark = stub.captured.size();
            conn.write("x");
            assertEquals("bottom-routed write must reach the sink exactly once",
                    "x", stub.delta(mark));
            mark = stub.captured.size();
            split.bottomRegion().write("y");
            assertEquals("direct bottom write must reach the sink exactly once",
                    "y", stub.delta(mark));
        } finally {
            conn.close();
        }
    }

    @Test
    public void testTopWriteWithTopCurrentDrawsOnce() throws Exception {
        StubTerminal stub = new StubTerminal();
        TerminalConnection conn = new TerminalConnection(stub);
        try {
            SplitScreen split = conn.splitScreen(0.5);
            conn.setCurrentRegion(split.topRegion());
            int mark = stub.captured.size();
            conn.write("hello");
            String delta = stub.delta(mark);
            assertEquals("top text must be drawn exactly once",
                    1, countOccurrences(delta, "hello"));
            assertTrue("redraw must save the cursor",
                    delta.contains(SAVE));
            assertTrue("redraw must restore the cursor",
                    delta.contains(RESTORE));
        } finally {
            conn.close();
        }
    }

    @Test
    public void testWriteWithNoCurrentRegionGoesDirect() throws Exception {
        StubTerminal stub = new StubTerminal();
        TerminalConnection conn = new TerminalConnection(stub);
        try {
            conn.splitScreen(0.5);
            int mark = stub.captured.size();
            conn.write("plain");
            assertEquals("write with no current region must go straight out",
                    "plain", stub.delta(mark));
        } finally {
            conn.close();
        }
    }

    @Test
    public void testRendererPathsBypassRouting() throws Exception {
        StubTerminal stub = new StubTerminal();
        TerminalConnection conn = new TerminalConnection(stub);
        try {
            SplitScreen split = conn.splitScreen(0.5);
            conn.setCurrentRegion(split.bottomRegion());

            int mark = stub.captured.size();
            split.setSeparator("=");
            String delta = stub.delta(mark);
            assertTrue("separator redraw must reach the sink",
                    delta.contains(SAVE) && delta.contains(RESTORE));

            mark = stub.captured.size();
            ((SplitScreenImpl) split).handleResize(new Size(80, 24));
            assertTrue("resize redraw must reach the sink",
                    stub.delta(mark).contains(ESC + "[2J"));

            mark = stub.captured.size();
            split.setSplitRatio(0.4);
            assertTrue("ratio redraw must reach the sink",
                    stub.delta(mark).contains(ESC + "[2J"));

            split.suspend();
            mark = stub.captured.size();
            split.topRegion().write("hidden");
            assertEquals("suspended top writes must be suppressed",
                    "", stub.delta(mark));
            split.bottomRegion().write("shown");
            assertEquals("suspended bottom writes still reach the sink",
                    "shown", stub.delta(mark));

            split.resume();
            mark = stub.captured.size();
            split.topRegion().write("visible");
            assertEquals("resumed top writes must draw exactly once",
                    1, countOccurrences(stub.delta(mark), "visible"));
        } finally {
            conn.close();
        }
    }

    @Test
    public void testCloseResetsOnceAndSuppresses() throws Exception {
        StubTerminal stub = new StubTerminal();
        TerminalConnection conn = new TerminalConnection(stub);
        try {
            SplitScreen split = conn.splitScreen(0.5);
            conn.setCurrentRegion(split.bottomRegion());
            int mark = stub.captured.size();
            split.close();
            assertEquals("close must emit the reset sequence exactly once",
                    1, countOccurrences(stub.delta(mark), RESET));
            mark = stub.captured.size();
            split.topRegion().write("after-close");
            assertEquals("writes after close must be suppressed",
                    "", stub.delta(mark));
            split.close();
            assertEquals("second close must emit nothing",
                    "", stub.delta(mark));
        } finally {
            conn.close();
        }
    }

    @Test
    public void testConcurrentRegionWritesStayFramed() throws Exception {
        final StubTerminal stub = new StubTerminal();
        final TerminalConnection conn = new TerminalConnection(stub);
        try {
            final SplitScreen split = conn.splitScreen(0.5);
            final int perThread = 50;
            final List<Thread> threads = new ArrayList<>();
            final List<Throwable> failures = new ArrayList<>();
            for (int t = 0; t < 2; t++) {
                final String marker = "b" + t + ";";
                Thread bottom = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            for (int i = 0; i < perThread; i++) {
                                split.bottomRegion().write(marker);
                            }
                        } catch (Throwable e) {
                            synchronized (failures) {
                                failures.add(e);
                            }
                        }
                    }
                });
                bottom.setDaemon(true);
                threads.add(bottom);
                final String line = "t" + t + "-line";
                Thread top = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            for (int i = 0; i < perThread; i++) {
                                split.topRegion().write(line + "-" + i);
                            }
                        } catch (Throwable e) {
                            synchronized (failures) {
                                failures.add(e);
                            }
                        }
                    }
                });
                top.setDaemon(true);
                threads.add(top);
            }
            for (Thread thread : threads) {
                thread.start();
            }
            for (Thread thread : threads) {
                thread.join(30000);
            }
            for (Thread thread : threads) {
                assertTrue("worker must finish promptly", !thread.isAlive());
            }
            assertTrue("no worker may fail: " + failures, failures.isEmpty());
            String output = new String(stub.captured.toByteArray(), StandardCharsets.UTF_8);
            assertEquals("every bottom payload must arrive exactly once",
                    perThread, countOccurrences(output, "b0;"));
            assertEquals("every bottom payload must arrive exactly once",
                    perThread, countOccurrences(output, "b1;"));
            assertEquals("every save must pair with a restore",
                    countOccurrences(output, SAVE), countOccurrences(output, RESTORE));
            List<String> kept = ((SplitScreenImpl.ScreenRegionImpl) split.topRegion()).scrollback.getLastLines(1000);
            for (int t = 0; t < 2; t++) {
                for (int i = 0; i < perThread; i++) {
                    assertTrue("scrollback must keep t" + t + "-line-" + i,
                            kept.contains("t" + t + "-line-" + i));
                }
            }
        } finally {
            conn.close();
        }
    }

    @Test
    public void testFallbackConnectionSink() {
        FakeConnection fake = new FakeConnection();
        SplitScreenImpl split = new SplitScreenImpl(fake, 0.5);
        try {
            int mark = fake.mark();
            split.bottomRegion().write("q");
            assertEquals("bottom write must reach a plain Connection once",
                    "q", fake.delta(mark));
            mark = fake.mark();
            split.topRegion().write("w");
            String delta = fake.delta(mark);
            assertEquals("top text must be drawn exactly once",
                    1, countOccurrences(delta, "w"));
            assertTrue("redraw must save and restore the cursor",
                    delta.contains(SAVE) && delta.contains(RESTORE));
        } finally {
            split.close();
        }
    }

    // ==================== Split ratio margins (#340) ====================

    @Test
    public void testSetSplitRatioReprogramsMargins() throws Exception {
        StubTerminal stub = new StubTerminal();
        TerminalConnection conn = new TerminalConnection(stub);
        try {
            SplitScreen split = conn.splitScreen(0.5);
            // 24 rows at 0.5: top 11, separator 12, bottom from 13
            int mark = stub.captured.size();
            split.setSplitRatio(0.25);
            // 23 available rows at 0.25: top 5, separator 6, bottom from 7
            String delta = stub.delta(mark);
            assertEquals("ratio change must program the new margins exactly once",
                    1, countOccurrences(delta, ESC + "[7;24r"));
            assertTrue("cursor must park in the new bottom region",
                    delta.contains(ESC + "[7;1H"));
            assertEquals("software top size must track the ratio",
                    new Size(80, 5), split.topRegion().size());
            assertEquals("software bottom size must track the ratio",
                    new Size(80, 18), split.bottomRegion().size());
        } finally {
            conn.close();
        }
    }

    @Test
    public void testMultipleRatioChangesTrackMargins() throws Exception {
        StubTerminal stub = new StubTerminal();
        TerminalConnection conn = new TerminalConnection(stub);
        try {
            SplitScreen split = conn.splitScreen(0.5);
            double[] ratios = { 0.7, 0.3, 0.5 };
            // bottomStartRow per ratio on 24 rows: 18, 8, 13
            int[] bottomRows = { 18, 8, 13 };
            for (int i = 0; i < ratios.length; i++) {
                int mark = stub.captured.size();
                split.setSplitRatio(ratios[i]);
                String delta = stub.delta(mark);
                assertEquals("change " + i + " must program its own margins once",
                        1, countOccurrences(delta, ESC + "[" + bottomRows[i] + ";24r"));
                assertTrue("change " + i + " must park the cursor",
                        delta.contains(ESC + "[" + bottomRows[i] + ";1H"));
            }
            // Final layout is coherent: regions plus separator fill the screen
            assertEquals(new Size(80, 11), split.topRegion().size());
            assertEquals(new Size(80, 12), split.bottomRegion().size());
        } finally {
            conn.close();
        }
    }

    @Test
    public void testResizeThenRatioTrackMargins() throws Exception {
        StubTerminal stub = new StubTerminal();
        TerminalConnection conn = new TerminalConnection(stub);
        try {
            SplitScreen split = conn.splitScreen(0.5);
            stub.height = 30;
            int mark = stub.captured.size();
            ((SplitScreenImpl) split).handleResize(new Size(80, 30));
            // 29 available rows at 0.5: top 14, separator 15, bottom from 16
            assertEquals("resize must program margins for the new height",
                    1, countOccurrences(stub.delta(mark), ESC + "[16;30r"));
            mark = stub.captured.size();
            split.setSplitRatio(0.25);
            // 29 available rows at 0.25: top 7, separator 8, bottom from 9
            String delta = stub.delta(mark);
            assertEquals("ratio change after resize must use the new height",
                    1, countOccurrences(delta, ESC + "[9;30r"));
            assertEquals(new Size(80, 7), split.topRegion().size());
            assertEquals(new Size(80, 22), split.bottomRegion().size());
        } finally {
            conn.close();
        }
    }

    @Test
    public void testSetSplitRatioRejectsInvalid() throws Exception {
        StubTerminal stub = new StubTerminal();
        TerminalConnection conn = new TerminalConnection(stub);
        try {
            SplitScreen split = conn.splitScreen(0.5);
            int mark = stub.captured.size();
            double[] bad = { 0.0, 1.0, -0.5, 1.5, Double.NaN,
                    Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY };
            for (double ratio : bad) {
                try {
                    split.setSplitRatio(ratio);
                    assertTrue("ratio " + ratio + " must be rejected", false);
                } catch (IllegalArgumentException expected) {
                    // Expected.
                }
            }
            assertEquals("rejected ratios must emit nothing",
                    "", stub.delta(mark));
            assertEquals("rejected ratios must not change the stored ratio",
                    0.5, split.getSplitRatio(), 0.0);
            assertEquals("layout must be unchanged after rejections",
                    new Size(80, 11), split.topRegion().size());
        } finally {
            conn.close();
        }
    }

    @Test
    public void testSetSplitRatioNotifiesResizeHandlers() throws Exception {
        StubTerminal stub = new StubTerminal();
        TerminalConnection conn = new TerminalConnection(stub);
        try {
            SplitScreen split = conn.splitScreen(0.5);
            final List<Size> topSizes = new ArrayList<>();
            final List<Size> bottomSizes = new ArrayList<>();
            split.topRegion().setResizeHandler(new Consumer<Size>() {
                @Override
                public void accept(Size size) {
                    topSizes.add(size);
                }
            });
            split.bottomRegion().setResizeHandler(new Consumer<Size>() {
                @Override
                public void accept(Size size) {
                    bottomSizes.add(size);
                }
            });
            split.setSplitRatio(0.25);
            assertEquals("top handler must fire once with the new size",
                    java.util.Collections.singletonList(new Size(80, 5)), topSizes);
            assertEquals("bottom handler must fire once with the new size",
                    java.util.Collections.singletonList(new Size(80, 18)), bottomSizes);
        } finally {
            conn.close();
        }
    }

    @Test
    public void testSetSplitRatioWhileSuspendedOrClosed() throws Exception {
        StubTerminal stub = new StubTerminal();
        TerminalConnection conn = new TerminalConnection(stub);
        try {
            SplitScreen split = conn.splitScreen(0.5);
            split.suspend();
            int mark = stub.captured.size();
            split.setSplitRatio(0.3);
            assertEquals("ratio change while suspended must stay silent",
                    "", stub.delta(mark));
            assertEquals("ratio must still be stored while suspended",
                    0.3, split.getSplitRatio(), 0.0);
            assertEquals("software sizes update even while suspended",
                    new Size(80, 6), split.topRegion().size());
            mark = stub.captured.size();
            split.resume();
            // 23 available rows at 0.3: top 6, separator 7, bottom from 8
            assertTrue("resume must program margins for the stored ratio",
                    stub.delta(mark).contains(ESC + "[8;24r"));
            split.close();
            mark = stub.captured.size();
            split.setSplitRatio(0.6);
            assertEquals("ratio change after close must stay silent",
                    "", stub.delta(mark));
        } finally {
            conn.close();
        }
    }

    @Test
    public void testSetSplitRatioOnTooSmallTerminalSuspends() throws Exception {
        StubTerminal stub = new StubTerminal();
        TerminalConnection conn = new TerminalConnection(stub);
        try {
            SplitScreen split = conn.splitScreen(0.5);
            stub.height = 6;
            int mark = stub.captured.size();
            split.setSplitRatio(0.4);
            assertTrue("too-small terminal must auto-suspend like resize does",
                    ((SplitScreenImpl) split).isSuspended());
            assertTrue("auto-suspend must reset the scroll region",
                    stub.delta(mark).contains(RESET));
        } finally {
            conn.close();
        }
    }
}
