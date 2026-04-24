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

import java.nio.file.FileSystems;

/**
 * Configuration utility class providing system and environment information
 * such as OS type, line separators, and path separators.
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class Config {

    private Config() {
    }

    private static final boolean macOS = OSUtils.IS_OSX;
    private static final boolean windows = OSUtils.IS_WINDOWS;
    private static final String lineSeparator = System.lineSeparator();
    private static final String pathSeparator = FileSystems.getDefault().getSeparator();
    private static final String tmpDir = System.getProperty("java.io.tmpdir");
    private static final boolean posixCompatible = checkPosixCompability();

    /**
     * The line separator as an array of code points.
     */
    public static final int[] CR = lineSeparator.codePoints().toArray();
    private static final boolean cygwin = OSUtils.IS_CYGWIN;

    /**
     * Check if the operating system is POSIX compatible.
     *
     * @return true if the OS is POSIX compatible, false otherwise
     */
    public static boolean isOSPOSIXCompatible() {
        return posixCompatible;
    }

    /**
     * Check if the environment is running under Cygwin.
     *
     * @return true if running under Cygwin, false otherwise
     */
    public static boolean isCygwin() {
        return cygwin;
    }

    /**
     * Check if the operating system is macOS.
     *
     * @return true if the OS is macOS, false otherwise
     */
    public static boolean isMacOS() {
        return macOS;
    }

    /**
     * Check if the operating system is Windows.
     *
     * @return true if the OS is Windows, false otherwise
     */
    public static boolean isWindows() {
        return windows;
    }

    /**
     * Get the system line separator.
     *
     * @return the line separator string for the current platform
     */
    public static String getLineSeparator() {
        return lineSeparator;
    }

    /**
     * Get the system path separator.
     *
     * @return the file path separator string for the current platform
     */
    public static String getPathSeparator() {
        return pathSeparator;
    }

    /**
     * Get the system temporary directory.
     *
     * @return the path to the system temporary directory
     */
    public static String getTmpDir() {
        return tmpDir;
    }

    /**
     * Get the user's home directory.
     *
     * @return the path to the user's home directory
     */
    public static String getHomeDir() {
        return System.getProperty("user.home");
    }

    /**
     * Get the current user directory (working directory).
     *
     * @return the path to the current user directory
     */
    public static String getUserDir() {
        return System.getProperty("user.dir");
    }

    private static boolean checkPosixCompability() {
        if (isWindows()) {
            return OSUtils.IS_CYGWIN;
        } else
            return !System.getProperty("os.name").startsWith("OS/2");
    }

}
