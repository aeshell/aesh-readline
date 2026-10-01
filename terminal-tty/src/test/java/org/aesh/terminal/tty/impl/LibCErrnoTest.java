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
package org.aesh.terminal.tty.impl;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.aesh.terminal.utils.OSUtils;
import org.junit.Assume;
import org.junit.Test;

/**
 * Errno reporting for the FFM open() wrapper (#348).
 * <p>
 * The per-call errno capture was removed from every wrapper except
 * {@code open()}, which now reports the captured errno in its exception
 * instead of discarding it. All access is reflective so this test
 * compiles on pre-22 runtimes (where the overlay does not exist) and
 * needs no PTY: opening a missing path fails deterministically.
 */
public class LibCErrnoTest {

    @Test
    public void testOpenFailureReportsErrno() throws Exception {
        Assume.assumeTrue("POSIX FFM open is Linux/macOS only",
                OSUtils.IS_LINUX || OSUtils.IS_OSX);
        Class<?> libC;
        try {
            libC = Class.forName("org.aesh.terminal.tty.impl.LibC");
        } catch (ClassNotFoundException | LinkageError e) {
            Assume.assumeTrue("requires the Java 22+ FFM overlay", false);
            return;
        }
        Class<?> arenaClass = Class.forName("java.lang.foreign.Arena");
        Object arena = arenaClass.getMethod("ofConfined").invoke(null);
        try {
            Method open = libC.getDeclaredMethod("open", arenaClass, String.class, int.class);
            open.setAccessible(true);
            try {
                // O_RDWR is 2 on both Linux and macOS; flags are
                // irrelevant — lookup of the missing path fails first.
                open.invoke(null, arena, "/definitely/not/a/tty-348", 2);
                fail("open() of a missing path must throw");
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                assertTrue("expected RuntimeException, got " + cause,
                        cause instanceof RuntimeException);
                assertTrue("message must carry errno=2 (ENOENT): " + cause.getMessage(),
                        cause.getMessage() != null && cause.getMessage().contains("errno=2"));
            }
        } finally {
            arenaClass.getMethod("close").invoke(arena);
        }
    }
}
