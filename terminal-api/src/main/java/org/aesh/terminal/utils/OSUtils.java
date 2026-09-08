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
package org.aesh.terminal.utils;

import java.io.File;

/**
 * Operating system detection and command utilities.
 */
public final class OSUtils {

    /**
     * Private constructor to prevent instantiation.
     */
    private OSUtils() {
    }

    /** True if running on Linux. */
    public static final boolean IS_LINUX = System.getProperty("os.name").toLowerCase().contains("linux");

    /** True if running on Windows. */
    public static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");

    /**
     * True if running in a Cygwin, MSYS2, or Git-Bash POSIX environment on Windows.
     * <p>
     * Detection uses multiple indicators because no single env var is reliable
     * across all launch contexts (IDE, cmd.exe, PowerShell, mintty):
     * <ul>
     * <li>{@code MSYSTEM} — set by MSYS2 and Git-Bash (e.g., {@code MINGW64}, {@code UCRT64})</li>
     * <li>{@code CYGWIN} — set in Cygwin environments</li>
     * <li>{@code TERM_PROGRAM=mintty} — the default Cygwin/MSYS2 terminal</li>
     * <li>{@code PWD} starting with {@code /} — legacy heuristic, still valid in many setups</li>
     * </ul>
     */
    public static final boolean IS_CYGWIN = IS_WINDOWS && detectCygwinOrMsys();

    /** True if running on macOS. */
    public static final boolean IS_OSX = System.getProperty("os.name").toLowerCase().contains("mac");

    /** True if running on HP-UX. */
    public static final boolean IS_HPUX = System.getProperty("os.name").toLowerCase().contains("hp-ux");

    /** True if running on SunOS/Solaris. */
    public static final boolean IS_SUNOS = System.getProperty("os.name").toLowerCase().contains("sunos");

    /**
     * Detects Cygwin, MSYS2, or Git-Bash environments on Windows.
     */
    private static boolean detectCygwinOrMsys() {
        // MSYSTEM is set by MSYS2 and Git-Bash (e.g., MINGW64, UCRT64, MSYS)
        if (System.getenv("MSYSTEM") != null) {
            return true;
        }
        // CYGWIN env var is set in Cygwin installations
        if (System.getenv("CYGWIN") != null) {
            return true;
        }
        // mintty is the default terminal for Cygwin/MSYS2
        if ("mintty".equals(System.getenv("TERM_PROGRAM"))) {
            return true;
        }
        // Legacy heuristic: PWD starting with / indicates a POSIX-style path
        String pwd = System.getenv("PWD");
        return pwd != null && pwd.startsWith("/");
    }

    /** Path to the tty command. */
    public static final String TTY_COMMAND;
    /** Path to the stty command. */
    public static final String STTY_COMMAND;
    /** The -f/-F option for stty based on OS. */
    public static final String STTY_F_OPTION;
    /** Path to the infocmp command. */
    public static final String INFOCMP_COMMAND;

    static {
        String tty;
        String stty;
        String sttyfopt;
        String infocmp;
        if (OSUtils.IS_CYGWIN) {
            tty = "tty.exe";
            stty = "stty.exe";
            sttyfopt = null;
            infocmp = "infocmp.exe";
            String path = System.getenv("PATH");
            if (path != null) {
                String[] paths = path.split(";");
                for (String p : paths) {
                    if (new File(p, "tty.exe").exists()) {
                        tty = new File(p, "tty.exe").getAbsolutePath();
                    }
                    if (new File(p, "stty.exe").exists()) {
                        stty = new File(p, "stty.exe").getAbsolutePath();
                    }
                    if (new File(p, "infocmp.exe").exists()) {
                        infocmp = new File(p, "infocmp.exe").getAbsolutePath();
                    }
                }
            }
        } else {
            infocmp = "infocmp";
            if (IS_OSX) {
                // Hardcode BSD paths on macOS to avoid conflict with GNU
                // coreutils stty from Homebrew/Nix which fails on macOS
                // terminal subsystem (#224).
                tty = "/usr/bin/tty";
                stty = "/bin/stty";
                sttyfopt = "-f";
            } else {
                tty = "tty";
                stty = "stty";
                if (IS_HPUX || IS_SUNOS) {
                    sttyfopt = "";
                } else {
                    sttyfopt = "-F";
                }
            }
        }
        TTY_COMMAND = tty;
        STTY_COMMAND = stty;
        STTY_F_OPTION = sttyfopt;
        INFOCMP_COMMAND = infocmp;
    }

}
