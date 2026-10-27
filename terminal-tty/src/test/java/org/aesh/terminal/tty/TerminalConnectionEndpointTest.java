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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.aesh.terminal.BaseDevice;
import org.aesh.terminal.Device;
import org.aesh.terminal.EndpointContext;
import org.aesh.terminal.Terminal;
import org.aesh.terminal.detect.TerminalCapabilities;
import org.aesh.terminal.detect.TerminalDetection;
import org.aesh.terminal.detect.TerminalProbeSession;
import org.aesh.terminal.detect.TerminalProbeTransport;
import org.junit.Assume;
import org.junit.Test;

/**
 * Endpoint facts decide the default owner on local connections (#382).
 * <p>
 * The approved default-selection rule uses process stdin/stdout facts,
 * not environment markers. A true result retains the shared capabilities;
 * two false results capture independent environment hints. Neither branch
 * starts discovery. The literal decision table and injected cached fd facts
 * run without a terminal. The piped-stdio case also checks the public wiring.
 * These tests do not establish locality for arbitrary caller-owned streams.
 */
public class TerminalConnectionEndpointTest {

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
    public void testAutoAttachSharesGlobalWhenTty() {
        TerminalCapabilities saved = TerminalCapabilities.getInstance();
        try {
            TerminalCapabilities first = TerminalCapabilities.detect();
            TerminalCapabilities.setInstance(first);
            assertSame(first, TerminalConnection.autoAttachOwner(true, false).capabilities());
            assertSame(first, TerminalConnection.autoAttachOwner(false, true).capabilities());
            assertSame(first, TerminalConnection.autoAttachOwner(true, true).capabilities());
        } finally {
            TerminalCapabilities.setInstance(saved);
        }
    }

    @Test(timeout = 45000)
    public void testAutoAttachHintsDoNotJoinSharedDiscovery() throws Exception {
        Map<String, String> env = System.getenv();
        String tmux = env.get("TMUX");
        String term = env.get("TERM");
        Assume.assumeFalse("live query skipped in multiplexer",
                (tmux != null && !tmux.isEmpty())
                        || (term != null && (term.toLowerCase(Locale.ROOT).startsWith("tmux")
                                || term.toLowerCase(Locale.ROOT).startsWith("screen"))));
        final CountDownLatch opened = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicInteger checks = new AtomicInteger();
        final AtomicInteger opens = new AtomicInteger();
        final AtomicInteger writes = new AtomicInteger();
        final AtomicInteger closes = new AtomicInteger();
        String esc = Character.toString((char) 27);
        String bel = Character.toString((char) 7);
        final byte[] reply = (esc + "[?2026;1$y" + esc + "[?2027;1$y" + esc + "[?63;1;2;4c"
                + esc + "]10;rgb:ffff/ffff/ffff" + bel + esc + "]11;rgb:0000/0000/0000" + bel)
                .getBytes(StandardCharsets.US_ASCII);
        TerminalCapabilities saved = TerminalCapabilities.getInstance();
        TerminalCapabilities pending = null;
        Thread consumer = null;
        TerminalCapabilities.setProbeTransport(new TerminalProbeTransport() {
            @Override
            public boolean isAvailable() {
                checks.incrementAndGet();
                return true;
            }

            @Override
            public TerminalProbeSession open() throws IOException {
                opens.incrementAndGet();
                opened.countDown();
                try {
                    if (!release.await(30, TimeUnit.SECONDS)) {
                        throw new IOException("probe open release timed out");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted", e);
                }
                return new TerminalProbeSession() {
                    @Override
                    public void write(byte[] data) {
                        writes.incrementAndGet();
                    }

                    @Override
                    public InputStream input() {
                        return new ByteArrayInputStream(reply);
                    }

                    @Override
                    public void close() {
                        closes.incrementAndGet();
                    }
                };
            }
        });
        try {
            TerminalCapabilities.invalidate();
            pending = TerminalCapabilities.detectAsync();
            assertTrue("worker must reach the gated open",
                    opened.await(10, TimeUnit.SECONDS));
            FutureTask<TerminalDetection> request = new FutureTask<>(new Callable<TerminalDetection>() {
                @Override
                public TerminalDetection call() {
                    return TerminalConnection.autoAttachOwner(false, false);
                }
            });
            consumer = new Thread(request, "headless-owner-consumer");
            consumer.setDaemon(true);
            consumer.start();
            TerminalDetection hints = request.get(5, TimeUnit.SECONDS);
            assertNotSame("headless default must not adopt shared discovery",
                    pending, hints.capabilities());
            assertTrue("hints are complete without waiting",
                    hints.capabilities().awaitColors(0, TimeUnit.NANOSECONDS));
            assertFalse("shared discovery must still be pending",
                    pending.awaitColors(0, TimeUnit.NANOSECONDS));
        } finally {
            release.countDown();
            try {
                if (pending != null) {
                    assertTrue("producer must finish before restoring global state",
                            pending.awaitColors(10, TimeUnit.SECONDS));
                    assertArrayEquals(new int[] { 0, 0, 0 }, pending.backgroundRGB());
                    assertEquals("fixture must execute one availability check", 1, checks.get());
                    assertEquals("fixture must execute one open", 1, opens.get());
                    assertEquals("fixture must execute one write", 1, writes.get());
                    assertEquals("fixture must execute one close", 1, closes.get());
                }
                if (consumer != null) {
                    consumer.join(10000);
                    assertFalse("consumer must stop before restoring global state", consumer.isAlive());
                }
            } finally {
                TerminalCapabilities.setProbeTransport(null);
                TerminalCapabilities.setInstance(saved);
            }
        }
    }

    @Test
    public void testTerminalAttachesSharedWhenTtyCached() throws Exception {
        int savedStdin = TtyDetect.cachedStdinForTests();
        int savedStdout = TtyDetect.cachedStdoutForTests();
        TerminalCapabilities saved = TerminalCapabilities.getInstance();
        try {
            // Pin the cached fd truth instead of inheriting the harness:
            // the live probe belongs to TtyDetect's own tests, while this
            // covers the wiring from cached truth to attached owner.
            TtyDetect.cachedStdinForTests(1);
            TerminalConnection connection = new TerminalConnection(stubTerminal(new BaseDevice("xterm-256color")));
            try {
                connection.terminal();
                assertSame("a TTY endpoint must share the global probe",
                        TerminalCapabilities.getInstance(), connection.attachedDetection().capabilities());
            } finally {
                connection.close();
            }
        } finally {
            TtyDetect.cachedStdinForTests(savedStdin);
            TtyDetect.cachedStdoutForTests(savedStdout);
            TerminalCapabilities.setInstance(saved);
        }
    }

    @Test
    public void testTerminalAttachesHintsWhenPiped() throws Exception {
        Assume.assumeFalse("piped stdio required",
                TtyDetect.isStdinTty() || TtyDetect.isStdoutTty());
        TerminalConnection connection = new TerminalConnection(stubTerminal(new BaseDevice("xterm-256color")));
        try {
            connection.terminal();
            assertNotNull(connection.attachedDetection());
            assertNotSame("piped endpoints must not share the global default",
                    TerminalCapabilities.getInstance(), connection.attachedDetection().capabilities());
            assertTrue(connection.attachedDetection().capabilities()
                    .awaitColors(0, TimeUnit.NANOSECONDS));
        } finally {
            connection.close();
        }
    }

    @Test
    public void testWinchPublishesObservedDimensionsBeforeLegacyCallback() {
        final Terminal.SignalHandler[] winch = new Terminal.SignalHandler[1];
        Terminal terminal = new TerminalConnectionLegacyLoopTest.StubTerminal(
                new ByteArrayInputStream(new byte[0])) {
            @Override
            public Terminal.SignalHandler handle(Signal signal, Terminal.SignalHandler handler) {
                if (signal == Signal.WINCH) {
                    winch[0] = handler;
                }
                return null;
            }

            @Override
            public Device device() {
                return new BaseDevice("xterm-256color");
            }

            @Override
            public Size getSize() {
                return new Size(132, 43);
            }
        };
        final TerminalConnection connection = new TerminalConnection(terminal);
        AtomicInteger callbacks = new AtomicInteger();
        try {
            connection.setSizeHandler(size -> {
                assertEquals(new Size(132, 43), size);
                EndpointContext context = connection.endpointContext();
                assertEquals(new Size(132, 43), context.dimensions());
                assertEquals(EndpointContext.Source.OBSERVED, context.dimensionsSource());
                callbacks.incrementAndGet();
            });
            assertNotNull(winch[0]);
            winch[0].handle(Signal.WINCH);

            assertEquals(1, callbacks.get());
            assertEquals(new Size(132, 43), connection.endpointContext().dimensions());
        } finally {
            connection.close();
        }
    }

    @Test
    public void testLocalSetupDeclaresLocalEndpoint() throws Exception {
        int savedStdin = TtyDetect.cachedStdinForTests();
        int savedStdout = TtyDetect.cachedStdoutForTests();
        int savedStderr = TtyDetect.cachedStderrForTests();
        try {
            TtyDetect.cachedStdinForTests(1);
            TtyDetect.cachedStdoutForTests(1);
            TtyDetect.cachedStderrForTests(0);
            TerminalConnection connection = new TerminalConnection();
            try {
                EndpointContext context = connection.endpointContext();
                assertEquals(EndpointContext.Locality.LOCAL, context.locality());
                assertEquals(EndpointContext.Truth.YES, context.input().terminal());
                assertEquals(EndpointContext.Source.OBSERVED, context.input().terminalSource());
                assertEquals(EndpointContext.Truth.YES, context.output().terminal());
                assertEquals(EndpointContext.Source.OBSERVED, context.output().terminalSource());
                assertEquals(EndpointContext.Truth.NO, context.error().terminal());
                assertEquals(EndpointContext.Source.OBSERVED, context.error().terminalSource());
                assertNull("setup performs no size query", context.dimensions());
            } finally {
                connection.close();
            }
        } finally {
            TtyDetect.cachedStdinForTests(savedStdin);
            TtyDetect.cachedStdoutForTests(savedStdout);
            TtyDetect.cachedStderrForTests(savedStderr);
        }
    }

    @Test
    public void testStreamBackedConnectionStaysUnknown() throws Exception {
        TerminalConnection connection = new TerminalConnection(StandardCharsets.UTF_8,
                new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream());
        try {
            assertEquals(EndpointContext.Locality.UNKNOWN, connection.endpointContext().locality());
        } finally {
            connection.close();
        }
    }

    @Test
    public void testConstructorFactsAreVisibleBeforeHandlerRuns() throws Exception {
        EndpointContext declared = EndpointContext.builder()
                .locality(EndpointContext.Locality.LOCAL)
                .build();
        List<EndpointContext> observed = new ArrayList<>();
        TerminalConnection connection = new TerminalConnection(StandardCharsets.UTF_8,
                StandardCharsets.UTF_8, new ByteArrayInputStream(new byte[0]),
                new ByteArrayOutputStream(),
                conn -> {
                    observed.add(conn.endpointContext());
                }, declared);
        try {
            assertEquals("the handler must see the supplied facts, not unknown",
                    1, observed.size());
            assertSame(declared, observed.get(0));
            assertSame(declared, connection.endpointContext());
        } finally {
            connection.close();
        }
    }

    @Test
    public void testCloseInvalidatesCreatorSuppliedEndpointFacts() {
        TerminalConnection connection = new TerminalConnection(stubTerminal(new BaseDevice("xterm-256color")));
        EndpointContext supplied = EndpointContext.builder()
                .locality(EndpointContext.Locality.LOCAL)
                .dimensions(new Size(160, 50), EndpointContext.Source.OBSERVED)
                .build();
        try {
            connection.setEndpointContext(supplied);
            connection.close();
            assertSame(EndpointContext.unknown(), connection.endpointContext());
            connection.setEndpointContext(supplied);
            assertSame(EndpointContext.unknown(), connection.endpointContext());
        } finally {
            connection.close();
        }
    }
}
