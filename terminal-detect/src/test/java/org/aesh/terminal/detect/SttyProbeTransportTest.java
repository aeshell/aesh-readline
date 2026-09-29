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
package org.aesh.terminal.detect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Failure semantics of {@link SttyProbeTransport} setup, exercised
 * through a fixture command instead of a real terminal.
 * <p>
 * The fixture answers {@code -g} with canned state and logs every other
 * invocation; its raw-mode exit code is fixed per generated script.
 * Fixture tests need a POSIX shell and are gated accordingly — the
 * transport itself is POSIX-only.
 */
public class SttyProbeTransportTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void testRawModeFailureAbortsOpenAndRestores() throws Exception {
        assumePosixShell();
        File dir = tmp.newFolder("stty-fail");
        File log = new File(dir, "invocations.log");
        File command = writeFixture(dir, log, 3, false);
        File device = new File(dir, "tty");
        assertTrue(device.createNewFile());

        try {
            SttyProbeTransport.open(command.getAbsolutePath(), device);
            fail("failing raw-mode setup must not produce a session");
        } catch (IOException e) {
            assertTrue("message must name raw mode: " + e.getMessage(),
                    e.getMessage().contains("raw mode"));
        }

        List<String> invocations = Files.readAllLines(log.toPath(), StandardCharsets.UTF_8);
        assertEquals("raw-mode attempt and restore must both run", 2, invocations.size());
        assertTrue(invocations.get(0).contains("-echo"));
        assertEquals("saved state must be restored after the failure",
                "fixture-state", invocations.get(1).trim());
    }

    @Test
    public void testMissingCommandAbortsOpen() throws Exception {
        File device = new File(tmp.newFolder("stty-missing"), "tty");
        assertTrue(device.createNewFile());
        try {
            SttyProbeTransport.open(new File(tmp.getRoot(), "no-such-stty").getAbsolutePath(), device);
            fail("a missing stty command must not produce a session");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("stty unavailable"));
        }
    }

    @Test
    public void testFixtureOpenWriteClose() throws Exception {
        assumePosixShell();
        File dir = tmp.newFolder("stty-ok");
        File log = new File(dir, "invocations.log");
        File command = writeFixture(dir, log, 0, false);
        File device = new File(dir, "tty");
        assertTrue(device.createNewFile());

        TerminalProbeSession session = SttyProbeTransport.open(command.getAbsolutePath(), device);
        try {
            session.write(new byte[] { 'x' });
            assertTrue(session.input() != null);
        } finally {
            session.close();
        }

        List<String> invocations = Files.readAllLines(log.toPath(), StandardCharsets.UTF_8);
        assertEquals(2, invocations.size());
        assertEquals("fixture-state", invocations.get(1).trim());
    }

    @Test
    public void testInterruptedRawSetupPreservesInterrupt() throws Exception {
        assumePosixShell();
        File dir = tmp.newFolder("stty-interrupt");
        File log = new File(dir, "invocations.log");
        File command = writeFixture(dir, log, 0, true);
        File device = new File(dir, "tty");
        assertTrue(device.createNewFile());

        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean interrupted = new AtomicBoolean();
        String absoluteCommand = command.getAbsolutePath();
        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    SttyProbeTransport.open(absoluteCommand, device);
                    failure.compareAndSet(null,
                            new AssertionError("interrupted setup must not produce a session"));
                } catch (IOException e) {
                    interrupted.set(Thread.currentThread().isInterrupted());
                    if (!e.getMessage().contains("interrupted")) {
                        failure.compareAndSet(null, e);
                    }
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            }
        });
        worker.setDaemon(true);
        worker.start();
        try {
            // Wait until the fixture starts its raw-mode sleep, so the
            // interrupt lands inside waitFor() deterministically.
            long deadline = System.currentTimeMillis() + 5000;
            boolean rawStarted = false;
            while (System.currentTimeMillis() < deadline && !rawStarted) {
                if (log.isFile()) {
                    for (String line : Files.readAllLines(log.toPath(), StandardCharsets.UTF_8)) {
                        if (line.contains("-echo")) {
                            rawStarted = true;
                        }
                    }
                }
                if (!rawStarted) {
                    Thread.sleep(20);
                }
            }
            assertTrue("fixture never reached raw-mode setup", rawStarted);
            worker.interrupt();
            worker.join(10000);
            assertTrue("interrupted setup must finish promptly", !worker.isAlive());
            assertTrue("interrupt flag must survive raw-mode setup", interrupted.get());
            if (failure.get() != null) {
                throw new AssertionError(failure.get());
            }
        } finally {
            worker.interrupt();
        }
    }

    private static void assumePosixShell() {
        Assume.assumeFalse("stty fixture needs a POSIX shell",
                System.getProperty("os.name", "").toLowerCase().contains("win"));
        Assume.assumeTrue("no executable POSIX shell",
                new File("/bin/sh").canExecute());
    }

    /**
     * Generate a fixture stand-in for stty: answers {@code -g} with canned
     * state, logs every other invocation, and exits raw-mode setup with
     * the given code (sleeping first when requested, so interruption can
     * land inside the wait).
     *
     * @param dir the directory for the script and its log
     * @param log the file receiving one line per non -g invocation
     * @param rawExit the exit code for raw-mode setup
     * @param rawSleep whether raw-mode setup sleeps before exiting
     * @return the executable fixture script
     */
    private static File writeFixture(File dir, File log, int rawExit, boolean rawSleep)
            throws IOException {
        File script = new File(dir, "stty");
        FileWriter writer = new FileWriter(script);
        try {
            writer.write("#!/bin/sh\n");
            writer.write("LOG=\"" + log.getAbsolutePath() + "\"\n");
            writer.write("for arg in \"$@\"; do\n");
            writer.write("  if [ \"$arg\" = \"-g\" ]; then echo \"fixture-state\"; exit 0; fi\n");
            writer.write("done\n");
            writer.write("echo \"$@\" >> \"$LOG\"\n");
            if (rawSleep) {
                writer.write("sleep 30\n");
            }
            writer.write("exit " + rawExit + "\n");
        } finally {
            writer.close();
        }
        assertTrue("fixture script must be executable", script.setExecutable(true));
        return script;
    }
}
