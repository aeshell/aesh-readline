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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntFunction;

import org.aesh.terminal.utils.PlatformContext;
import org.junit.Assume;
import org.junit.Test;

/**
 * Selection tests for the FFM isatty fast path (#399).
 * <p>
 * The seam under test is the package-private
 * {@code isTty(fd, context, nativeProbe, shellProbe)} overload:
 * production passes the real helper and the real {@code test -t}
 * probe, tests pass counted, scripted, or throwing answers.
 * Assertions are call counts and preserved answers, never timing.
 * A throwing probe proves its branch was not entered: no throw
 * escapes, so the selection never consulted it.
 */
public class TtyDetectFfmTest {

    private static PlatformContext posix() {
        return new PlatformContext("Linux", "amd64",
                Collections.<String, String> emptyMap(), "");
    }

    private static PlatformContext unsupportedPosix() {
        return new PlatformContext("SunOS", "sparc",
                Collections.<String, String> emptyMap(), "");
    }

    private static PlatformContext windows() {
        return new PlatformContext("Windows 11", "amd64",
                Collections.<String, String> emptyMap(), "");
    }

    private static PlatformContext cygwin() {
        return new PlatformContext("Windows 11", "amd64",
                Collections.singletonMap("MSYSTEM", "MINGW64"), "");
    }

    private static IntFunction<Boolean> throwingNative() {
        return new IntFunction<Boolean>() {
            @Override
            public Boolean apply(int fd) {
                throw new AssertionError("native probe must not run here, fd=" + fd);
            }
        };
    }

    private static IntFunction<Boolean> throwingShell() {
        return new IntFunction<Boolean>() {
            @Override
            public Boolean apply(int fd) {
                throw new AssertionError("shell probe must not run here, fd=" + fd);
            }
        };
    }

    private static IntFunction<Boolean> scriptedNative(final Boolean answer, final AtomicInteger calls) {
        return new IntFunction<Boolean>() {
            @Override
            public Boolean apply(int fd) {
                calls.incrementAndGet();
                return answer;
            }
        };
    }

    private static IntFunction<Boolean> scriptedShell(final Boolean answer, final AtomicInteger calls) {
        return new IntFunction<Boolean>() {
            @Override
            public Boolean apply(int fd) {
                calls.incrementAndGet();
                return answer;
            }
        };
    }

    @Test
    public void testNativeTrueReturnsWithoutFallbacks() {
        AtomicInteger nativeCalls = new AtomicInteger();
        assertTrue("native TRUE must answer true",
                TtyDetect.isTty(1, posix(), scriptedNative(Boolean.TRUE, nativeCalls), throwingShell()));
        assertEquals("native must run exactly once", 1, nativeCalls.get());
    }

    @Test
    public void testNativeFalseReturnsWithoutFallbacks() {
        AtomicInteger nativeCalls = new AtomicInteger();
        assertFalse("native FALSE must answer false, not fall back",
                TtyDetect.isTty(1, posix(), scriptedNative(Boolean.FALSE, nativeCalls), throwingShell()));
        assertEquals("native must run exactly once", 1, nativeCalls.get());
    }

    @Test
    public void testNativeNullFallsBackToShellTrue() {
        AtomicInteger nativeCalls = new AtomicInteger();
        AtomicInteger shellCalls = new AtomicInteger();
        assertTrue("shell TRUE must survive a native decline",
                TtyDetect.isTty(1, posix(), scriptedNative(null, nativeCalls),
                        scriptedShell(Boolean.TRUE, shellCalls)));
        assertEquals("native must run exactly once", 1, nativeCalls.get());
        assertEquals("shell must run exactly once", 1, shellCalls.get());
    }

    @Test
    public void testNativeNullFallsBackToShellFalse() {
        AtomicInteger nativeCalls = new AtomicInteger();
        AtomicInteger shellCalls = new AtomicInteger();
        assertFalse("shell FALSE must survive a native decline",
                TtyDetect.isTty(1, posix(), scriptedNative(null, nativeCalls),
                        scriptedShell(Boolean.FALSE, shellCalls)));
        assertEquals("native must run exactly once", 1, nativeCalls.get());
        assertEquals("shell must run exactly once", 1, shellCalls.get());
    }

    @Test
    public void testNativeNullShellNullUsesConsoleFallback() {
        Assume.assumeTrue("needs a headless test JVM", System.console() == null);
        AtomicInteger nativeCalls = new AtomicInteger();
        AtomicInteger shellCalls = new AtomicInteger();
        assertFalse("double decline with no console must answer false",
                TtyDetect.isTty(1, posix(), scriptedNative(null, nativeCalls),
                        scriptedShell(null, shellCalls)));
        assertEquals("native must run exactly once", 1, nativeCalls.get());
        assertEquals("shell must run exactly once", 1, shellCalls.get());
    }

    @Test
    public void testInvalidFdsTouchNeitherProbe() {
        for (int fd : new int[] { -1, 3 }) {
            assertFalse("fd " + fd + " must answer false",
                    TtyDetect.isTty(fd, posix(), throwingNative(), throwingShell()));
        }
    }

    @Test
    public void testWindowsNeverEntersPosixProbes() {
        assertEquals("Windows routing must still agree with native-console admission",
                TtyDetect.isWindowsConsole(1),
                TtyDetect.isTty(1, windows(), throwingNative(), throwingShell()));
    }

    @Test
    public void testCygwinNeverEntersNativeProbe() {
        AtomicInteger shellCalls = new AtomicInteger();
        assertTrue("Cygwin shell TRUE must be preserved",
                TtyDetect.isTty(1, cygwin(), throwingNative(),
                        scriptedShell(Boolean.TRUE, shellCalls)));
        assertEquals("shell must run exactly once", 1, shellCalls.get());
    }

    @Test
    public void testUnsupportedPosixSkipsNativeProbe() {
        AtomicInteger shellCalls = new AtomicInteger();
        assertTrue("unsupported POSIX shell TRUE must be preserved",
                TtyDetect.isTty(1, unsupportedPosix(), throwingNative(),
                        scriptedShell(Boolean.TRUE, shellCalls)));
        assertEquals("shell must run exactly once", 1, shellCalls.get());
    }

    @Test
    public void testRepeatedCallsObserveChangingAnswers() {
        final AtomicReference<Boolean> answer = new AtomicReference<Boolean>(Boolean.TRUE);
        AtomicInteger nativeCalls = new AtomicInteger();
        IntFunction<Boolean> nativeProbe = new IntFunction<Boolean>() {
            @Override
            public Boolean apply(int fd) {
                nativeCalls.incrementAndGet();
                return answer.get();
            }
        };
        assertTrue("first call must observe TRUE",
                TtyDetect.isTty(1, posix(), nativeProbe, throwingShell()));
        answer.set(Boolean.FALSE);
        assertFalse("second call must observe FALSE (no stale cache)",
                TtyDetect.isTty(1, posix(), nativeProbe, throwingShell()));
        assertEquals("native must run once per uncached call", 2, nativeCalls.get());
    }

    @Test
    public void testConvenienceCachesRemainPerFd() {
        int oldStdin = TtyDetect.cachedStdinForTests();
        int oldStdout = TtyDetect.cachedStdoutForTests();
        try {
            TtyDetect.cachedStdinForTests(1);
            TtyDetect.cachedStdoutForTests(0);
            assertTrue("pinned stdin cache must read true", TtyDetect.isStdinTty());
            assertFalse("pinned stdout cache must read false independently",
                    TtyDetect.isStdoutTty());
        } finally {
            TtyDetect.cachedStdinForTests(oldStdin);
            TtyDetect.cachedStdoutForTests(oldStdout);
        }
    }
}
