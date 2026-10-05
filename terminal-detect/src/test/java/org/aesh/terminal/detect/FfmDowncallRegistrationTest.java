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
package org.aesh.terminal.detect;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Method;

import org.junit.Test;

/**
 * Guards POSIX FFM downcall registration for GraalVM native-image (#301).
 * <p>
 * Availability checks never create a downcall handle, so they cannot catch
 * a missing {@code foreign} entry in {@code reachability-metadata.json}:
 * the failure only surfaces as {@code MissingForeignRegistrationError} when
 * a downcall is actually invoked. This test opens and closes
 * {@code /dev/null} through {@code FfmPosix}, which needs no controlling
 * terminal, so it runs deterministically on JVM CI and proves registration
 * when executed as a native binary.
 * <p>
 * All access is reflective so this test compiles and passes on pre-22
 * runtimes (where {@code FfmPosix} does not exist).
 */
public class FfmDowncallRegistrationTest {

    private static final String FFM_POSIX = "org.aesh.terminal.detect.FfmPosix";

    @Test
    public void testPosixDowncallsRegistered() throws Exception {
        Class<?> ffmPosix = loadFfmPosix();
        if (ffmPosix == null) {
            return;
        }
        if (isWindows()) {
            return;
        }
        if (!isNativeAccessEnabled(ffmPosix)) {
            return;
        }
        Object arena = newConfinedArena();
        Class<?> arenaClass = Class.forName("java.lang.foreign.Arena");
        Class<?> segmentClass = Class.forName("java.lang.foreign.MemorySegment");
        Method arenaClose = arenaClass.getMethod("close");
        Method allocate = arenaClass.getMethod("allocate", long.class, long.class);
        Method open = ffmPosix.getMethod("open", String.class, int.class, arenaClass);
        Method close = ffmPosix.getMethod("close", int.class);
        Method read = ffmPosix.getMethod("read", int.class, segmentClass, long.class);
        Method write = ffmPosix.getMethod("write", int.class, segmentClass, long.class);
        Method tcgetattr = ffmPosix.getMethod("tcgetattr", int.class, segmentClass);
        Method tcsetattr = ffmPosix.getMethod("tcsetattr", int.class, int.class, segmentClass);
        try {
            int fd = (Integer) open.invoke(null, "/dev/null", 2, arena);
            try {
                assertTrue("open(/dev/null) must succeed", fd >= 0);
                Object buffer = allocate.invoke(arena, 8L, 1L);
                Object termios = allocate.invoke(arena, 64L, 8L);
                long written = (Long) write.invoke(null, fd, buffer, 8L);
                assertTrue("write(/dev/null) must succeed", written >= 0);
                long bytesRead = (Long) read.invoke(null, fd, buffer, 8L);
                assertTrue("read(/dev/null) must report EOF", bytesRead == 0);
                // /dev/null is not a terminal: these fail with IOException
                // after linkage, which still proves handle registration.
                try {
                    tcgetattr.invoke(null, fd, termios);
                } catch (Exception expected) {
                    rethrowIfForeignRegistrationMissing(expected);
                }
                try {
                    tcsetattr.invoke(null, fd, 0, termios);
                } catch (Exception expected) {
                    rethrowIfForeignRegistrationMissing(expected);
                }
            } finally {
                if (fd >= 0) {
                    close.invoke(null, fd);
                }
            }
        } catch (Throwable t) {
            if (hasCauseNamed(t, "MissingForeignRegistrationError")) {
                fail("POSIX FFM downcall is not registered for native-image: "
                        + "add its shape to META-INF/native-image/org.aesh/terminal-detect/"
                        + "reachability-metadata.json (#301)");
            }
            if (t instanceof Exception) {
                throw (Exception) t;
            }
            if (t instanceof Error) {
                throw (Error) t;
            }
            throw new AssertionError(t);
        } finally {
            arenaClose.invoke(arena);
        }
    }

    private static Class<?> loadFfmPosix() {
        try {
            return Class.forName(FFM_POSIX);
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static boolean isNativeAccessEnabled(Class<?> ffmPosix) throws Exception {
        Method check = ffmPosix.getMethod("isNativeAccessEnabled");
        return Boolean.TRUE.equals(check.invoke(null));
    }

    private static Object newConfinedArena() throws Exception {
        Class<?> arena = Class.forName("java.lang.foreign.Arena");
        return arena.getMethod("ofConfined").invoke(null);
    }

    private static void rethrowIfForeignRegistrationMissing(Exception e) throws Exception {
        if (hasCauseNamed(e, "MissingForeignRegistrationError")) {
            fail("POSIX FFM downcall is not registered for native-image: "
                    + "add its shape to META-INF/native-image/org.aesh/terminal-detect/"
                    + "reachability-metadata.json (#301)");
        }
    }

    private static boolean hasCauseNamed(Throwable t, String simpleName) {
        while (t != null) {
            if (simpleName.equals(t.getClass().getSimpleName())) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }
}
