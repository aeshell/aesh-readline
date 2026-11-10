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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.junit.Test;

/**
 * FFM size path guards (slice 1 POSIX).
 * <p>
 * All access is reflective so this test compiles and passes on
 * pre-22 runtimes (where {@code FfmPosix} does not exist). The
 * ioctl registration leg proves the new downcall shape is present
 * in {@code reachability-metadata.json} when run as a native
 * binary, mirroring {@link FfmDowncallRegistrationTest}.
 */
public class FfmTerminalSizeTest {

    private static final String FFM_POSIX = "org.aesh.terminal.detect.FfmPosix";
    private static final String FFM_SIZE = "org.aesh.terminal.detect.FfmTerminalSize";

    @Test
    public void testWinsizeConstants() throws Exception {
        Class<?> ffmPosix = loadFfmPosix();
        if (ffmPosix == null) {
            return;
        }
        boolean macos = System.getProperty("os.name", "")
                .toLowerCase().startsWith("mac")
                || System.getProperty("os.name", "").toLowerCase()
                        .contains("darwin");
        int request = (Integer) field(ffmPosix, "TIOCGWINSZ").get(null);
        assertEquals(macos ? 0x40087468 : 0x5413, request);
        assertEquals(8L, field(ffmPosix, "WINSIZE_SIZE").get(null));
        assertEquals(0L, field(ffmPosix, "WINSIZE_ROW_OFFSET").get(null));
        assertEquals(2L, field(ffmPosix, "WINSIZE_COL_OFFSET").get(null));
        assertEquals(0, field(ffmPosix, "O_RDONLY").get(null));
    }

    @Test
    public void testWinsizeStructPacking() throws Exception {
        Class<?> ffmPosix = loadFfmPosix();
        if (ffmPosix == null) {
            return;
        }
        if (isWindows() || !isNativeAccessEnabled(ffmPosix)) {
            return;
        }
        Class<?> arenaClass = Class.forName("java.lang.foreign.Arena");
        Class<?> segmentClass = Class.forName(
                "java.lang.foreign.MemorySegment");
        Class<?> layoutClass = Class.forName(
                "java.lang.foreign.ValueLayout");
        Object arena = arenaClass.getMethod("ofConfined").invoke(null);
        try {
            Object ws = arenaClass.getMethod("allocate", long.class,
                    long.class).invoke(arena, 8L, 2L);
            Object javaShort = layoutClass.getField("JAVA_SHORT").get(null);
            Method set = segmentClass.getMethod("set",
                    Class.forName("java.lang.foreign.ValueLayout$OfShort"),
                    long.class, short.class);
            Method get = segmentClass.getMethod("get",
                    Class.forName("java.lang.foreign.ValueLayout$OfShort"),
                    long.class);
            set.invoke(ws, javaShort, 0L, (short) 50);
            set.invoke(ws, javaShort, 2L, (short) 132);
            assertEquals((short) 50, get.invoke(ws, javaShort, 0L));
            assertEquals((short) 132, get.invoke(ws, javaShort, 2L));
        } finally {
            arenaClass.getMethod("close").invoke(arena);
        }
    }

    @Test
    public void testIoctlDowncallRegistered() throws Exception {
        Class<?> ffmPosix = loadFfmPosix();
        if (ffmPosix == null) {
            return;
        }
        if (isWindows() || !isNativeAccessEnabled(ffmPosix)) {
            return;
        }
        Class<?> arenaClass = Class.forName("java.lang.foreign.Arena");
        Class<?> segmentClass = Class.forName(
                "java.lang.foreign.MemorySegment");
        Object arena = arenaClass.getMethod("ofConfined").invoke(null);
        try {
            Method open = ffmPosix.getMethod("open", String.class,
                    int.class, arenaClass);
            Method close = ffmPosix.getMethod("close", int.class);
            Method ioctl = ffmPosix.getMethod("ioctl", int.class,
                    long.class, segmentClass);
            Method allocate = arenaClass.getMethod("allocate", long.class,
                    long.class);
            int fd = (Integer) open.invoke(null, "/dev/null", 0, arena);
            try {
                assertTrue("/dev/null must open", fd >= 0);
                Object ws = allocate.invoke(arena, 8L, 2L);
                Field requestField = field(ffmPosix, "TIOCGWINSZ");
                long request = ((Number) requestField.get(null))
                        .longValue();
                try {
                    ioctl.invoke(null, fd, request, ws);
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
                fail("ioctl downcall is not registered for "
                        + "native-image: add jint/jlong/void* to "
                        + "reachability-metadata.json (#394)");
            }
            if (t instanceof Exception) {
                throw (Exception) t;
            }
            if (t instanceof Error) {
                throw (Error) t;
            }
            throw new AssertionError(t);
        } finally {
            arenaClass.getMethod("close").invoke(arena);
        }
    }

    @Test
    public void testFfmQueryNeverThrows() throws Exception {
        Class<?> sizeQuery;
        try {
            sizeQuery = Class.forName(FFM_SIZE);
        } catch (ClassNotFoundException | LinkageError e) {
            return;
        }
        if (isWindows()) {
            return;
        }
        try {
            Object result = sizeQuery.getDeclaredMethod("query")
                    .invoke(null);
            if (result != null) {
                int[] dims = (int[]) result;
                assertEquals(2, dims.length);
                assertTrue(dims[0] > 0);
                assertTrue(dims[1] > 0);
            }
        } catch (Exception e) {
            fail("FfmTerminalSize.query() must decline, never throw: "
                    + e);
        }
    }

    private static Class<?> loadFfmPosix() {
        try {
            return Class.forName(FFM_POSIX);
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    private static Field field(Class<?> clazz, String name)
            throws Exception {
        Field field = clazz.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase()
                .contains("win");
    }

    private static boolean isNativeAccessEnabled(Class<?> ffmPosix)
            throws Exception {
        Method check = ffmPosix.getMethod("isNativeAccessEnabled");
        return Boolean.TRUE.equals(check.invoke(null));
    }

    private static void rethrowIfForeignRegistrationMissing(
            Exception e) throws Exception {
        if (hasCauseNamed(e, "MissingForeignRegistrationError")) {
            fail("ioctl downcall is not registered for native-image: "
                    + "add jint/jlong/void* to "
                    + "reachability-metadata.json (#394)");
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
