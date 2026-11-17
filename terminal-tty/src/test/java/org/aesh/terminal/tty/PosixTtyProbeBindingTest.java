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
package org.aesh.terminal.tty;

import static org.junit.Assert.assertNotNull;

import org.junit.Assume;
import org.junit.Test;

/**
 * Proves the FFM isatty binding executes where the overlay loads.
 * <p>
 * The overlay's handle initializer swallows every linkage failure and
 * declines with null, so a missing {@code foreign} downcall registration
 * in {@code reachability-metadata.json} never throws — the fast path
 * silently degrades to the shell. A public {@code isTty} returning false
 * therefore cannot tell a working native negative from missing
 * registration quietly falling back. This test calls the helper directly
 * and requires a non-null answer wherever the overlay loads with native
 * access enabled.
 * <p>
 * The primary audience is the enabled native test binary
 * ({@code -Dnative.delete.ffm=false} lane): the same assertion also runs
 * in ordinary JVM suites, where it duplicates the direct helper tests on
 * purpose — a green run consisting of skips or fallback is not evidence.
 * The compatibility binary (overlay deleted for old GraalVM) and runtimes
 * without native access skip; fallback coverage for those lives in the fd
 * matrix and the decline tests.
 */
public class PosixTtyProbeBindingTest {

    private static boolean isJava22OrLater() {
        String spec = System.getProperty("java.specification.version", "8");
        try {
            if (spec.startsWith("1.")) {
                return false;
            }
            return Integer.parseInt(spec) >= 22;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Test
    public void testBindingExecutesWhereOverlayLoads() {
        Assume.assumeTrue("needs Java 22+ FFM", isJava22OrLater());
        try {
            Class.forName("org.aesh.terminal.tty.PosixTtyProbe$Handles");
        } catch (ClassNotFoundException | LinkageError e) {
            Assume.assumeTrue("FFM overlay deleted for compatibility; fallback covered elsewhere",
                    false);
        }
        Assume.assumeTrue("native access required", PosixTtyProbe.isNativeAccessEnabled());
        assertNotNull("FFM isatty binding must execute, not decline: the downcall descriptor "
                + "is missing from reachability-metadata.json or the holder pin is wrong",
                PosixTtyProbe.tryIsTty(1));
    }
}
