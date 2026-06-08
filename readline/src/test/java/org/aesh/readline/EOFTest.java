/*
 * Copyright 2012 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.aesh.readline;

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
        EnumMap<ReadlineFlag, Integer> flags = new EnumMap<>(ReadlineFlag.class);
        TestReadlineConnection term = new TestReadlineConnection(flags);

        // Ctrl+D with non-empty buffer deletes char (like delete-char)
        term.read("foo".getBytes());
        term.read(Key.CTRL_D);
        term.assertBuffer("foo");
        term.read(Key.CTRL_A);
        term.read(Key.CTRL_D);
        term.assertBuffer("oo");

        // Ctrl+D on empty buffer triggers EOF — finish(null)
        term.read(Key.CTRL_D);
        term.read(Key.CTRL_D);
        // After "oo" is deleted char by char, buffer is empty, next Ctrl+D = EOF
        term.read(Key.CTRL_D);
        term.assertLine(null);
    }

    @Test
    public void testIgnoreEOF() {
        EnumMap<ReadlineFlag, Integer> flags = new EnumMap<>(ReadlineFlag.class);
        flags.put(ReadlineFlag.IGNORE_EOF, 2);
        TestReadlineConnection term = new TestReadlineConnection(flags);

        // Ctrl+D with non-empty buffer deletes char
        term.read("foo".getBytes());
        term.read(Key.CTRL_D);
        term.assertBuffer("foo");
        term.read(Key.CTRL_A);
        term.read(Key.CTRL_D);
        term.assertBuffer("oo");

        // Empty buffer: need 3 Ctrl+D presses (ignore 2, accept on 3rd)
        term.read(Key.CTRL_D);
        term.read(Key.CTRL_D);
        // Still ignored (only 2 presses on empty)
        term.read(Key.CTRL_D); // 3rd press = EOF
        term.assertLine(null);
    }

    @Test
    public void testIgnoreEOF2() {
        EnumMap<ReadlineFlag, Integer> flags = new EnumMap<>(ReadlineFlag.class);
        flags.put(ReadlineFlag.IGNORE_EOF, 2);
        TestReadlineConnection term = new TestReadlineConnection(flags);

        term.read("foo".getBytes());
        term.read(Key.CTRL_D);
        term.assertBuffer("foo");
        term.read(Key.CTRL_A);
        term.read(Key.CTRL_D);
        term.assertBuffer("oo");

        // Empty buffer: 2 presses ignored
        term.read(Key.CTRL_D);
        term.read(Key.CTRL_D);

        // Pressing a different key resets the EOF counter
        term.read(Key.CTRL_D);
        term.read(Key.CTRL_A);

        // Need 3 more Ctrl+D presses again
        term.read(Key.CTRL_D);
        term.read(Key.CTRL_D);
        term.read(Key.CTRL_D);
        term.assertLine(null);
    }

    @Test
    public void testEOFOnEmptyBuffer() {
        EnumMap<ReadlineFlag, Integer> flags = new EnumMap<>(ReadlineFlag.class);
        TestReadlineConnection term = new TestReadlineConnection(flags);

        // Empty buffer + Ctrl+D = EOF
        term.read(Key.CTRL_D);
        term.assertLine(null);
    }

    @Test
    public void testEOFAfterClearingBuffer() {
        EnumMap<ReadlineFlag, Integer> flags = new EnumMap<>(ReadlineFlag.class);
        TestReadlineConnection term = new TestReadlineConnection(flags);

        term.read(Key.a);
        term.read(Key.b);
        term.read(Key.BACKSPACE);
        term.read(Key.BACKSPACE);
        term.assertBuffer("");

        term.read(Key.CTRL_D);
        term.assertLine(null);
    }

    /**
     * Test that finish() handles exceptions from setAttributes() gracefully.
     */
    @Test
    public void testFinishHandlesClosedConnectionException() {
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
            throwOnSetAttributes[0] = true;
        });

        try {
            term.read(Key.CTRL_D);
            term.assertLine(null);
        } catch (Exception e) {
            fail("finish() should not propagate exceptions from closed connection: " + e);
        }
    }

    /**
     * Test that finish() handles IOError from setAttributes() gracefully.
     */
    @Test
    public void testFinishHandlesClosedConnectionIOError() {
        final boolean[] throwOnSetAttributes = { false };

        EnumMap<ReadlineFlag, Integer> flags = new EnumMap<>(ReadlineFlag.class);
        TestReadlineConnection term = new TestReadlineConnection(flags) {
            @Override
            public void setAttributes(org.aesh.terminal.Attributes attr) {
                if (throwOnSetAttributes[0]) {
                    throw new java.io.IOError(new java.io.IOException("FfmPty is closed"));
                }
                super.setAttributes(attr);
            }
        };
        term.setCloseHandler(v -> {
            throwOnSetAttributes[0] = true;
        });

        try {
            term.read(Key.CTRL_D);
            term.assertLine(null);
        } catch (Throwable e) {
            fail("finish() should not propagate IOError from closed connection: " + e);
        }
    }
}
