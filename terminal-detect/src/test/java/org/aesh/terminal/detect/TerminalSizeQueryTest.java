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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Slice 1 size reads: {@code stty size} parsing, fixture fallback,
 * headless decline, and the never-throws contract.
 * <p>
 * All stty legs run through a fixture command, never a real
 * terminal. FFM legs are covered reflectively in
 * {@link FfmTerminalSizeTest}.
 */
public class TerminalSizeQueryTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void testParseSttySize() {
        assertArrayEquals(new int[] { 80, 24 },
                TerminalSizeQuery.parseSttySize("24 80\n"));
        assertArrayEquals(new int[] { 132, 50 },
                TerminalSizeQuery.parseSttySize("50 132"));
        assertArrayEquals(new int[] { 80, 24 },
                TerminalSizeQuery.parseSttySize("  24   80  \n"));
    }

    @Test
    public void testParseSttySizeRejectsGarbage() {
        assertNull(TerminalSizeQuery.parseSttySize(null));
        assertNull(TerminalSizeQuery.parseSttySize(""));
        assertNull(TerminalSizeQuery.parseSttySize("   \n"));
        assertNull(TerminalSizeQuery.parseSttySize("80"));
        assertNull(TerminalSizeQuery.parseSttySize("24 80 extra"));
        assertNull(TerminalSizeQuery.parseSttySize("rows cols"));
        assertNull(TerminalSizeQuery.parseSttySize("0 80"));
        assertNull(TerminalSizeQuery.parseSttySize("24 0"));
        assertNull(TerminalSizeQuery.parseSttySize("-1 80"));
        assertNull(TerminalSizeQuery.parseSttySize("24 -5"));
    }

    @Test
    public void testToSizeRejectsUnmeasurable() {
        assertFalse(TerminalSizeQuery.toSize(null).isPresent());
        assertFalse(TerminalSizeQuery.toSize(new int[] { 80 }).isPresent());
        assertFalse(TerminalSizeQuery.toSize(
                new int[] { 80, 24, 1 }).isPresent());
        assertFalse(TerminalSizeQuery.toSize(
                new int[] { 0, 24 }).isPresent());
        assertFalse(TerminalSizeQuery.toSize(
                new int[] { 80, -1 }).isPresent());
        Optional<TerminalSize> size = TerminalSizeQuery.toSize(
                new int[] { 132, 50 });
        assertTrue(size.isPresent());
        assertEquals(132, size.get().width());
        assertEquals(50, size.get().height());
    }

    @Test
    public void testWindowsBranchDeclinesHeadless() {
        Assume.assumeFalse("a real Windows console may answer",
                System.getProperty("os.name", "").toLowerCase()
                        .contains("win"));
        Optional<TerminalSize> size = TerminalSizeQuery.query(
                "Windows 11");
        assertFalse("headless Windows branch must decline",
                size.isPresent());
    }

    @Test
    public void testWindowsBranchNeverThrows() {
        Optional<TerminalSize> size = TerminalSizeQuery.query(
                "Windows 11");
        if (size.isPresent()) {
            assertTrue(size.get().width() > 0);
            assertTrue(size.get().height() > 0);
        }
    }

    @Test
    public void testSttyFixtureAnswersSize() throws Exception {
        assumePosixShell();
        File dir = tmp.newFolder("stty-size-ok");
        File device = new File(dir, "tty");
        assertTrue(device.createNewFile());
        File command = writeSizeFixture(dir, "24 80", 0);
        int[] dims = TerminalSizeQuery.querySttySize(
                device, command.getAbsolutePath(), 2000);
        assertArrayEquals(new int[] { 80, 24 }, dims);
    }

    @Test
    public void testSttyFixtureFailureDeclines() throws Exception {
        assumePosixShell();
        File dir = tmp.newFolder("stty-size-fail");
        File device = new File(dir, "tty");
        assertTrue(device.createNewFile());
        File command = writeSizeFixture(dir, "24 80", 3);
        assertNull(TerminalSizeQuery.querySttySize(
                device, command.getAbsolutePath(), 2000));
    }

    @Test
    public void testSttyFixtureGarbageDeclines() throws Exception {
        assumePosixShell();
        File dir = tmp.newFolder("stty-size-garbage");
        File device = new File(dir, "tty");
        assertTrue(device.createNewFile());
        File command = writeSizeFixture(dir, "not a size", 0);
        assertNull(TerminalSizeQuery.querySttySize(
                device, command.getAbsolutePath(), 2000));
    }

    @Test
    public void testMissingTtyDeclinesWithoutSpawning() {
        final List<String> commands = new ArrayList<>();
        ProcessRunner.setInstalled(
                new ProcessRunner.RunnerTransport() {
                    @Override
                    public ProcessRunner.Result run(
                            ProcessBuilder starter, long timeoutMs) {
                        commands.add(String.join(" ",
                                starter.command()));
                        return new ProcessRunner.Result(0, false,
                                "24 80\n".getBytes(
                                        StandardCharsets.US_ASCII));
                    }
                });
        try {
            File missing = new File(tmp.getRoot(), "no-such-tty");
            assertNull(TerminalSizeQuery.querySttySize(
                    missing, "stty", 2000));
            assertTrue("missing tty must not spawn a process: "
                    + commands, commands.isEmpty());
        } finally {
            ProcessRunner.setInstalled(null);
        }
    }

    @Test
    public void testMissingCommandDeclines() throws Exception {
        File device = new File(tmp.newFolder("stty-size-missing"),
                "tty");
        assertTrue(device.createNewFile());
        assertNull(TerminalSizeQuery.querySttySize(device,
                new File(tmp.getRoot(), "no-such-stty")
                        .getAbsolutePath(),
                2000));
    }

    @Test
    public void testQueryTerminalSizeNeverThrows() {
        Optional<TerminalSize> size = TerminalCapabilities
                .queryTerminalSize();
        if (size.isPresent()) {
            assertTrue(size.get().width() > 0);
            assertTrue(size.get().height() > 0);
        }
    }

    private static void assumePosixShell() {
        Assume.assumeFalse("stty fixture needs a POSIX shell",
                System.getProperty("os.name", "").toLowerCase()
                        .contains("win"));
        Assume.assumeTrue("no executable POSIX shell",
                new File("/bin/sh").canExecute());
    }

    /**
     * Fixture stand-in for stty: answers {@code size} with canned
     * output and exit code.
     *
     * @param dir the directory for the script
     * @param sizeOutput the canned {@code size} reply
     * @param sizeExit the exit code for the {@code size} invocation
     * @return the executable fixture script
     */
    private static File writeSizeFixture(File dir, String sizeOutput,
            int sizeExit) throws IOException {
        File script = new File(dir, "stty");
        FileWriter writer = new FileWriter(script);
        try {
            writer.write("#!/bin/sh\n");
            writer.write("for arg in \"$@\"; do\n");
            writer.write("  if [ \"$arg\" = \"size\" ]; then\n");
            writer.write("    printf '%s\\n' '" + sizeOutput.trim() + "';\n");
            writer.write("    exit " + sizeExit + "\n");
            writer.write("  fi\n");
            writer.write("done\n");
            writer.write("exit " + sizeExit + "\n");
        } finally {
            writer.close();
        }
        assertTrue("fixture script must be executable",
                script.setExecutable(true));
        return script;
    }
}
