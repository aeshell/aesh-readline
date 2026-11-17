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
import java.util.function.Function;
import java.util.function.IntFunction;

import org.aesh.terminal.utils.PlatformContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Selection tests for the combined stdout+stderr shell probe (#400).
 * <p>
 * The seam under test is the package-private
 * {@code isStdoutTty/isStderrTty(context, nativeProbe, shellProbe,
 * combinedProbe)} overloads: a complete non-null pair fills both
 * per-fd caches from one call, anything else falls back to the
 * individual per-fd chain. Assertions are call counts and preserved
 * answers, never timing. The static caches are saved and restored
 * around every test so the order is irrelevant.
 */
public class TtyDetectCombinedTest {

    private int savedStdin;
    private int savedStdout;
    private int savedStderr;

    @Before
    public void resetCaches() {
        savedStdin = TtyDetect.cachedStdinForTests();
        savedStdout = TtyDetect.cachedStdoutForTests();
        savedStderr = TtyDetect.cachedStderrForTests();
        TtyDetect.cachedStdinForTests(-1);
        TtyDetect.cachedStdoutForTests(-1);
        TtyDetect.cachedStderrForTests(-1);
    }

    @After
    public void restoreCaches() {
        TtyDetect.cachedStdinForTests(savedStdin);
        TtyDetect.cachedStdoutForTests(savedStdout);
        TtyDetect.cachedStderrForTests(savedStderr);
    }

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

    private static IntFunction<Boolean> declinedNative(final AtomicInteger calls) {
        return new IntFunction<Boolean>() {
            @Override
            public Boolean apply(int fd) {
                calls.incrementAndGet();
                return null;
            }
        };
    }

    private static Function<int[], Boolean[]> throwingCombined() {
        return new Function<int[], Boolean[]>() {
            @Override
            public Boolean[] apply(int[] fds) {
                throw new AssertionError("combined probe must not run here");
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

    private static Function<int[], Boolean[]> scriptedCombined(final Boolean[] answers,
            final AtomicInteger calls) {
        return new Function<int[], Boolean[]>() {
            @Override
            public Boolean[] apply(int[] fds) {
                calls.incrementAndGet();
                assertEquals("combined probe must cover stdout+stderr in order",
                        "[1, 2]", java.util.Arrays.toString(fds));
                return answers;
            }
        };
    }

    @Test
    public void testCombinedTrueAnswersBothWithoutFallbacks() {
        AtomicInteger combinedCalls = new AtomicInteger();
        assertTrue("combined TRUE must answer stdout true",
                TtyDetect.isStdoutTty(posix(), throwingNative(), throwingShell(),
                        scriptedCombined(new Boolean[] { Boolean.TRUE, Boolean.FALSE },
                                combinedCalls)));
        assertFalse("combined FALSE must answer stderr false from the same call",
                TtyDetect.isStderrTty(posix(), throwingNative(), throwingShell(),
                        throwingCombined()));
        assertEquals("one combined call must fill both caches", 1, combinedCalls.get());
    }

    @Test
    public void testCombinedFalseAnswersWithoutFallbacks() {
        AtomicInteger combinedCalls = new AtomicInteger();
        assertFalse("combined FALSE must answer stdout false",
                TtyDetect.isStdoutTty(posix(), throwingNative(), throwingShell(),
                        scriptedCombined(new Boolean[] { Boolean.FALSE, Boolean.TRUE },
                                combinedCalls)));
        assertTrue("combined TRUE must answer stderr true from the same call",
                TtyDetect.isStderrTty(posix(), throwingNative(), throwingShell(),
                        throwingCombined()));
        assertEquals("one combined call must fill both caches", 1, combinedCalls.get());
    }

    @Test
    public void testStderrFirstTriggersCombining() {
        AtomicInteger combinedCalls = new AtomicInteger();
        Function<int[], Boolean[]> combined = scriptedCombined(new Boolean[] { Boolean.TRUE, Boolean.TRUE }, combinedCalls);
        assertTrue("stderr-first combined TRUE must answer true",
                TtyDetect.isStderrTty(posix(), throwingNative(), throwingShell(), combined));
        assertTrue("stdout must observe the combined answer without a new call",
                TtyDetect.isStdoutTty(posix(), throwingNative(), throwingShell(),
                        throwingCombined()));
        assertEquals("one combined call must fill both caches", 1, combinedCalls.get());
    }

    @Test
    public void testCombinedNullFallsBackPerFd() {
        AtomicInteger combinedCalls = new AtomicInteger();
        AtomicInteger nativeCalls = new AtomicInteger();
        AtomicInteger shellCalls = new AtomicInteger();
        IntFunction<Boolean> nativeProbe = declinedNative(nativeCalls);
        IntFunction<Boolean> shell = scriptedShell(Boolean.TRUE, shellCalls);
        assertTrue("null combined must fall back to the shell for stdout",
                TtyDetect.isStdoutTty(posix(), nativeProbe, shell,
                        scriptedCombined(null, combinedCalls)));
        assertEquals("native must decline once for stdout", 1, nativeCalls.get());
        assertEquals("shell must run once for stdout", 1, shellCalls.get());
        assertTrue("null combined must fall back to the shell for stderr",
                TtyDetect.isStderrTty(posix(), nativeProbe, shell,
                        scriptedCombined(null, combinedCalls)));
        assertEquals("native must decline once per uncached fd", 2, nativeCalls.get());
        assertEquals("shell must run once per uncached fd", 2, shellCalls.get());
        assertEquals("combined runs once for the first query; the resolved sibling disables it after",
                1, combinedCalls.get());
    }

    @Test
    public void testCombinedPartialAnswerFallsBack() {
        AtomicInteger combinedCalls = new AtomicInteger();
        AtomicInteger nativeCalls = new AtomicInteger();
        AtomicInteger shellCalls = new AtomicInteger();
        IntFunction<Boolean> nativeProbe = declinedNative(nativeCalls);
        IntFunction<Boolean> shell = scriptedShell(Boolean.FALSE, shellCalls);
        Function<int[], Boolean[]> partial = scriptedCombined(new Boolean[] { null, Boolean.TRUE }, combinedCalls);
        assertFalse("partial combined must fall back to the shell for stdout",
                TtyDetect.isStdoutTty(posix(), nativeProbe, shell, partial));
        assertEquals("shell must answer stdout", 1, shellCalls.get());
        assertFalse("partial combined must fall back to the shell for stderr",
                TtyDetect.isStderrTty(posix(), nativeProbe, shell, partial));
        assertEquals("shell must answer stderr", 2, shellCalls.get());
        assertEquals("no partial caching: combined runs once, then disables itself", 1,
                combinedCalls.get());
    }

    @Test
    public void testComputedCacheDisablesCombining() {
        AtomicInteger combinedCalls = new AtomicInteger();
        AtomicInteger nativeCalls = new AtomicInteger();
        AtomicInteger shellCalls = new AtomicInteger();
        TtyDetect.cachedStderrForTests(1);
        assertTrue("pinned stderr cache must read true",
                TtyDetect.isStderrTty(posix(), throwingNative(), throwingShell(),
                        throwingCombined()));
        assertTrue("stdout must probe alone when stderr is already known",
                TtyDetect.isStdoutTty(posix(), declinedNative(nativeCalls),
                        scriptedShell(Boolean.TRUE, shellCalls),
                        scriptedCombined(new Boolean[] { Boolean.TRUE, Boolean.TRUE },
                                combinedCalls)));
        assertEquals("shell must run once for stdout alone", 1, shellCalls.get());
        assertEquals("combined must not run with a computed sibling cache", 0,
                combinedCalls.get());
    }

    @Test
    public void testShouldCombineRouting() {
        assertTrue("Cygwin probes through its POSIX runtime first",
                TtyDetect.shouldCombine(cygwin()));
        assertFalse("native Windows answers per handle without spawning",
                TtyDetect.shouldCombine(windows()));
        assertTrue("unsupported POSIX still spawns, so it still combines",
                TtyDetect.shouldCombine(unsupportedPosix()));
    }
}
