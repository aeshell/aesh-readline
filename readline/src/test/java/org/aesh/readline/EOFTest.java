/*
 * Copyright 2012 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.aesh.readline;

import org.aesh.readline.terminal.Key;
import org.aesh.readline.tty.terminal.TestConnection;
import org.junit.Test;

import java.util.EnumMap;

import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class EOFTest {


    @Test
    public void testEOF() {

        final int[] closeCalled = {0};

        EnumMap<ReadlineFlag, Integer> flags = new EnumMap<>(ReadlineFlag.class);
        TestConnection term = new TestConnection(flags);

        term.setCloseHandler(v -> {
            closeCalled[0]++;
        });


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

        final int[] closeCalled = {0};

        EnumMap<ReadlineFlag, Integer> flags = new EnumMap<>(ReadlineFlag.class);
        flags.put(ReadlineFlag.IGNORE_EOF, 2);
        TestConnection term = new TestConnection(flags);

        term.setCloseHandler(v -> {
            closeCalled[0]++;
        });

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

    @Test
    public void testIgnoreEOF2() {

        final int[] closeCalled = {0};

        EnumMap<ReadlineFlag, Integer> flags = new EnumMap<>(ReadlineFlag.class);
        flags.put(ReadlineFlag.IGNORE_EOF, 2);
        TestConnection term = new TestConnection(flags);

        term.setCloseHandler(v -> {
            closeCalled[0]++;
        });

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
