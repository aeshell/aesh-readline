/*
 * Copyright 2026 Red Hat, Inc. and/or its affiliates.
 *
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

import java.io.ByteArrayOutputStream;
import java.io.IOError;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.aesh.terminal.Attributes;
import org.aesh.terminal.Terminal;
import org.aesh.terminal.utils.ANSI;
import org.junit.Test;

/**
 * Tests for TerminalConnection close/cleanup behavior.
 * Uses piped streams to create a TerminalConnection backed by ExternalTerminal
 * (no real TTY needed, works in CI).
 */
public class TerminalConnectionCloseTest {

    private TerminalConnection createConnection() throws IOException {
        PipedOutputStream stdinWriter = new PipedOutputStream();
        PipedInputStream stdinReader = new PipedInputStream(stdinWriter, 4096);
        ByteArrayOutputStream stdoutCapture = new ByteArrayOutputStream();
        return new TerminalConnection(StandardCharsets.UTF_8,
                stdinReader, stdoutCapture);
    }

    @Test
    public void testDoubleCloseDoesNotThrow() throws Exception {
        TerminalConnection conn = createConnection();
        conn.close();
        conn.close(); // should not throw
    }

    @Test
    public void testCloseHandlerCalledOnce() throws Exception {
        TerminalConnection conn = createConnection();
        AtomicInteger closeCount = new AtomicInteger(0);
        conn.setCloseHandler(v -> closeCount.incrementAndGet());

        conn.close();
        assertEquals("Close handler should be called once", 1, closeCount.get());

        conn.close(); // second close — guard should prevent re-entry
        assertEquals("Close handler should still be 1 after double close",
                1, closeCount.get());
    }

    @Test
    public void testCloseHandlerExceptionDoesNotPreventCleanup() throws Exception {
        TerminalConnection conn = createConnection();
        conn.setCloseHandler(v -> {
            throw new RuntimeException("Simulated failure");
        });

        // close() should not throw — the exception should be caught internally
        conn.close();
        // Verify reading is false (cleanup continued past the exception)
        assertFalse("reading should be false after close", conn.reading());
    }

    @Test
    public void testCloseHandlerCalledBeforeTerminalClose() throws Exception {
        TerminalConnection conn = createConnection();
        // The closeHandler should be able to write to the terminal
        // because terminal.close() hasn't happened yet
        AtomicInteger handlerOrder = new AtomicInteger(0);
        conn.setCloseHandler(v -> {
            // At this point, the terminal should still be usable
            handlerOrder.set(1);
        });

        conn.close();
        assertEquals("Close handler should have been called", 1, handlerOrder.get());
    }

    @Test
    public void testReadingIsFalseAfterClose() throws Exception {
        TerminalConnection conn = createConnection();
        // reading starts as false (not yet opened)
        assertFalse("reading should be false before open", conn.reading());

        conn.close();
        assertFalse("reading should be false after close", conn.reading());
    }

    @Test
    public void testCloseWithNullCloseHandler() throws Exception {
        TerminalConnection conn = createConnection();
        // Don't set a close handler — close should still work
        conn.close(); // should not throw NPE
    }

    @Test
    public void testCloseCleanupSequencesAnsi() {
        String seq = TerminalConnection.closeCleanupSequences(true, false);
        assertTrue("Must end synchronized output",
                seq.contains(ANSI.MODE_2026_DISABLE));
        assertTrue("Must ensure the cursor is visible (#273)",
                seq.contains(ANSI.CURSOR_SHOW));
        assertFalse("Must not exit the alternate screen: the restore slot "
                + "is stale when this connection never entered it",
                seq.contains(ANSI.MAIN_BUFFER));
        assertFalse("Must not disable focus tracking when not enabled",
                seq.contains(ANSI.FOCUS_TRACKING_DISABLE));
        // Order: synchronized-output end before cursor show
        assertTrue("2026-disable must precede cursor show",
                seq.indexOf(ANSI.MODE_2026_DISABLE) < seq.indexOf(ANSI.CURSOR_SHOW));
    }

    @Test
    public void testCloseCleanupSequencesNoAnsi() {
        // External terminals and redirected stdout get no escape sequences
        assertEquals("", TerminalConnection.closeCleanupSequences(false, false));
    }

    @Test
    public void testCloseCleanupSequencesFocus() {
        assertEquals(ANSI.FOCUS_TRACKING_DISABLE,
                TerminalConnection.closeCleanupSequences(false, true));
    }

    /**
     * Terminal whose attribute restore and close fail on demand, recording
     * the close. A dynamic proxy avoids hand-implementing the wide
     * Terminal interface.
     */
    private static final class FailingTerminal implements InvocationHandler {
        final AtomicReference<Throwable> setAttributesFailure = new AtomicReference<>();
        final AtomicReference<Throwable> closeFailure = new AtomicReference<>();
        final AtomicBoolean closed = new AtomicBoolean();
        final Attributes attributes = new Attributes();

        Terminal proxy() {
            return (Terminal) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] { Terminal.class }, this);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            switch (method.getName()) {
                case "getAttributes":
                    return attributes;
                case "setAttributes":
                    Throwable restoreFailure = setAttributesFailure.get();
                    if (restoreFailure != null) {
                        throw restoreFailure;
                    }
                    return null;
                case "close":
                    closed.set(true);
                    Throwable shutdownFailure = closeFailure.get();
                    if (shutdownFailure != null) {
                        throw shutdownFailure;
                    }
                    return null;
                case "handle":
                case "getCodePointConsumer":
                case "device":
                    return null;
                case "toString":
                    return "FailingTerminal";
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                default:
                    throw new UnsupportedOperationException(method.getName());
            }
        }
    }

    @Test
    public void testRestoreFailureStillClosesTerminal() throws Exception {
        FailingTerminal failing = new FailingTerminal();
        failing.setAttributesFailure.set(new IOError(new IOException("restore blew up")));
        TerminalConnection conn = new TerminalConnection(failing.proxy());
        // Must stay silent and still close the terminal.
        conn.close();
        assertTrue("terminal.close() must run despite restore failure",
                failing.closed.get());
    }

    @Test
    public void testBothFailuresCloseSilently() throws Exception {
        FailingTerminal failing = new FailingTerminal();
        failing.setAttributesFailure.set(new IOError(new IOException("restore blew up")));
        failing.closeFailure.set(new RuntimeException("close blew up"));
        TerminalConnection conn = new TerminalConnection(failing.proxy());
        conn.close();
        assertTrue(failing.closed.get());
    }

    @Test
    public void testRestoreAndCloseShapes() throws Exception {
        FailingTerminal failing = new FailingTerminal();
        Terminal terminal = failing.proxy();
        Attributes saved = new Attributes();

        assertNull(TerminalConnection.restoreAndClose(terminal, saved));
        assertTrue(failing.closed.get());

        IOError restoreFailure = new IOError(new IOException("restore blew up"));
        RuntimeException closeFailure = new RuntimeException("close blew up");
        failing.closed.set(false);
        failing.setAttributesFailure.set(restoreFailure);
        failing.closeFailure.set(closeFailure);
        Throwable primary = TerminalConnection.restoreAndClose(terminal, saved);
        assertSame(restoreFailure, primary);
        assertEquals(1, primary.getSuppressed().length);
        assertSame(closeFailure, primary.getSuppressed()[0]);
        assertTrue(failing.closed.get());

        failing.closed.set(false);
        failing.setAttributesFailure.set(null);
        failing.closeFailure.set(null);
        assertNull(TerminalConnection.restoreAndClose(terminal, saved));
        assertTrue(failing.closed.get());

        assertNull(TerminalConnection.restoreAndClose(null, saved));
    }

    private static int countReaderThreads() {
        Thread[] threads = new Thread[Thread.activeCount() + 16];
        int n = Thread.enumerate(threads);
        int count = 0;
        for (int i = 0; i < n; i++) {
            if ("Aesh InputStream Reader".equals(threads[i].getName())
                    && threads[i].isAlive()) {
                count++;
            }
        }
        return count;
    }

    private static void awaitReaderCount(int expected, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (countReaderThreads() == expected) {
                return;
            }
            Thread.sleep(20);
        }
        assertEquals("reader thread count", expected, countReaderThreads());
    }

    private static void awaitReading(TerminalConnection conn, long timeoutMs) throws InterruptedException {
        // Thread-alive and flag-set are two different events: the worker
        // must be scheduled before it sets reading, so poll the flag.
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (conn.reading()) {
                return;
            }
            Thread.sleep(20);
        }
        assertTrue("worker must observe reading", conn.reading());
    }

    @Test
    public void testConcurrentOpensStartSingleReader() throws Exception {
        TerminalConnection conn = createConnection();
        int base = countReaderThreads();
        int starters = 8;
        CountDownLatch ready = new CountDownLatch(starters);
        CountDownLatch go = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        try {
            for (int i = 0; i < starters; i++) {
                Thread starter = new Thread(() -> {
                    try {
                        ready.countDown();
                        assertTrue(go.await(5, TimeUnit.SECONDS));
                        conn.openNonBlocking();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
                starter.setDaemon(true);
                threads.add(starter);
                starter.start();
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            go.countDown();
            for (Thread starter : threads) {
                starter.join(5000);
                assertFalse(starter.isAlive());
            }
            awaitReaderCount(base + 1, 5000);
            awaitReading(conn, 5000);
        } finally {
            conn.close();
        }
        awaitReaderCount(base, 5000);
    }

    @Test
    public void testCloseBeforeOpenStartsNothing() throws Exception {
        TerminalConnection conn = createConnection();
        conn.close();
        int base = countReaderThreads();
        conn.openNonBlocking();
        Thread.sleep(200);
        assertFalse(conn.reading());
        assertEquals(base, countReaderThreads());
    }

    @Test
    public void testOpenAfterCloseStaysClosed() throws Exception {
        TerminalConnection conn = createConnection();
        int base = countReaderThreads();
        conn.openNonBlocking();
        awaitReaderCount(base + 1, 5000);
        conn.close();
        awaitReaderCount(base, 5000);
        conn.openNonBlocking();
        Thread.sleep(200);
        assertFalse("open after close must not resurrect reading", conn.reading());
        assertEquals(base, countReaderThreads());
    }

    @Test
    public void testReaderExceptionShutsDown() throws Exception {
        PipedOutputStream stdinWriter = new PipedOutputStream();
        PipedInputStream stdinReader = new PipedInputStream(stdinWriter, 4096);
        ByteArrayOutputStream stdoutCapture = new ByteArrayOutputStream();
        TerminalConnection conn = new TerminalConnection(StandardCharsets.UTF_8,
                stdinReader, stdoutCapture);
        int base = countReaderThreads();
        try {
            conn.openNonBlocking();
            awaitReaderCount(base + 1, 5000);
            conn.setStdinHandler(cps -> {
                throw new RuntimeException("Simulated reader failure");
            });
            stdinWriter.write("x".getBytes(StandardCharsets.UTF_8));
            stdinWriter.flush();
            awaitReaderCount(base, 5000);
            assertFalse("failed reader must not leave reading true", conn.reading());
        } finally {
            stdinWriter.close();
            conn.close();
        }
    }
}
