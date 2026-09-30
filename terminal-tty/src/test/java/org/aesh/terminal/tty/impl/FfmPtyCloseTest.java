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
package org.aesh.terminal.tty.impl;

import static org.junit.Assert.assertTrue;

import java.util.concurrent.TimeUnit;

import org.aesh.terminal.utils.OSUtils;
import org.junit.Assume;
import org.junit.Test;

/**
 * Close-race coverage for {@code FfmPty} bulk/single-byte reads.
 * <p>
 * Runs {@link FfmCloseProbe} as a child JVM under {@code script(1)}: a
 * real PTY whose master stays open and idle, so blocked reads can only
 * end via close — the issue's exact masking condition. Gates: Linux
 * only, Java 22+ (the MRJAR layer is invisible otherwise), a working
 * {@code script} binary, and no native-image run.
 */
public class FfmPtyCloseTest {

    private static boolean isNativeImage() {
        return System.getProperty("org.graalvm.nativeimage.imagecode") != null;
    }

    /**
     * Whether the runtime is Java 22 or later (release-8-safe: no
     * {@code Runtime.version()} which needs Java 9+). Since #342 the base
     * layer ships an {@code FfmPty} stub, so mere class presence no longer
     * implies the overlay is active.
     */
    private static boolean isJava22OrLater() {
        String spec = System.getProperty("java.specification.version", "8");
        try {
            if (spec.startsWith("1.")) {
                return false;
            }
            return Integer.parseInt(spec) >= 22;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean canRunScriptMatrix() {
        if (!OSUtils.IS_LINUX || isNativeImage() || !isJava22OrLater()) {
            return false;
        }
        try {
            Class.forName("org.aesh.terminal.tty.impl.FfmPty");
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
        try {
            Process probe = new ProcessBuilder(
                    "script", "-qec", "true", "/dev/null")
                    .redirectErrorStream(true).start();
            byte[] buf = new byte[1024];
            while (probe.getInputStream().read(buf) != -1) {
                // Drain; tiny.
            }
            return probe.waitFor(30, TimeUnit.SECONDS) && probe.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    public void testCloseUnblocksIdleReads() throws Exception {
        Assume.assumeTrue("needs script(1), Linux, and Java 22+", canRunScriptMatrix());
        String inner = System.getProperty("java.home") + "/bin/java"
                + " --enable-native-access=ALL-UNNAMED"
                + " -cp \"" + System.getProperty("java.class.path") + "\""
                + " org.aesh.terminal.tty.impl.FfmCloseProbe";
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
        boolean finished = process.waitFor(120, TimeUnit.SECONDS);
        Assume.assumeTrue("probe child did not finish", finished);
        assertTrue("probe must report success, output was:\n" + output,
                output.toString().contains("FFM-CLOSE-PROBE-OK"));
    }
}
