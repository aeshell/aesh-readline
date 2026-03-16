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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.function.Consumer;

import org.aesh.readline.TestTerminal;
import org.aesh.terminal.Attributes;
import org.aesh.terminal.tty.Signal;
import org.aesh.terminal.tty.TerminalConnection;
import org.aesh.terminal.utils.Config;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class TestTerminalConnection {

    @Test
    public void testRead() throws IOException, InterruptedException {
        PipedOutputStream outputStream = new PipedOutputStream();
        PipedInputStream pipedInputStream = new PipedInputStream(outputStream);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        TerminalConnection connection = new TerminalConnection(Charset.defaultCharset(), pipedInputStream,
                byteArrayOutputStream);

        final ArrayList<int[]> result = new ArrayList<>();
        connection.setStdinHandler(result::add);

        outputStream.write(("FOO").getBytes());
        outputStream.flush();
        outputStream.close();
        Thread.sleep(150);
        connection.openBlocking();

        assertArrayEquals(result.get(0), new int[] { 70, 79, 79 });
    }

    @Test
    public void testTestConnection() {
        TestTerminal testConnection = new TestTerminal();
        testConnection.read(read -> {
            assertEquals("foo", read);
        });

        testConnection.write("foo\n");
        testConnection.close();
        testConnection.start();
    }

    @Test
    public void testConnection() {
        TestReadlineConnection test = new TestReadlineConnection();
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

        // Replace Readline behavior: echo ^C when ECHOCTL is set, then send newline
        connection.setSignalHandler(signal -> {
            if (signal == Signal.INT) {
                Attributes attr = connection.attributes();
                if (attr != null && attr.getLocalFlag(Attributes.LocalFlag.ECHOCTL))
                    connection.stdoutHandler().accept(new int[] { '^', 'C' });
                connection.stdoutHandler().accept(Config.CR);
            }
        });

        connection.openNonBlocking();
        outputStream.write(("FOO").getBytes());
        outputStream.flush();
        Thread.sleep(100);
        connection.getTerminal().raise(Signal.INT);
        connection.close();

        Assert.assertEquals("FOO^C" + Config.getLineSeparator(), new String(out.toByteArray()));
    }

    @Test
    public void testSignalEchoCtlFalse() throws IOException, InterruptedException {
        PipedOutputStream outputStream = new PipedOutputStream();
        PipedInputStream pipedInputStream = new PipedInputStream(outputStream);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        TerminalConnection connection = new TerminalConnection(Charset.defaultCharset(), pipedInputStream, out);

        // echo stdin to stdout
        connection.setStdinHandler(cp -> connection.stdoutHandler().accept(cp));

        // on interrupt just write newline
        connection.setSignalHandler(signal -> {
            if (signal == Signal.INT) {
                connection.stdoutHandler().accept(Config.CR);
            }
        });

        connection.openNonBlocking();
        outputStream.write(("FOO").getBytes());
        outputStream.flush();
        Thread.sleep(100);
        connection.getTerminal().raise(Signal.INT);
        connection.close();

        Assert.assertEquals(new String(out.toByteArray()), "FOO" + Config.getLineSeparator());
    }

    @Test
    public void testCustomSignal() throws IOException, InterruptedException {
        PipedOutputStream outputStream = new PipedOutputStream();
        PipedInputStream pipedInputStream = new PipedInputStream(outputStream);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        TerminalConnection connection = new TerminalConnection(Charset.defaultCharset(), pipedInputStream, out);

        // simple line-buffering stdin handler that echoes complete lines only when newline is seen
        final StringBuilder lineBuffer = new StringBuilder();
        connection.setStdinHandler(echoReader(lineBuffer, connection));

        // custom interrupt handling: flush any pending input, write BAR, newline and close
        connection.setSignalHandler(signal -> {
            if (signal == Signal.INT) {
                if (lineBuffer.length() > 0) {
                    int[] pending = lineBuffer.toString().chars().toArray();
                    connection.stdoutHandler().accept(pending);
                    lineBuffer.setLength(0);
                }
                connection.write("BAR");
                connection.stdoutHandler().accept(Config.CR);
                connection.close();
            }
        });

        connection.openNonBlocking();
        outputStream.write(("GAH" + Config.getLineSeparator()).getBytes());
        outputStream.flush();
        Thread.sleep(250);
        assertEquals(new String(out.toByteArray()), "GAH" + Config.getLineSeparator());

        // second read: send partial input then interrupt
        outputStream.write(("FOO").getBytes());
        outputStream.flush();
        connection.getTerminal().raise(Signal.INT);
        Thread.sleep(250);

        assertEquals(new String(out.toByteArray()), "GAH" + Config.getLineSeparator() + "FOOBAR" + Config.getLineSeparator());
    }

    private static Consumer<int[]> echoReader(StringBuilder lineBuffer, TerminalConnection connection) {
        return cp -> {
            for (int c : cp) {
                if (c == '\n' || c == '\r') {
                    if (lineBuffer.length() > 0) {
                        int[] toEcho = lineBuffer.toString().chars().toArray();
                        connection.stdoutHandler().accept(toEcho);
                        lineBuffer.setLength(0);
                    }
                    // echo the newline itself
                    connection.stdoutHandler().accept(Config.CR);
                } else {
                    lineBuffer.append((char) c);
                }
            }
        };
    }

}
