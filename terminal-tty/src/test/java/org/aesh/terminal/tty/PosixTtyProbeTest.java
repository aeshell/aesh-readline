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
package org.aesh.terminal.tty;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.aesh.terminal.utils.OSUtils;
import org.junit.Assume;
import org.junit.Test;

/**
 * Direct tests for the FFM isatty fast path.
 * <p>
 * The contract under test is tri-state: a completed native call answers
 * TRUE/FALSE, while anything else (pre-22 base stub, unsupported host,
 * denied native access, missing binding) declines with null. Tests assert
 * the tri-state explicitly — never just "does not throw".
 */
public class PosixTtyProbeTest {

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

    private static boolean nativeProbeEnabled() {
        if (!isJava22OrLater()) {
            return false;
        }
        if (!OSUtils.IS_FFM_POSIX_SUPPORTED) {
            return false;
        }
        return PosixTtyProbe.isNativeAccessEnabled();
    }

    /**
     * Whether the FFM overlay copy is present in test-classes. The native
     * compatibility profile deletes the overlay for old-GraalVM testing;
     * then even a capable runtime declines through the base stub.
     */
    private static boolean overlayCopyPresent() {
        return new File("target/test-classes/org/aesh/terminal/tty/PosixTtyProbe.class").isFile();
    }

    private static void assumePosixSh() {
        Assume.assumeFalse("native probe fixture needs a POSIX shell",
                OSUtils.IS_WINDOWS);
        Assume.assumeTrue("no executable POSIX shell",
                new File("/bin/sh").canExecute() || new File("/usr/bin/sh").canExecute());
    }

    /**
     * Ground truth for one descriptor: {@code test -t} answers for the
     * inherited fd, so the child must inherit the fd under test.
     */
    private static boolean shellTestT(int fd) throws Exception {
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
        assertTrue("test -t child did not finish", process.waitFor(30, TimeUnit.SECONDS));
        return process.exitValue() == 0;
    }

    @Test
    public void testAnswersForStandardDescriptorsWhenNativeEnabled() {
        Assume.assumeTrue("native probe unavailable (pre-22, no native access, or unsupported host)",
                nativeProbeEnabled());
        for (int fd = 0; fd <= 2; fd++) {
            Boolean answer = PosixTtyProbe.tryIsTty(fd);
            assertNotNull("native probe must answer instead of declining, fd=" + fd, answer);
        }
    }

    @Test
    public void testAgreesWithShellGroundTruth() throws Exception {
        Assume.assumeTrue("native probe unavailable (pre-22, no native access, or unsupported host)",
                nativeProbeEnabled());
        assumePosixSh();
        for (int fd = 0; fd <= 2; fd++) {
            Boolean actual = PosixTtyProbe.tryIsTty(fd);
            assertNotNull("native probe must answer, fd=" + fd, actual);
            assertEquals("isatty must agree with test -t for fd " + fd,
                    shellTestT(fd), actual.booleanValue());
        }
    }

    @Test
    public void testDeclinesWithoutNativeProbe() {
        Assume.assumeFalse("native probe available; decline path untestable here",
                nativeProbeEnabled());
        assertNull(PosixTtyProbe.tryIsTty(0));
        assertNull(PosixTtyProbe.tryIsTty(1));
        assertNull(PosixTtyProbe.tryIsTty(2));
    }

    @Test
    public void testDeniedNativeAccessDeclinesInChildJvm() throws Exception {
        Assume.assumeTrue("needs the Java 22 overlay to deny",
                isJava22OrLater());
        String javaHome = System.getProperty("java.home");
        String executable = System.getProperty("os.name", "").startsWith("Windows") ? "java.exe" : "java";
        File java = new File(new File(javaHome, "bin"), executable);
        List<String> command = new ArrayList<String>();
        command.add(java.getAbsolutePath());
        command.add("--enable-native-access=java.base");
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add("org.aesh.terminal.tty.PosixTtyProbeIdentityCheck");
        ProcessBuilder builder = new ProcessBuilder(command);
        // The test JVM runs with --enable-native-access=ALL-UNNAMED on
        // Java 22+; the child must not inherit any blanket grant.
        builder.environment().remove("JAVA_TOOL_OPTIONS");
        builder.environment().remove("JDK_JAVA_OPTIONS");
        builder.environment().remove("_JAVA_OPTIONS");
        builder.redirectErrorStream(true);
        Process process = builder.start();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int n;
        while ((n = process.getInputStream().read(buf)) != -1) {
            output.write(buf, 0, n);
        }
        if (!process.waitFor(60, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            fail("denied-native-access child did not finish");
        }
        assertEquals("child must exit cleanly", 0, process.exitValue());
        // The overlay loads from the test-classes copy; the native
        // compatibility profile deletes that copy for old-GraalVM testing
        // and the child then declines through the base stub instead.
        boolean overlayExpected = isJava22OrLater() && overlayCopyPresent();
        assertEquals("denied native access must decline on every fd",
                "overlay=" + overlayExpected + " native=false 0:null 1:null 2:null",
                output.toString("UTF-8").trim());
    }
}
