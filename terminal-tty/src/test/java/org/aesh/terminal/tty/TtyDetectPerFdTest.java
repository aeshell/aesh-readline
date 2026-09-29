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
import static org.junit.Assert.assertFalse;

import java.io.File;
import java.util.concurrent.TimeUnit;

import org.aesh.terminal.utils.OSUtils;
import org.junit.Assume;
import org.junit.Test;

/**
 * Per-fd TTY detection: each descriptor must answer for itself.
 * <p>
 * The ground-truth legs agree with {@code test -t} in whatever environment
 * runs them (PTY or pipes), so they need no fixture terminal. The mixed
 * redirection matrix spawns a child JVM under {@code script} (real PTY)
 * with stdin redirected, which the old global answer got wrong.
 */
public class TtyDetectPerFdTest {

    private static boolean isNativeImage() {
        return System.getProperty("org.graalvm.nativeimage.imagecode") != null;
    }

    private static void assumePosixSh() {
        Assume.assumeFalse("POSIX shell probing needs non-Windows", OSUtils.IS_WINDOWS);
        Assume.assumeTrue("no executable POSIX shell",
                new File("/bin/sh").canExecute() || new File("/usr/bin/sh").canExecute());
    }

    /**
     * Run {@code test -t} directly: the ground truth TtyDetect must agree with.
     * The tested descriptor is inherited, or the child would probe a pipe.
     */
    private static boolean shellGroundTruth(int fd) throws Exception {
        ProcessBuilder builder = new ProcessBuilder("sh", "-c", "test -t " + fd);
        if (fd == 0) {
            builder.redirectInput(ProcessBuilder.Redirect.INHERIT);
        }
        if (fd == 1) {
            builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        }
        if (fd == 2) {
            builder.redirectError(ProcessBuilder.Redirect.INHERIT);
        }
        Process process = builder.start();
        byte[] buf = new byte[64];
        while (process.getInputStream().read(buf) != -1) {
            // Drain; tiny.
        }
        while (process.getErrorStream().read(buf) != -1) {
            // Drain; tiny.
        }
        process.waitFor(10, TimeUnit.SECONDS);
        return process.exitValue() == 0;
    }

    @Test
    public void testAgreesWithShellGroundTruth() throws Exception {
        assumePosixSh();
        for (int fd = 0; fd <= 2; fd++) {
            assertEquals("fd " + fd + " must match test -t",
                    shellGroundTruth(fd), TtyDetect.isTty(fd));
        }
    }

    @Test
    public void testInvalidFdsAlwaysFalse() {
        // No environment, native image, or platform may report these true.
        // No subprocess runs for negatives; bogus positives fail here.
        assertFalse(TtyDetect.isTty(-1));
        assertFalse(TtyDetect.isTty(99));
        assertFalse(TtyDetect.isTty(999));
        assertFalse(TtyDetect.isTty(Integer.MAX_VALUE));
    }

    private static Boolean scriptUsable;

    private static boolean canRunScriptMatrix() {
        if (OSUtils.IS_WINDOWS || isNativeImage()) {
            return false;
        }
        if (scriptUsable != null) {
            return scriptUsable;
        }
        scriptUsable = false;
        try {
            // Self-gate on the exact invocation shape (util-linux and
            // BSD spellings differ); doubles as the binary probe.
            Process probe = new ProcessBuilder(
                    "script", "-qec", "true", "/dev/null")
                    .redirectErrorStream(true).start();
            byte[] buf = new byte[1024];
            while (probe.getInputStream().read(buf) != -1) {
                // Drain; tiny.
            }
            scriptUsable = probe.waitFor(30, TimeUnit.SECONDS) && probe.exitValue() == 0;
        } catch (Exception e) {
            scriptUsable = false;
        }
        return scriptUsable;
    }

    /**
     * Run the probe child under a real PTY with the given inner command,
     * returning its verdict line (e.g. {@code 0:false 1:true 2:true}).
     */
    private static String runProbe(String inner) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(
                "script", "-qec", inner, "/dev/null");
        builder.redirectErrorStream(true);
        Process process = builder.start();
        StringBuilder output = new StringBuilder();
        byte[] buf = new byte[4096];
        int n;
        while ((n = process.getInputStream().read(buf)) != -1) {
            output.append(new String(buf, 0, n));
        }
        boolean finished = process.waitFor(60, TimeUnit.SECONDS);
        Assume.assumeTrue("probe child did not finish", finished);
        String verdict = null;
        for (String line : output.toString().split("\\r?\\n")) {
            if (line.startsWith("0:")) {
                verdict = line.trim();
            }
        }
        assertFalse("probe printed no verdict line", verdict == null);
        return verdict;
    }

    private static String probeCommand() {
        return System.getProperty("java.home") + "/bin/java"
                + " -cp " + System.getProperty("java.class.path")
                + " org.aesh.terminal.tty.TtyFdProbe";
    }

    @Test
    public void testAllPtyReportsAllTrue() throws Exception {
        Assume.assumeTrue("needs script(1) for a real PTY", canRunScriptMatrix());
        assertEquals("0:true 1:true 2:true 999:false", runProbe(probeCommand()));
    }

    @Test
    public void testRedirectedStdinKeepsPtyStdout() throws Exception {
        Assume.assumeTrue("needs script(1) for a real PTY", canRunScriptMatrix());
        File input = File.createTempFile("tty-stdin", ".txt");
        input.deleteOnExit();
        // Redirect only stdin to a file; stdout/stderr stay on the PTY.
        // Quoting stays trivial: temp paths contain no spaces.
        String inner = "sh -c '" + probeCommand()
                + " < " + input.getAbsolutePath() + "'";
        assertEquals("0:false 1:true 2:true 999:false", runProbe(inner));
    }
}
