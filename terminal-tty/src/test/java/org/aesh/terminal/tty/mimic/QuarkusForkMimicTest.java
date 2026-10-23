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
package org.aesh.terminal.tty.mimic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.junit.Assume;
import org.junit.Test;

public class QuarkusForkMimicTest {

    @Test(timeout = 45000)
    public void testParallelRunsBeforeSequentialAndTtyStatus() throws Exception {
        Assume.assumeTrue("cold-start ordering needs a child JVM",
                System.getProperty("org.graalvm.nativeimage.imagecode") == null);
        Path output = Files.createTempFile("mimic-order-", ".log");
        Process process = null;
        try {
            String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
            File java = new File(new File(System.getProperty("java.home"), "bin"), executable);
            ProcessBuilder builder = new ProcessBuilder(java.getAbsolutePath(), "-cp",
                    System.getProperty("java.class.path"), QuarkusForkMimic.class.getName(), "both", "1", "2", "15");
            builder.environment().remove("AESH_TTY_PROBE_MARKER_DIR");
            process = builder.redirectErrorStream(true).redirectOutput(output.toFile()).start();
            assertTrue("mimic did not finish", process.waitFor(30, TimeUnit.SECONDS));
            String text = new String(Files.readAllBytes(output), StandardCharsets.UTF_8);
            assertEquals(text, 0, process.exitValue());
            int parallel = text.indexOf("MIMIC-PHASE parallel-cold-first");
            int sequential = text.indexOf("MIMIC-PHASE sequential-warm");
            int tty = text.indexOf("MIMIC-PHASE cached-tty-status");
            assertTrue(text, parallel >= 0 && sequential > parallel && tty > sequential);
            assertTrue(text, text.indexOf("MIMIC-ENV stdinTty=") > tty);
            assertEquals(text, 20, text.split("MIMIC-OK parallel build ", -1).length - 1);
            assertEquals(text, 1, text.split("MIMIC-OK build ", -1).length - 1);
            assertTrue(text, text.contains("MIMIC-OK all builds completed within their watchdog"));
        } finally {
            if (process != null) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
            Files.deleteIfExists(output);
        }
    }

    @Test
    public void testMissingProbeMarkersFail() throws Exception {
        Path directory = Files.createTempDirectory("mimic-markers-");
        try {
            try {
                QuarkusForkMimic.verifyProbeMarkers(directory.toFile());
                fail("an unexecuted probe fixture must not pass");
            } catch (IllegalStateException expected) {
                assertTrue(expected.getMessage(), expected.getMessage().contains("did not execute for fd 0"));
            }
        } finally {
            Files.deleteIfExists(directory);
        }
    }

    @Test
    public void testRequiresValidMarkersForBothDescriptors() throws Exception {
        Path directory = Files.createTempDirectory("mimic-markers-");
        Path stdin = directory.resolve("123-0.started");
        Path stdout = directory.resolve("124-1.started");
        try {
            Files.write(stdin, "fd=0\n".getBytes(StandardCharsets.US_ASCII));
            try {
                QuarkusForkMimic.verifyProbeMarkers(directory.toFile());
                fail("stdin execution alone must not satisfy the stdout canary");
            } catch (IllegalStateException expected) {
                assertTrue(expected.getMessage(), expected.getMessage().contains("did not execute for fd 1"));
            }
            Files.write(stdout, "fd=1\n".getBytes(StandardCharsets.US_ASCII));
            QuarkusForkMimic.verifyProbeMarkers(directory.toFile());
            Files.write(stdout, "not a probe\n".getBytes(StandardCharsets.US_ASCII));
            try {
                QuarkusForkMimic.verifyProbeMarkers(directory.toFile());
                fail("a file name alone must not satisfy the execution canary");
            } catch (IllegalStateException expected) {
                assertTrue(expected.getMessage(), expected.getMessage().contains("invalid probe fixture marker"));
            }
        } finally {
            Files.deleteIfExists(stdin);
            Files.deleteIfExists(stdout);
            Files.deleteIfExists(directory);
        }
    }

}
