/*
 * JBoss, Home of Professional Open Source
 * Copyright 2014 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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
package org.aesh.readline.tty.terminal;

import org.aesh.terminal.Attributes;
import org.aesh.utils.Config;
import org.aesh.readline.Prompt;
import org.aesh.readline.Readline;
import org.aesh.readline.TestTerminal;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import org.aesh.terminal.tty.Signal;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class TestTerminalConnection {

    @Test
    public void testRead() throws IOException, InterruptedException {
        PipedOutputStream outputStream = new PipedOutputStream();
        PipedInputStream pipedInputStream = new PipedInputStream(outputStream);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        TerminalConnection connection = new TerminalConnection(Charset.defaultCharset(), pipedInputStream, byteArrayOutputStream);

        final ArrayList<int[]> result = new ArrayList<>();
        connection.setStdinHandler(result::add);

        outputStream.write(("FOO").getBytes());
        outputStream.flush();
        outputStream.close();
        Thread.sleep(150);
        connection.openBlocking();

        assertArrayEquals(result.get(0), new int[] {70,79,79});
    }

    @Test
    public void testTestConnection() {
        TestTerminal testConnection = new TestTerminal();
        testConnection.read( read -> {
            assertEquals("foo", read);
        });

        testConnection.write("foo\n");
        testConnection.close();
        testConnection.start();
    }

    @Test
    public void testConnection() {
        TestConnection test = new TestConnection();
        test.read("foo");
        test.assertBuffer("foo");
        test.assertLine(null);
        test.read("\n");
        test.assertLine("foo");
    }

    @Test
    public void testSignal() throws IOException, InterruptedException {
        PipedOutputStream outputStream = new PipedOutputStream();
        PipedInputStream pipedInputStream = new PipedInputStream(outputStream);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        TerminalConnection connection = new TerminalConnection(Charset.defaultCharset(), pipedInputStream, out);
        Attributes attributes = new Attributes();
        attributes.setLocalFlag(Attributes.LocalFlag.ECHOCTL, true);
        connection.setAttributes(attributes);

        Readline readline = new Readline();
        readline.readline(connection, new Prompt(""), s -> {  });

        connection.openNonBlocking();
        outputStream.write(("FOO").getBytes());
        outputStream.flush();
        Thread.sleep(100);
        connection.getTerminal().raise(Signal.INT);
        connection.close();

        Assert.assertEquals("FOO^C"+ Config.getLineSeparator(), new String(out.toByteArray()));
    }

    @Test
    public void testSignalEchoCtlFalse() throws IOException, InterruptedException {
        PipedOutputStream outputStream = new PipedOutputStream();
        PipedInputStream pipedInputStream = new PipedInputStream(outputStream);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        TerminalConnection connection = new TerminalConnection(Charset.defaultCharset(), pipedInputStream, out);

        Readline readline = new Readline();
        readline.readline(connection, new Prompt(""), s -> {  });

        connection.openNonBlocking();
        outputStream.write(("FOO").getBytes());
        outputStream.flush();
        Thread.sleep(100);
        connection.getTerminal().raise(Signal.INT);
        connection.close();

        Assert.assertEquals(new String(out.toByteArray()), "FOO"+ Config.getLineSeparator());
    }



    @Test
    public void testCustomSignal() throws IOException, InterruptedException {
        PipedOutputStream outputStream = new PipedOutputStream();
        PipedInputStream pipedInputStream = new PipedInputStream(outputStream);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        TerminalConnection connection = new TerminalConnection(Charset.defaultCharset(), pipedInputStream, out);
        connection.setSignalHandler( signal -> {
            if(signal == Signal.INT) {
                connection.write("BAR");
                connection.stdoutHandler().accept(Config.CR);
                connection.close();
            }
        });

        Readline readline = new Readline();
        readline.readline(connection, new Prompt(""), s -> {  });

        connection.openNonBlocking();
        outputStream.write(("GAH"+Config.getLineSeparator()).getBytes());
        outputStream.flush();
        Thread.sleep(250);
        assertEquals(new String(out.toByteArray()), "GAH"+Config.getLineSeparator());

        readline.readline(connection, new Prompt(""), s -> {  });
        outputStream.write(("FOO").getBytes());
        outputStream.flush();
        connection.getTerminal().raise(Signal.INT);
        Thread.sleep(250);

        assertEquals(new String(out.toByteArray()), "GAH"+Config.getLineSeparator()+"FOOBAR"+ Config.getLineSeparator());
    }

}
