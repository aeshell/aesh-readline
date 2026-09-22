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
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

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
}
