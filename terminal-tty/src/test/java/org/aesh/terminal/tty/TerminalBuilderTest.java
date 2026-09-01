/*
 * JBoss, Home of Professional Open Source
 * Copyright 2026 Red Hat Inc. and/or its affiliates and other contributors
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
package org.aesh.terminal.tty;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

import org.aesh.terminal.Terminal;
import org.aesh.terminal.tty.impl.ExternalTerminal;
import org.aesh.terminal.tty.impl.WinExternalTerminal;
import org.aesh.terminal.utils.OSUtils;
import org.junit.Test;

/**
 * Tests for TerminalBuilder TTY detection.
 * <p>
 * Under Maven surefire (forked JVM) stdin is piped, so the default
 * builder must produce an external terminal instead of grabbing the
 * controlling TTY (regression test for redirected stdin/stdout being
 * bypassed via /dev/tty). The TTY case can only be verified when
 * running interactively (e.g., from an IDE).
 */
public class TerminalBuilderTest {

    private static boolean isNativeImage() {
        return System.getProperty("org.graalvm.nativeimage.imagecode") != null;
    }

    private static boolean underSurefire() {
        return System.getProperty("surefire.real.class.path") != null
                || System.getenv("MAVEN_CMD_LINE_ARGS") != null;
    }

    private static boolean isExternalTerminal(Terminal terminal) {
        return terminal instanceof ExternalTerminal || terminal instanceof WinExternalTerminal;
    }

    /**
     * Whether the current process has a controlling TTY. Without one, the
     * system terminal providers fail and the builder falls back to an
     * external terminal regardless of the TTY detection, so the regression
     * case (piped stdin must NOT attach to a live TTY) can only be
     * distinguished when a controlling TTY actually exists.
     */
    private static boolean hasControllingTty() {
        if (OSUtils.IS_WINDOWS) {
            // Windows always has a console; use the TTY probe instead.
            return TtyDetect.isStdinTty() || TtyDetect.isStdoutTty();
        }
        try (FileInputStream tty = new FileInputStream("/dev/tty")) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * When stdin is piped (as in surefire) but a controlling TTY exists,
     * the default builder must not attach to that TTY — it must wrap the
     * given streams in an external terminal.
     * <p>
     * This is the core regression test: before the fix, {@code /dev/tty}
     * was opened directly, bypassing the redirected stdin/stdout.
     */
    @Test
    public void testPipedStdinYieldsExternalTerminal() throws IOException {
        assumeFalse("native-image runtime differs", isNativeImage());
        assumeTrue("requires piped stdin (run under surefire)", underSurefire());
        assumeTrue("requires a controlling TTY to be distinguishable from the provider fallback",
                hasControllingTty());
        assertFalse("sanity check: stdin must be piped in surefire", TtyDetect.isStdinTty());

        Terminal terminal = TerminalBuilder.builder().build();
        try {
            assertTrue("Expected external terminal for piped stdin but got "
                    + terminal.getClass().getSimpleName(), isExternalTerminal(terminal));
        } finally {
            terminal.close();
        }
    }

    /**
     * When stdin is a real TTY (interactive run), the default builder
     * must still produce a system terminal. Only verifiable outside
     * surefire, so this is skipped in CI.
     */
    @Test
    public void testTtyStdinYieldsSystemTerminal() throws IOException {
        assumeFalse("native-image runtime differs", isNativeImage());
        if (!TtyDetect.isStdinTty()) {
            return; // no TTY available — nothing to verify
        }
        Terminal terminal = TerminalBuilder.builder().build();
        try {
            assertFalse("Expected system terminal for TTY stdin but got "
                    + terminal.getClass().getSimpleName(), isExternalTerminal(terminal));
        } finally {
            terminal.close();
        }
    }

    /**
     * An explicit system(true) must bypass the TTY detection and keep
     * attempting system terminal providers even when stdin is piped.
     */
    @Test
    public void testExplicitSystemTrueBypassesTtyCheck() throws IOException {
        assumeFalse("native-image runtime differs", isNativeImage());
        Terminal terminal = TerminalBuilder.builder().system(true).build();
        assertNotNull(terminal);
        terminal.close();
    }

    /**
     * Explicit non-standard streams must always yield an external
     * terminal, regardless of TTY state.
     */
    @Test
    public void testExplicitStreamsYieldExternalTerminal() throws IOException {
        assumeFalse("native-image runtime differs", isNativeImage());
        PipedOutputStream stdinWriter = new PipedOutputStream();
        PipedInputStream stdinReader = new PipedInputStream(stdinWriter, 4096);
        ByteArrayOutputStream stdoutCapture = new ByteArrayOutputStream();

        Terminal terminal = TerminalBuilder.builder()
                .input(stdinReader)
                .output(stdoutCapture)
                .build();
        try {
            assertTrue("Expected external terminal for explicit streams but got "
                    + terminal.getClass().getSimpleName(), isExternalTerminal(terminal));
        } finally {
            terminal.close();
        }
    }
}
