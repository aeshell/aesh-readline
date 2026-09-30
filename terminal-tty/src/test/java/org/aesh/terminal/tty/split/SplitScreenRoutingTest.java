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
            return new Size(80, 24);
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
}
