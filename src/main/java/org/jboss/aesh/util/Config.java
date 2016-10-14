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
package org.jboss.aesh.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;

/**
 *
 * @author Ståle W. Pedersen <stale.pedersen@jboss.org>
 */
public class Config {

    private static final boolean windows = System.getProperty("os.name").startsWith("Windows");
    private static final String lineSeparator = System.getProperty("line.separator");
    private static final String pathSeparator = System.getProperty("file.separator");
    private static final String tmpDir = System.getProperty("java.io.tmpdir");
    private static final boolean posixCompatible = checkPosixCompability();
    public static final int[] CR = Parser.toCodePoints(lineSeparator);
    private static boolean cygwin = false;

    private static final Logger LOGGER = LoggerUtil.getLogger(Config.class.getName());

    public static boolean isOSPOSIXCompatible() {
        return posixCompatible;
    }

    public static boolean isCygwin() {
        return cygwin;
    }

    public static boolean isWindows() {
        return windows;
    }

    public static String getLineSeparator() {
        return lineSeparator;
    }

    public static String getPathSeparator() {
        return pathSeparator;
    }

    public static String getTmpDir() {
        return tmpDir;
    }

    public static String getHomeDir() {
        return System.getProperty("user.home");
    }

    public static String getUserDir() {
        return System.getProperty("user.dir");
    }

    private static boolean checkPosixCompability() {
        if(isWindows()) {
            //need to check if we're running under cygwin
            try {
                Process process = Runtime.getRuntime().exec(new String[]{"uname"});
                ByteArrayOutputStream bout = new ByteArrayOutputStream();
                int c;
                InputStream in = process.getInputStream();
                while ((c = in.read()) != -1) {
                    bout.write(c);
                }
                process.waitFor();

                String output = new String(bout.toByteArray());
                if(output.toLowerCase().contains("cygwin")) {
                    cygwin = true;
                    return true;
                }
            }
            catch (IOException | InterruptedException e) {
                //silently ignore that we're not running cygwin
            }

            return false;
        }
        else
            return !System.getProperty("os.name").startsWith("OS/2");
    }

}
