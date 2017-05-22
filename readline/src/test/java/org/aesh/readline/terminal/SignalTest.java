/*
 * Copyright 2012 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.aesh.readline.terminal;

import org.aesh.readline.tty.terminal.TestConnection;
import org.aesh.terminal.Attributes;
import org.aesh.terminal.tty.Signal;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class SignalTest {

    @Test
    public void testSignals() {

        TestConnection connection = new TestConnection(null, null, null, null, null, null);

        connection.setSignalHandler(signal -> {
            if(signal == Signal.INT)
                connection.write("INTR");
            else if(signal == Signal.EOF)
                connection.write("EOF");
        });

        connection.read("foo");
        assertEquals(": foo", connection.getOutputBuffer());
        connection.read(Key.CTRL_D);
        assertEquals(": fooEOF", connection.getOutputBuffer());
    }

    @Test
    public void testCustomSignals() {

        Attributes attributes = new Attributes();
        attributes.setControlChar(Attributes.ControlChar.VEOF, Key.CTRL_A.getFirstValue());
        attributes.setControlChar(Attributes.ControlChar.VINTR, Key.CTRL_B.getFirstValue());
        TestConnection connection = new TestConnection(null, null, null, null, null, attributes);

        connection.setSignalHandler(signal -> {
            if(signal == Signal.INT)
                connection.write("INTR");
            else if(signal == Signal.EOF)
                connection.write("EOF");
        });

        connection.read("foo");
        assertEquals(": foo", connection.getOutputBuffer());
        connection.read(Key.CTRL_B);
        assertEquals(": fooINTR", connection.getOutputBuffer());
        connection.read(Key.CTRL_A);
        assertEquals(": fooINTREOF", connection.getOutputBuffer());
    }


}
