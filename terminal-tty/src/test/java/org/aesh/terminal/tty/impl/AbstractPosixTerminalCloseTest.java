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
package org.aesh.terminal.tty.impl;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.aesh.terminal.Attributes;
import org.aesh.terminal.tty.Size;
import org.junit.Test;

/**
 * Tests for {@link AbstractPosixTerminal#close()} cleanup ordering (#336):
 * a failing attribute restore must not skip the underlying PTY close.
 */
public class AbstractPosixTerminalCloseTest {

    /**
     * PTY with injectable restore/close failures and benign streams.
     */
    private static class FailingPty implements Pty {
        final AtomicReference<IOException> setAttrFailure = new AtomicReference<>();
        final AtomicReference<IOException> closeFailure = new AtomicReference<>();
        final AtomicBoolean closed = new AtomicBoolean();

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
        public void setAttr(Attributes attr) throws IOException {
            IOException failure = setAttrFailure.get();
            if (failure != null) {
                throw failure;
            }
        }

        @Override
        public Size getSize() {
            return new Size(80, 24);
        }

        @Override
        public void close() throws IOException {
            closed.set(true);
            IOException failure = closeFailure.get();
            if (failure != null) {
                throw failure;
            }
        }
    }

    @Test
    public void testRestoreFailureStillClosesPty() throws IOException {
        FailingPty pty = new FailingPty();
        PosixSysTerminal term = new PosixSysTerminal("test", "ansi", pty, false);
        IOException restoreFailure = new IOException("restore blew up");
        pty.setAttrFailure.set(restoreFailure);
        try {
            term.close();
            assertTrue("close() must propagate the restore failure", false);
        } catch (IOException e) {
            assertSame(restoreFailure, e);
            assertTrue(e.getSuppressed().length == 0);
        }
        assertTrue("pty.close() must run despite restore failure", pty.closed.get());
    }

    @Test
    public void testBothFailuresSuppress() throws IOException {
        FailingPty pty = new FailingPty();
        PosixSysTerminal term = new PosixSysTerminal("test", "ansi", pty, false);
        IOException restoreFailure = new IOException("restore blew up");
        IOException closeFailure = new IOException("close blew up");
        pty.setAttrFailure.set(restoreFailure);
        pty.closeFailure.set(closeFailure);
        try {
            term.close();
            assertTrue("close() must propagate the restore failure", false);
        } catch (IOException e) {
            assertSame(restoreFailure, e);
            assertSame(closeFailure, e.getSuppressed()[0]);
        }
        assertTrue(pty.closed.get());
    }

    @Test
    public void testCleanClosePropagatesNothing() throws IOException {
        FailingPty pty = new FailingPty();
        PosixSysTerminal term = new PosixSysTerminal("test", "ansi", pty, false);
        term.close();
        assertTrue(pty.closed.get());
    }
}
