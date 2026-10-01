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
package org.aesh.terminal.utils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Immutable snapshot of the platform facts terminal selection needs.
 * <p>
 * Provider eligibility used to read static ambient state directly, so
 * any host-independent selection matrix had to mutate the real
 * environment. A context carries the slow, stable facts (OS name and
 * architecture, environment map, home directory) as injectable
 * literals; live descriptor checks (is this fd a terminal, is this
 * handle a console) stay at the I/O boundary and are never snapshotted.
 * <p>
 * Java 8, zero dependencies beyond {@code java.base}.
 */
public final class PlatformContext {

    private final String osName;
    private final String osArch;
    private final Map<String, String> env;
    private final String userHome;

    /**
     * Create a platform context from explicit values.
     *
     * @param osName the operating system name, as in {@code os.name}
     * @param osArch the operating system architecture, as in {@code os.arch}
     * @param env the environment variables (copied defensively)
     * @param userHome the user home directory
     */
    public PlatformContext(String osName, String osArch, Map<String, String> env, String userHome) {
        this.osName = osName == null ? "" : osName;
        this.osArch = osArch == null ? "" : osArch;
        this.env = Collections.unmodifiableMap(new HashMap<>(env));
        this.userHome = userHome == null ? "" : userHome;
    }

    /**
     * Snapshot the running process: real {@code os.name}/{@code os.arch},
     * {@code System.getenv()}, and {@code user.home}.
     *
     * @return the ambient platform context
     */
    public static PlatformContext system() {
        return new PlatformContext(System.getProperty("os.name", ""),
                System.getProperty("os.arch", ""),
                System.getenv(), System.getProperty("user.home", ""));
    }

    /**
     * Get the operating system name.
     *
     * @return the OS name, never null
     */
    public String osName() {
        return osName;
    }

    /**
     * Get the operating system architecture.
     *
     * @return the OS architecture, never null
     */
    public String osArch() {
        return osArch;
    }

    /**
     * Get the captured environment variables.
     *
     * @return the unmodifiable environment map
     */
    public Map<String, String> env() {
        return env;
    }

    /**
     * Look up one environment variable.
     *
     * @param name the variable name
     * @return the value, or null when absent
     */
    public String env(String name) {
        return env.get(name);
    }

    /**
     * Get the TERM variable.
     *
     * @return the TERM value, or null when absent
     */
    public String term() {
        return env.get("TERM");
    }

    /**
     * Get the captured user home directory.
     *
     * @return the user home, never null
     */
    public String userHome() {
        return userHome;
    }

    /**
     * Whether the OS is Windows (any flavor, including Cygwin/MSYS2).
     *
     * @return true on Windows
     */
    public boolean isWindows() {
        return osName.toLowerCase().contains("win");
    }

    /**
     * Whether the OS is Linux.
     *
     * @return true on Linux
     */
    public boolean isLinux() {
        return osName.toLowerCase().contains("linux");
    }

    /**
     * Whether the OS is macOS.
     *
     * @return true on macOS
     */
    public boolean isMac() {
        String os = osName.toLowerCase();
        return os.startsWith("mac") || os.contains("darwin");
    }

    /**
     * Whether running in a Cygwin, MSYS2, or Git-Bash POSIX environment
     * on Windows. Mirrors the {@code OSUtils} indicators (MSYSTEM,
     * CYGWIN, mintty TERM_PROGRAM, POSIX PWD), evaluated against the
     * captured environment instead of the live one.
     *
     * @return true for Cygwin-like environments on Windows
     */
    public boolean isCygwin() {
        if (!isWindows()) {
            return false;
        }
        if (env.get("MSYSTEM") != null) {
            return true;
        }
        if (env.get("CYGWIN") != null) {
            return true;
        }
        if ("mintty".equals(env.get("TERM_PROGRAM"))) {
            return true;
        }
        String pwd = env.get("PWD");
        return pwd != null && pwd.startsWith("/");
    }

    /**
     * Whether the FFM-based POSIX backends may be selected: Linux/macOS
     * on 64-bit x86/ARM only. Anything else would take an unimplemented
     * struct layout and must decline to the portable fallback.
     *
     * @return true for implemented OS/ABI combinations
     */
    public boolean isFfmPosixSupported() {
        return OSUtils.isFfmPosixSupported(osName, osArch);
    }
}
