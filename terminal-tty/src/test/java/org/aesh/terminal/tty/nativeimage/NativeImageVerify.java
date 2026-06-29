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
package org.aesh.terminal.tty.nativeimage;

import java.util.ServiceLoader;

import org.aesh.terminal.provider.TerminalProvider;

/**
 * Minimal verification app for GraalVM native-image testing.
 * <p>
 * This class is intentionally in a named package (not the default package)
 * because GraalVM treats default-package and named-package classes differently
 * for native-image.properties processing (#209).
 * <p>
 * Run as a native-image built from the released JAR to verify:
 * <ul>
 *   <li>ServiceLoader discovers terminal providers</li>
 *   <li>No class initialization errors (WinSysTerminal on Linux, #218)</li>
 *   <li>native-image.properties is correctly applied</li>
 * </ul>
 */
public class NativeImageVerify {

    /**
     * Entry point for native-image verification.
     *
     * @param args command line arguments (unused)
     */
    public static void main(String[] args) {
        System.out.println("=== Native Image Verification ===");

        // 1. Verify ServiceLoader finds providers
        int providerCount = 0;
        boolean hasExecPty = false;
        for (TerminalProvider provider : ServiceLoader.load(TerminalProvider.class)) {
            System.out.println("Provider: " + provider.name()
                    + " supported=" + provider.isSupported()
                    + " priority=" + provider.priority());
            providerCount++;
            if ("exec".equals(provider.name())) {
                hasExecPty = true;
            }
        }

        if (providerCount == 0) {
            System.err.println("FAIL: No terminal providers found via ServiceLoader");
            System.exit(1);
        }

        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("win") && !hasExecPty) {
            System.err.println("FAIL: ExecPtyTerminalProvider not found on POSIX system");
            System.exit(1);
        }

        // 2. Verify TerminalBuilder can create a terminal
        // (this exercises the full ServiceLoader → provider → terminal chain)
        try {
            org.aesh.terminal.Terminal terminal = org.aesh.terminal.tty.TerminalBuilder.builder()
                    .system(false)
                    .build();
            System.out.println("Terminal: " + terminal.getClass().getName());
            terminal.close();
        } catch (Exception e) {
            System.err.println("FAIL: TerminalBuilder.build() failed: " + e);
            System.exit(1);
        }

        System.out.println("OK: " + providerCount + " providers found");
    }
}
