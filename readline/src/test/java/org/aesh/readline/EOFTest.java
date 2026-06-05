/*
 * Copyright 2012 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.aesh.readline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.EnumMap;

import org.aesh.readline.tty.terminal.TestReadlineConnection;
import org.aesh.terminal.Key;
import org.junit.Test;

/**
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class EOFTest {

    @Test
    public void testEOF() {

        final int[] closeCalled = { 0 };

        EnumMap<ReadlineFlag, Integer> flags = new EnumMap<>(ReadlineFlag.class);
        TestReadlineConnection term = new TestReadlineConnection(flags);

        term.setCloseHandler(v -> closeCalled[0]++);

        term.read("foo".getBytes());
        term.read(Key.CTRL_D);
        term.assertBuffer("foo");
        term.read(Key.CTRL_A);
        term.read(Key.CTRL_D);
        term.assertBuffer("oo");
        term.read(Key.CTRL_D);
        term.read(Key.CTRL_D);
        assertEquals(0, closeCalled[0]);
        term.read(Key.CTRL_D);
        assertEquals(1, closeCalled[0]);

    }

    @Test
    public void testIgnoreEOF() {

        final int[] closeCalled = { 0 };

        EnumMap<ReadlineFlag, Integer> flags = new EnumMap<>(ReadlineFlag.class);
        flags.put(ReadlineFlag.IGNORE_EOF, 2);
        TestReadlineConnection term = new TestReadlineConnection(flags);

        term.setCloseHandler(v -> closeCalled[0]++);

        term.read("foo".getBytes());
        term.read(Key.CTRL_D);
        term.assertBuffer("foo");
        term.read(Key.CTRL_A);
        term.read(Key.CTRL_D);
        term.assertBuffer("oo");
        term.read(Key.CTRL_D);
        term.read(Key.CTRL_D);
        assertEquals(0, closeCalled[0]);
        term.read(Key.CTRL_D);
        assertEquals(0, closeCalled[0]);
        term.read(Key.CTRL_D);
        assertEquals(0, closeCalled[0]);
        term.read(Key.CTRL_D);
        assertEquals(1, closeCalled[0]);
    }

    /**
     * Test that Ctrl+D on an empty buffer closes the connection cleanly.
     */
    @Test
    public void testEOFOnEmptyBuffer() {
        final int[] closeCalled = { 0 };

        EnumMap<ReadlineFlag, Integer> flags = new EnumMap<>(ReadlineFlag.class);
        TestReadlineConnection term = new TestReadlineConnection(flags);
        term.setCloseHandler(v -> closeCalled[0]++);

        // Empty buffer + Ctrl+D = EOF → close
        term.read(Key.CTRL_D);
        assertEquals(1, closeCalled[0]);
    }

    /**
     * Test that Ctrl+D works cleanly after typing and deleting text
     * (buffer becomes empty, then EOF triggers).
     */
    @Test
    public void testEOFAfterClearingBuffer() {
        final int[] closeCalled = { 0 };

        EnumMap<ReadlineFlag, Integer> flags = new EnumMap<>(ReadlineFlag.class);
        TestReadlineConnection term = new TestReadlineConnection(flags);
        term.setCloseHandler(v -> closeCalled[0]++);

        // Type "ab", then delete both chars, then Ctrl+D = EOF
        term.read(Key.a);
        term.read(Key.b);
        term.read(Key.BACKSPACE);
        term.read(Key.BACKSPACE);
        term.assertBuffer("");

        term.read(Key.CTRL_D);
        assertEquals(1, closeCalled[0]);
    }

    /**
     * Test that finish() handles exceptions from setAttributes() gracefully.
     * <p>
     * This simulates what happens when EndOfFile calls connection.close()
     * before finish(): the connection's setAttributes() throws because
     * the underlying pty is closed. The finish() method must catch this
     * and not propagate the exception.
     */
    @Test
    public void testFinishHandlesClosedConnectionException() {
        final int[] closeCalled = { 0 };
        final boolean[] throwOnSetAttributes = { false };

        EnumMap<ReadlineFlag, Integer> flags = new EnumMap<>(ReadlineFlag.class);
        TestReadlineConnection term = new TestReadlineConnection(flags) {
            @Override
            public void setAttributes(org.aesh.terminal.Attributes attr) {
                if (throwOnSetAttributes[0]) {
                    throw new RuntimeException("Connection closed");
                }
                super.setAttributes(attr);
            }
        };
        term.setCloseHandler(v -> {
            closeCalled[0]++;
            throwOnSetAttributes[0] = true;
        });

        try {
            term.read(Key.CTRL_D);
            assertEquals("close handler should be called", 1, closeCalled[0]);
        } catch (Exception e) {
            fail("finish() should not propagate exceptions from closed connection: " + e);
        }
    }

    /**
     * Test that finish() handles IOError from setAttributes() gracefully.
     * <p>
     * AbstractPosixTerminal.setAttributes() wraps IOException in java.io.IOError
     * (which is an Error, not an Exception). The finish() method must catch this too.
     */
    @Test
    public void testFinishHandlesClosedConnectionIOError() {
        final int[] closeCalled = { 0 };
        final boolean[] throwOnSetAttributes = { false };

        EnumMap<ReadlineFlag, Integer> flags = new EnumMap<>(ReadlineFlag.class);
        TestReadlineConnection term = new TestReadlineConnection(flags) {
            @Override
            public void setAttributes(org.aesh.terminal.Attributes attr) {
                if (throwOnSetAttributes[0]) {
                    // Simulate what AbstractPosixTerminal does: wrap IOException in IOError
                    throw new java.io.IOError(new java.io.IOException("FfmPty is closed"));
                }
                super.setAttributes(attr);
            }
        };
        term.setCloseHandler(v -> {
            closeCalled[0]++;
            throwOnSetAttributes[0] = true;
        });

        try {
            term.read(Key.CTRL_D);
            assertEquals("close handler should be called", 1, closeCalled[0]);
        } catch (Throwable e) {
            fail("finish() should not propagate IOError from closed connection: " + e);
        }
    }

    @Test
    public void testIgnoreEOF2() {

        final int[] closeCalled = { 0 };

        EnumMap<ReadlineFlag, Integer> flags = new EnumMap<>(ReadlineFlag.class);
        flags.put(ReadlineFlag.IGNORE_EOF, 2);
        TestReadlineConnection term = new TestReadlineConnection(flags);

        term.setCloseHandler(v -> closeCalled[0]++);

        term.read("foo".getBytes());
        term.read(Key.CTRL_D);
        term.assertBuffer("foo");
        term.read(Key.CTRL_A);
        term.read(Key.CTRL_D);
        term.assertBuffer("oo");
        term.read(Key.CTRL_D);
        term.read(Key.CTRL_D);
        assertEquals(0, closeCalled[0]);
        term.read(Key.CTRL_D);
        assertEquals(0, closeCalled[0]);
        term.read(Key.CTRL_D);
        term.read(Key.CTRL_A);
        assertEquals(0, closeCalled[0]);
        term.read(Key.CTRL_D);
        assertEquals(0, closeCalled[0]);
        term.read(Key.CTRL_D);
        term.read(Key.CTRL_D);
        assertEquals(1, closeCalled[0]);
    }

}
