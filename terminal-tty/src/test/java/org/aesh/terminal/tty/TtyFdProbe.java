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

import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

import org.aesh.terminal.utils.PlatformContext;

/**
 * Prints per-fd TTY answers for child-process redirection tests.
 * <p>
 * Launched with controlled stdin/stdout/stderr (PTY, file, or pipe) so
 * mixed combinations are observable; each run is a fresh JVM with cold
 * {@link TtyDetect} caches.
 * <p>
 * Prints three lines, in order:
 * <ol>
 * <li>the {@code TtyDetect} verdict, e.g. {@code 0:true 1:false 2:true 999:false};
 * <li>the independent {@code test -t} agreement line for the same inherited
 * descriptors, e.g. {@code shell 0:true 1:false 2:true 999:false} ({@code null}
 * when the shell probe cannot answer);
 * <li>the identity line proving which binding ran, e.g.
 * {@code identity overlay=true native=true lib=file:/.../terminal-tty-....jar}.
 * </ol>
 * Every line goes to stderr and, when {@code TTY_FD_PROBE_REPORT} points at a
 * file, is also written there. The file is the only channel that survives
 * redirected or closed stderr, so the matrix harness always reads the file.
 * <p>
 * When {@code TTY_FD_PROBE_SKIP_SHELL} is set (to any value), the child omits
 * its own {@code test -t} grandchildren and reports them as null. The
 * no-spawn proof uses this so that only {@code TtyDetect}'s own subprocess
 * use can reach a marker {@code sh} on the path.
 */
public final class TtyFdProbe {

    /**
     * Environment variable naming the report file. The harness sets it to a
     * unique temp file per run; the probe truncates and writes all verdict
     * lines there in addition to stderr.
     */
    static final String REPORT_ENV = "TTY_FD_PROBE_REPORT";

    /**
     * Environment variable opting out of the agreement grandchildren.
     * When set, the shell line reports null instead of spawning.
     */
    static final String SKIP_SHELL_ENV = "TTY_FD_PROBE_SKIP_SHELL";

    private TtyFdProbe() {
    }

    /**
     * Print one verdict triple for the inherited descriptors.
     * <p>
     * The {@code cached} argument switches the tty line to the caching
     * convenience methods ({@code isStdoutTty}/{@code isStderrTty}) instead
     * of per-fd {@code isTty}, exercising the combined shell probe where
     * the platform would spawn. May be combined with a transport argument.
     *
     * @param args optional "cached" mode flag and simulated Windows
     *        transport context ("cygwin" or "windows")
     */
    public static void main(String[] args) {
        boolean cached = false;
        String transport = null;
        for (String arg : args) {
            if ("cached".equals(arg)) {
                cached = true;
            } else if ("cygwin".equals(arg) || "windows".equals(arg)) {
                transport = arg;
            }
        }
        PlatformContext context = PlatformContext.system();
        if (transport != null) {
            context = new PlatformContext("Windows 11", "amd64",
                    "cygwin".equals(transport) ? java.util.Collections.singletonMap("MSYSTEM", "MINGW64")
                            : java.util.Collections.<String, String> emptyMap(),
                    "");
        }
        String tty;
        if (cached) {
            tty = "0:" + TtyDetect.isTty(0, context)
                    + " 1:" + TtyDetect.isStdoutTty(context)
                    + " 2:" + TtyDetect.isStderrTty(context)
                    + " 999:" + TtyDetect.isTty(999, context);
        } else {
            tty = "0:" + TtyDetect.isTty(0, context)
                    + " 1:" + TtyDetect.isTty(1, context)
                    + " 2:" + TtyDetect.isTty(2, context)
                    + " 999:" + TtyDetect.isTty(999, context);
        }
        String shell;
        if (System.getenv(SKIP_SHELL_ENV) != null) {
            shell = "shell 0:null 1:null 2:null 999:null";
        } else {
            shell = "shell 0:" + testT(0)
                    + " 1:" + testT(1)
                    + " 2:" + testT(2)
                    + " 999:" + testT(999);
        }
        String identity = "identity overlay=" + isOverlay() + " native=" + PosixTtyProbe.isNativeAccessEnabled()
                + " lib=" + probeLocation();
        // Keep the verdict visible when stdout itself is redirected.
        System.err.println(tty);
        System.err.println(shell);
        System.err.println(identity);
        String report = System.getenv(REPORT_ENV);
        if (report != null) {
            OutputStream out = null;
            try {
                out = new FileOutputStream(report, false);
                String body = tty + "\n" + shell + "\n" + identity + "\n";
                out.write(body.getBytes("UTF-8"));
                out.flush();
            } catch (Exception e) {
                System.err.println("TtyFdProbe: cannot write report file: " + e);
            } finally {
                if (out != null) {
                    try {
                        out.close();
                    } catch (Exception ignored) {
                        // Report already attempted; stderr carries the verdict.
                    }
                }
            }
        }
    }

    /**
     * Independent ground truth for one descriptor: {@code test -t} answers for
     * the inherited fd, so the grandchild must inherit the fd under test.
     *
     * @param fd the descriptor to test
     * @return TRUE/FALSE from the exit status, or null when sh is missing or
     *         the probe exceeds its deadline
     */
    private static Boolean testT(int fd) {
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
        Process process;
        try {
            process = builder.start();
        } catch (Exception e) {
            return null;
        }
        try {
            byte[] buf = new byte[1024];
            while (process.getInputStream().read(buf) != -1) {
                // Drain; tiny.
            }
            while (process.getErrorStream().read(buf) != -1) {
                // Drain; tiny.
            }
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            return process.exitValue() == 0 ? Boolean.TRUE : Boolean.FALSE;
        } catch (Exception e) {
            process.destroyForcibly();
            return null;
        }
    }

    private static boolean isOverlay() {
        try {
            java.security.CodeSource source = PosixTtyProbe.class.getProtectionDomain().getCodeSource();
            if (source == null || source.getLocation() == null) {
                return false;
            }
            String location = source.getLocation().toString();
            return location.contains("test-classes") || location.endsWith(".jar");
        } catch (SecurityException e) {
            return false;
        }
    }

    private static String probeLocation() {
        try {
            java.security.CodeSource source = PosixTtyProbe.class.getProtectionDomain().getCodeSource();
            if (source == null || source.getLocation() == null) {
                return "none";
            }
            return source.getLocation().toString();
        } catch (SecurityException e) {
            return "denied";
        }
    }
}
