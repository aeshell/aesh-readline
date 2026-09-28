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
package org.aesh.terminal.detect;

import java.lang.reflect.Method;

/**
 * Opt-in probe check for a real conhost or Windows Terminal session.
 * Launch directly from that terminal, not under Surefire (which pipes
 * standard handles). Compiles on Java 8 but requires Java 22+ at runtime.
 * Never calls System.console(), which would start the JDK's input pump.
 */
public final class Win32ProbeLiveCheck {

    private Win32ProbeLiveCheck() {
    }

    /**
     * Probe colors and grapheme clustering, checking console-mode restore.
     * Windows Terminal must answer the OSC background query; a legacy
     * conhost may not, but both must restore input and output modes.
     *
     * @param args exactly one argument: "conhost" or "windows-terminal"
     * @throws Exception if a native binding or terminal query fails
     */
    public static void main(String[] args) throws Exception {
        if (args.length != 1 || (!"conhost".equals(args[0]) && !"windows-terminal".equals(args[0]))) {
            throw new IllegalArgumentException("Pass conhost or windows-terminal as the sole argument");
        }
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            throw new IllegalStateException("Run from an interactive Windows console");
        }

        Class<?> probe = Class.forName("org.aesh.terminal.detect.Win32Probe");
        Method getStdHandle = probe.getDeclaredMethod("getStdHandle", int.class);
        Method getConsoleMode = probe.getDeclaredMethod("getConsoleMode", long.class);
        getStdHandle.setAccessible(true);
        getConsoleMode.setAccessible(true);

        long input = (Long) getStdHandle.invoke(null, -10);
        long output = (Long) getStdHandle.invoke(null, -11);
        int savedInput = mode(getConsoleMode, input);
        int savedOutput = mode(getConsoleMode, output);
        if (savedInput == -1 || savedOutput == -1) {
            throw new IllegalStateException("No real console on both stdin and stdout; do not run under Surefire");
        }

        Class<?> type = Class.forName("org.aesh.terminal.detect.Win32ProbeTransport");
        TerminalProbeTransport transport = (TerminalProbeTransport) type.getDeclaredConstructor().newInstance();
        if (!transport.isAvailable()) {
            throw new IllegalStateException("Win32 probe unavailable; check --enable-native-access=ALL-UNNAMED");
        }

        TerminalColorQuery colors;
        boolean grapheme;
        try {
            colors = TerminalColorQuery.query(transport);
            grapheme = TerminalColorQuery.probeGraphemeClustering(transport);
        } finally {
            if (mode(getConsoleMode, input) != savedInput || mode(getConsoleMode, output) != savedOutput) {
                throw new AssertionError("Win32 probe did not restore both console modes");
            }
        }
        if ("windows-terminal".equals(args[0]) && (colors == null || colors.background == null)) {
            throw new AssertionError("Windows Terminal did not answer the OSC background query");
        }
        System.out.println("Win32 probe: mode restored; background="
                + (colors == null || colors.background == null ? "no OSC response" : "received")
                + "; grapheme=" + grapheme);
    }

    private static int mode(Method getConsoleMode, long handle) throws Exception {
        return (Integer) getConsoleMode.invoke(null, handle);
    }
}
