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
 * <li>ServiceLoader discovers terminal providers</li>
 * <li>No class initialization errors (WinSysTerminal on Linux, #218)</li>
 * <li>native-image.properties is correctly applied</li>
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
        int failures = 0;

        // 1. Verify ServiceLoader finds providers
        int providerCount = 0;
        boolean hasExecPty = false;
        boolean hasFfm = false;
        int ffmPriority = -1;
        boolean ffmSupported = false;
        TerminalProvider highestSupported = null;

        for (TerminalProvider provider : ServiceLoader.load(TerminalProvider.class)) {
            System.out.println("Provider: " + provider.name()
                    + " supported=" + provider.isSupported()
                    + " priority=" + provider.priority());
            providerCount++;
            if ("exec".equals(provider.name())) {
                hasExecPty = true;
            }
            if ("ffm".equals(provider.name())) {
                hasFfm = true;
                ffmPriority = provider.priority();
                ffmSupported = provider.isSupported();
            }
            if (provider.isSupported()) {
                if (highestSupported == null || provider.priority() > highestSupported.priority()) {
                    highestSupported = provider;
                }
            }
        }

        if (providerCount == 0) {
            System.err.println("FAIL: No terminal providers found via ServiceLoader");
            System.exit(1);
        }

        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("win") && !hasExecPty) {
            System.err.println("FAIL: ExecPtyTerminalProvider not found on POSIX system");
            failures++;
        }

        // 2. Verify FFM provider presence and priority (soft check — may not be
        // available if FFM classes were deleted for GraalVM < 25 compatibility)
        if (!os.contains("win")) {
            if (hasFfm) {
                System.out.println("FFM provider: supported=" + ffmSupported + " priority=" + ffmPriority);
                if (ffmPriority != 100) {
                    System.err.println("WARNING: FfmTerminalProvider priority is " + ffmPriority
                            + " (expected 100)");
                }
                if (!ffmSupported) {
                    System.err.println("WARNING: FfmTerminalProvider is not supported on this platform");
                }
            } else {
                System.err.println("WARNING: FfmTerminalProvider not found — FFM classes may have been "
                        + "excluded (expected on GraalVM < 25)");
            }
        }

        // 3. Print which provider would be selected
        if (highestSupported != null) {
            System.out.println("Highest priority supported provider: " + highestSupported.name()
                    + " (priority=" + highestSupported.priority() + ")");
        }

        // 4. Verify reachability-metadata.json is accessible as a resource
        java.net.URL metadata = NativeImageVerify.class.getClassLoader().getResource(
                "META-INF/native-image/org.aesh/terminal-tty/reachability-metadata.json");
        if (metadata != null) {
            System.out.println("Reachability metadata: found");
        } else {
            System.err.println("WARNING: reachability-metadata.json not found as resource "
                    + "(expected — native-image consumes it at build time)");
        }

        // 5. Verify TerminalBuilder can create a terminal
        // (this exercises the full ServiceLoader → provider → terminal chain)
        try {
            org.aesh.terminal.Terminal terminal = org.aesh.terminal.tty.TerminalBuilder.builder()
                    .system(false)
                    .build();
            System.out.println("Terminal: " + terminal.getClass().getName());
            terminal.close();
        } catch (Exception e) {
            System.err.println("FAIL: TerminalBuilder.build() failed: " + e);
            failures++;
        }

        if (failures > 0) {
            System.err.println("FAILED: " + failures + " check(s) failed");
            System.exit(1);
        }
        System.out.println("OK: " + providerCount + " providers found, "
                + (highestSupported != null ? highestSupported.name() : "none") + " selected");
    }
}
