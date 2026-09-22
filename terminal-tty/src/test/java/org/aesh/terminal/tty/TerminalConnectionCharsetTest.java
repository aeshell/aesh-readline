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

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.aesh.terminal.Attributes;
import org.aesh.terminal.tty.impl.ExternalTerminal;
import org.aesh.terminal.tty.impl.PosixSysTerminal;
import org.aesh.terminal.tty.impl.Pty;
import org.junit.Test;

/**
 * Tests for TerminalConnection default charset selection (#280).
 * <p>
 * Cygwin/MSYS2 consoles speak UTF-8 while the JVM default on Windows
 * without beta-UTF-8 is a legacy codepage. Uses a fake Pty so a real
 * PosixSysTerminal constructs headless on all platforms.
 */
public class TerminalConnectionCharsetTest {

    /**
     * Fake PTY with canned attributes and memory streams — no subprocesses,
     * no native calls.
     */
    private static class FakePty implements Pty {
        @Override
        public InputStream getMasterInput() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public OutputStream getMasterOutput() {
            return new ByteArrayOutputStream();
        }

        @Override
        public InputStream getSlaveInput() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public OutputStream getSlaveOutput() {
            return new ByteArrayOutputStream();
        }

        @Override
        public Attributes getAttr() {
            return new Attributes();
        }

        @Override
        public void setAttr(Attributes attr) {
        }

        @Override
        public Size getSize() {
            return new Size(80, 24);
        }

        @Override
        public void close() {
        }
    }

    @Test
    public void testCygwinPosixDefaultsToUtf8() throws IOException {
        PosixSysTerminal term = new PosixSysTerminal("test", "ansi", new FakePty(), false);
        try {
            assertEquals(StandardCharsets.UTF_8,
                    TerminalConnection.defaultConnectionCharset(term, true));
        } finally {
            term.close();
        }
    }

    @Test
    public void testNonCygwinPosixKeepsJvmDefault() throws IOException {
        PosixSysTerminal term = new PosixSysTerminal("test", "ansi", new FakePty(), false);
        try {
            assertEquals(Charset.defaultCharset(),
                    TerminalConnection.defaultConnectionCharset(term, false));
        } finally {
            term.close();
        }
    }

    @Test
    public void testCygwinExternalKeepsJvmDefault() throws IOException {
        ExternalTerminal term = new ExternalTerminal("test", "ansi",
                new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream());
        try {
            assertEquals(Charset.defaultCharset(),
                    TerminalConnection.defaultConnectionCharset(term, true));
        } finally {
            term.close();
        }
    }

    @Test
    public void testConnectionWiringUsesHelper() throws IOException {
        // End-to-end through the public constructor on this (non-Cygwin)
        // platform: unspecified charsets fall back to the JVM default.
        PosixSysTerminal term = new PosixSysTerminal("test", "ansi", new FakePty(), false);
        TerminalConnection conn = new TerminalConnection(term);
        try {
            assertEquals(Charset.defaultCharset(), conn.inputEncoding());
            assertEquals(Charset.defaultCharset(), conn.outputEncoding());
        } finally {
            conn.close();
        }
    }
}
