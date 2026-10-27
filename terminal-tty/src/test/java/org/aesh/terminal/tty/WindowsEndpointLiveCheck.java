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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.aesh.terminal.EndpointContext;
import org.aesh.terminal.Terminal;
import org.aesh.terminal.TerminalFeatures;
import org.aesh.terminal.detect.TerminalCapabilities;
import org.aesh.terminal.detect.TerminalTheme;
import org.aesh.terminal.utils.OSUtils;

/**
 * Opt-in Windows field check for endpoint facts, attachment, theme
 * notifications, and bounded full detection (#377, #382).
 * <p>
 * Launch from a real Windows console (conhost, Windows Terminal, Git Bash,
 * standalone mintty), never under Surefire: hosted CI has no window station.
 * Every blocking step is bounded; worst case finishes in about two minutes.
 * The probe writes real OSC/DA1 queries and briefly sets raw mode during
 * full detection; both are restored in {@code finally} blocks.
 * <p>
 * Recipes (from the repository root, after building the packaged jars so
 * the Java 22 FFM/Win32 overlays are visible — exploded
 * {@code target/classes} dirs hide them):
 *
 * <pre>
 *   mvn -DskipTests -Dformat.skip=true -pl terminal-tty -am package
 * </pre>
 * <p>
 * Then from the {@code terminal-tty} directory, on JDK 25 (drop the
 * {@code --enable-native-access} flag on JDK 17; native probe paths then
 * decline gracefully):
 * <p>
 * cmd.exe:
 *
 * <pre>
 *   java --enable-native-access=ALL-UNNAMED -cp ..\terminal-api\target\terminal-api-3.18.8-dev.jar;..\terminal-detect\target\terminal-detect-3.18.8-dev.jar;target\terminal-tty-3.18.8-dev.jar;target\test-classes org.aesh.terminal.tty.WindowsEndpointLiveCheck
 *   echo hi | java --enable-native-access=ALL-UNNAMED -cp ... org.aesh.terminal.tty.WindowsEndpointLiveCheck
 * </pre>
 * <p>
 * PowerShell (quote the classpath):
 *
 * <pre>
 *   java --enable-native-access=ALL-UNNAMED -cp "..\terminal-api\target\terminal-api-3.18.8-dev.jar;..\terminal-detect\target\terminal-detect-3.18.8-dev.jar;target\terminal-tty-3.18.8-dev.jar;target\test-classes" org.aesh.terminal.tty.WindowsEndpointLiveCheck
 * </pre>
 * <p>
 * Git Bash:
 *
 * <pre>
 *   java --enable-native-access=ALL-UNNAMED -cp ../terminal-api/target/terminal-api-3.18.8-dev.jar:../terminal-detect/target/terminal-detect-3.18.8-dev.jar:target/terminal-tty-3.18.8-dev.jar:target/test-classes org.aesh.terminal.tty.WindowsEndpointLiveCheck
 * </pre>
 * <p>
 * Paste everything between the BEGIN/END markers back into the issue,
 * naming the console (conhost, Windows Terminal, Git Bash, mintty) and
 * the exact command line used, including any pipe.
 */
public final class WindowsEndpointLiveCheck {

    private WindowsEndpointLiveCheck() {
    }

    private static void report(String line) {
        System.err.println(line);
    }

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null || value.isEmpty() ? "<unset>" : value;
    }

    private static String hex(int[] rgb) {
        if (rgb == null) {
            return "<unknown>";
        }
        return "#" + String.format("%02X%02X%02X",
                Integer.valueOf(rgb[0]), Integer.valueOf(rgb[1]), Integer.valueOf(rgb[2]));
    }

    public static void main(String[] args) throws Exception {
        if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")) {
            throw new IllegalStateException("Run on Windows (conhost, Windows Terminal, Git Bash, or mintty)");
        }
        report("===== AESH-READLINE WINDOWS FIELD REPORT BEGIN =====");
        try {
            reportEnvironment();
            reportDescriptors();
            reportProvider();
            reportLocalEndpoint();
            reportFullDetection();
            reportInteractive();
        } catch (Throwable t) {
            report("PROBE-FAILED: " + t);
        }
        report("===== AESH-READLINE WINDOWS FIELD REPORT END =====");
    }

    private static void reportEnvironment() {
        try {
            report("os.name=" + System.getProperty("os.name")
                    + " os.version=" + System.getProperty("os.version")
                    + " os.arch=" + System.getProperty("os.arch"));
            report("java.version=" + System.getProperty("java.version")
                    + " file.encoding=" + System.getProperty("file.encoding")
                    + " jvmDefaultCharset=" + java.nio.charset.Charset.defaultCharset());
            report("WT_SESSION=" + env("WT_SESSION")
                    + " WT_PROFILE_ID=" + env("WT_PROFILE_ID")
                    + " TERM_PROGRAM=" + env("TERM_PROGRAM")
                    + " TERM=" + env("TERM")
                    + " MSYSTEM=" + env("MSYSTEM")
                    + " ConEmuANSI=" + env("ConEmuANSI"));
            report("IS_WINDOWS=" + OSUtils.IS_WINDOWS + " IS_CYGWIN=" + OSUtils.IS_CYGWIN);
        } catch (Throwable t) {
            report("SECTION-FAILED environment: " + t);
        }
    }

    private static void reportDescriptors() {
        try {
            for (int fd = 0; fd <= 2; fd++) {
                report("fd" + fd + ".tty=" + TtyDetect.isTty(fd)
                        + " fd" + fd + ".console=" + TtyDetect.isWindowsConsole(fd));
            }
            report("stdinTty=" + TtyDetect.isStdinTty()
                    + " stdoutTty=" + TtyDetect.isStdoutTty()
                    + " stderrTty=" + TtyDetect.isStderrTty());
        } catch (Throwable t) {
            report("SECTION-FAILED descriptors: " + t);
        }
    }

    private static void reportProvider() {
        Terminal terminal = null;
        try {
            terminal = TerminalBuilder.builder().build();
            report("terminal=" + terminal.getClass().getName());
        } catch (Throwable t) {
            report("SECTION-FAILED provider: " + t);
        } finally {
            if (terminal != null) {
                try {
                    terminal.close();
                } catch (Exception e) {
                    report("terminal.close failed: " + e);
                }
            }
        }
    }

    private static void reportLocalEndpoint() {
        TerminalConnection connection = null;
        try {
            connection = new TerminalConnection();
            EndpointContext context = connection.endpointContext();
            report("locality=" + context.locality());
            report("input.terminal=" + context.input().terminal()
                    + " input.terminalSource=" + context.input().terminalSource()
                    + " input.console=" + context.input().nativeConsole()
                    + " input.consoleSource=" + context.input().nativeConsoleSource());
            report("output.terminal=" + context.output().terminal()
                    + " output.terminalSource=" + context.output().terminalSource()
                    + " output.console=" + context.output().nativeConsole()
                    + " output.consoleSource=" + context.output().nativeConsoleSource());
            report("error.terminal=" + context.error().terminal()
                    + " error.terminalSource=" + context.error().terminalSource());
            report("dimensions=" + context.dimensions()
                    + " dimensionsSource=" + context.dimensionsSource());
            report("inputEncoding=" + connection.inputEncoding()
                    + " outputEncoding=" + connection.outputEncoding());
            TerminalFeatures features = connection.terminal();
            report("attached=" + (connection.attachedDetection() != null));
            report("theme=" + features.colorCapability().getTheme()
                    + " colorDepth=" + features.colorDepth()
                    + " imageProtocol=" + features.getImageProtocol());
            report("size=" + connection.size());
        } catch (Throwable t) {
            report("SECTION-FAILED endpoint: " + t);
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }

    private static void reportFullDetection() {
        final AtomicReference<TerminalCapabilities> result = new AtomicReference<TerminalCapabilities>();
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    result.set(TerminalCapabilities.detectFull());
                } catch (Throwable t) {
                    failure.set(t);
                }
            }
        }, "windows-check-full");
        worker.setDaemon(true);
        long start = System.nanoTime();
        worker.start();
        try {
            worker.join(60000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        TerminalCapabilities caps = result.get();
        if (caps == null) {
            report("fullDetection=TIMEOUT after " + elapsedMs + "ms failure=" + failure.get());
            return;
        }
        report("fullDetection done in " + elapsedMs + "ms"
                + " theme=" + caps.theme()
                + " background=" + hex(caps.backgroundRGB())
                + " imageProtocol=" + caps.imageProtocol()
                + " measuredImageProtocol=" + caps.measuredImageProtocol());
    }

    private static void reportInteractive() {
        TerminalConnection connection = null;
        try {
            connection = new TerminalConnection();
            final TerminalConnection owned = connection;
            owned.openNonBlocking();
            final List<String> notifications = Collections.synchronizedList(new ArrayList<String>());
            final CountDownLatch done = new CountDownLatch(1);
            owned.setStdinHandler(new Consumer<int[]>() {
                @Override
                public void accept(int[] input) {
                    for (int codePoint : input) {
                        if (codePoint == '\r' || codePoint == '\n') {
                            done.countDown();
                        }
                    }
                }
            });
            owned.terminal().enableThemeChangeNotification(new Consumer<TerminalTheme>() {
                @Override
                public void accept(TerminalTheme theme) {
                    notifications.add(theme.name());
                }
            });
            try {
                report("ACTION: toggle Settings > Personalization > Colors between Light and Dark, "
                        + "then press Enter here (30s window, or just wait)");
                boolean entered = done.await(30, TimeUnit.SECONDS);
                report("themeCallbacks=" + notifications.size() + " values=" + notifications
                        + " entered=" + entered);
                report("size.before=" + owned.size());
                report("ACTION: resize this window now if you like, then press Enter (30s)");
                CountDownLatch resized = new CountDownLatch(1);
                final CountDownLatch gate = resized;
                owned.setStdinHandler(new Consumer<int[]>() {
                    @Override
                    public void accept(int[] input) {
                        for (int codePoint : input) {
                            if (codePoint == '\r' || codePoint == '\n') {
                                gate.countDown();
                            }
                        }
                    }
                });
                boolean resizedEntered = resized.await(30, TimeUnit.SECONDS);
                report("size.after=" + owned.size() + " entered=" + resizedEntered);
            } finally {
                try {
                    owned.terminal().disableThemeChangeNotification();
                } catch (Throwable t) {
                    report("disableThemeChangeNotification failed: " + t);
                }
            }
        } catch (Throwable t) {
            report("SECTION-FAILED interactive: " + t);
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }
}
