package org.aesh.terminal.detect;

import static org.junit.Assert.*;

import java.io.File;
import java.nio.charset.StandardCharsets;

import org.junit.Assume;
import org.junit.Test;

public class TerminalDiscoveryColdStartTest {

    @Test(timeout = 60000)
    public void testColdParallelMixedFactories() throws Exception {
        Assume.assumeTrue("fresh child-JVM class initialization is verified by JVM suites",
                System.getProperty("org.graalvm.nativeimage.imagecode") == null);
        String executable = System.getProperty("os.name", "").startsWith("Windows") ? "java.exe" : "java";
        File java = new File(new File(System.getProperty("java.home"), "bin"), executable);
        ProcessBuilder builder = new ProcessBuilder(java.getAbsolutePath(), "-cp", System.getProperty("java.class.path"),
                TerminalDiscoveryColdChild.class.getName());
        builder.environment().remove("TMUX");
        builder.environment().remove("STY");
        builder.environment().put("TERM", "xterm-256color");
        ProcessRunner.Result result = ProcessRunner.execute(builder, 30000);
        String output = result.text(StandardCharsets.UTF_8);
        assertFalse("cold child timed out: " + output, result.timedOut());
        assertEquals("cold child failed: " + output, 0, result.exitCode());
        assertTrue("the barrier must actually release", output.contains("barrier-release"));
        assertTrue("all workers and the measured session must execute: " + output,
                output.contains("cold-mixed workers=8 opens=1 writes=1 closes=1"));
    }
}
