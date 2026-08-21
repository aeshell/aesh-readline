/*
 * JBoss, Home of Professional Open Source
 * Copyright 2017 Red Hat Inc. and/or its affiliates and other contributors
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
package org.aesh.readline.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.aesh.readline.completion.Completion;
import org.aesh.readline.tty.terminal.TestReadlineConnection;
import org.aesh.terminal.Attributes;
import org.aesh.terminal.Key;
import org.aesh.terminal.tty.Signal;
import org.junit.Test;

/**
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class SignalTest {

    @Test
    public void testSignals() {

        TestReadlineConnection connection = new TestReadlineConnection(null, null, null, null, null, null, null);

        connection.setSignalHandler(signal -> {
            if (signal == Signal.INT)
                connection.write("INTR");
            else if (signal == Signal.EOF)
                connection.write("EOF");
        });

        connection.read("foo");
        assertEquals(": foo", connection.getOutputBuffer());
        connection.read(Key.CTRL_D);
        assertEquals(": fooEOF", connection.getOutputBuffer());
        connection.close();
    }

    @Test
    public void testCustomSignals() {

        Attributes attributes = new Attributes();
        attributes.setControlChar(Attributes.ControlChar.VEOF, Key.CTRL_A.getFirstValue());
        attributes.setControlChar(Attributes.ControlChar.VINTR, Key.CTRL_B.getFirstValue());
        TestReadlineConnection connection = new TestReadlineConnection(null, null, null, null, null, attributes, null);

        connection.setSignalHandler(signal -> {
            if (signal == Signal.INT)
                connection.write("INTR");
            else if (signal == Signal.EOF)
                connection.write("EOF");
        });

        connection.read("foo");
        assertEquals(": foo", connection.getOutputBuffer());
        connection.read(Key.CTRL_B);
        assertEquals(": fooINTR", connection.getOutputBuffer());
        connection.read(Key.CTRL_A);
        assertEquals(": fooINTREOF", connection.getOutputBuffer());
    }

    /**
     * Verify that Ctrl+C delivers an empty string result via finish().
     */
    @Test
    public void testCtrlCDeliversEmptyString() {
        TestReadlineConnection connection = new TestReadlineConnection(null, null, null, null, null, null, null);

        connection.read("hello");
        connection.read(Key.CTRL_C);

        // finish() delivers "" to the request handler
        connection.assertLine("");
    }

    /**
     * Verify that calling close() in the signal handler works without errors.
     * The user's signal handler runs before finish(), so close() may close
     * the terminal before finish() writes cleanup sequences. The cleanup
     * writes should fail silently (logged at FINE level, not WARNING). (#251)
     */
    @Test
    public void testCloseInSignalHandlerNoError() {
        AtomicBoolean closedCleanly = new AtomicBoolean(false);
        AtomicBoolean errorOccurred = new AtomicBoolean(false);

        TestReadlineConnection connection = new TestReadlineConnection(null, null, null, null, null, null, null);

        connection.setSignalHandler(signal -> {
            if (signal == Signal.INT) {
                try {
                    connection.close();
                    closedCleanly.set(true);
                } catch (Exception | java.io.IOError e) {
                    errorOccurred.set(true);
                }
            }
        });

        connection.read("test");
        connection.read(Key.CTRL_C);

        assertTrue("close() in signal handler should complete without error",
                closedCleanly.get());
        assertFalse("No error should occur during close()",
                errorOccurred.get());
    }

    /**
     * Verify that tab completion works after Ctrl+C clears the line
     * and a new readline session is started. This mimics sub-command mode
     * where Ctrl+C clears the current input but doesn't exit — a new
     * readline session starts for the next command.
     *
     * Regression test: if finish() runs before the user's signal handler,
     * it ends the readline session prematurely, and the user's handler
     * cannot keep the sub-command mode active. The completion in the
     * subsequent readline cycle fails.
     */
    @Test
    public void testCompletionWorksAfterCtrlC() {
        List<Completion> completions = new ArrayList<>();
        completions.add(co -> {
            String buf = co.getBuffer();
            for (String cmd : new String[] { "build", "deploy" }) {
                if (cmd.startsWith(buf)) {
                    co.addCompletionCandidate(cmd);
                }
            }
        });

        TestReadlineConnection term = new TestReadlineConnection(completions);

        // Cycle 1: type "bu" + TAB → should complete to "build "
        term.read("bu");
        term.read(Key.CTRL_I);
        term.assertBuffer("build ");

        // Ctrl+C → finish("") clears the line, ends cycle 1
        term.read(Key.CTRL_C);
        term.assertLine("");

        // Cycle 2: start a new readline session with same completions
        term.readline(completions);

        // Type "de" + TAB → should complete to "deploy "
        term.read("de");
        term.read(Key.CTRL_I);
        term.assertBuffer("deploy ");
    }

}
