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

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.aesh.terminal.Terminal;

/**
 * Close-race probe for {@code FfmPty}, run as a child JVM under a real
 * PTY ({@code script(1)} holding the master open, idle slave, no writer).
 * Talks only through the release-8 {@link Pty} interface plus reflective
 * {@code current()}, so this file compiles on any Java version.
 * <p>
 * Every phase is join-bounded: on the fixed code the whole run takes a
 * few seconds; on code with unbounded waits the joins expire instead of
 * hanging the suite. Prints {@code FFM-CLOSE-PROBE-OK} on success and
 * exits nonzero otherwise.
 */
public final class FfmCloseProbe {

    private static final List<String> failures = new ArrayList<>();

    private FfmCloseProbe() {
    }

    /**
     * Run the close-race phases against a fresh PTY per phase.
     *
     * @param args ignored
     * @throws Exception on unexpected errors (fails the run)
     */
    public static void main(String[] args) throws Exception {
        Class<?> impl = Class.forName("org.aesh.terminal.tty.impl.FfmPty");
        Method current = impl.getMethod("current");

        Pty idle = (Pty) current.invoke(null);
        try {
            timedSanity(idle);
        } finally {
            idle.close();
        }

        Pty single = (Pty) current.invoke(null);
        singleByteCloseRace(single);

        Pty bulk = (Pty) current.invoke(null);
        bulkCloseRace(bulk);
        bulk.close();
        bulk.close();

        try {
            bulk.read(new byte[8], 0, 8, 0);
            fail("post-close read must throw");
        } catch (java.io.IOException expected) {
        }

        if (!failures.isEmpty()) {
            System.out.println("FFM-CLOSE-PROBE-FAIL");
            for (String failure : failures) {
                System.out.println("FAIL: " + failure);
            }
            System.exit(1);
        }
        System.out.println("FFM-CLOSE-PROBE-OK");
    }

    private static void timedSanity(Pty pty) throws Exception {
        byte[] buf = new byte[1024];
        long start = System.currentTimeMillis();
        int result = pty.read(buf, 0, buf.length, 200);
        long elapsed = System.currentTimeMillis() - start;
        check(result == Terminal.READ_EXPIRED, "timed bulk read must expire, got " + result);
        check(elapsed < 2000, "timed bulk read took " + elapsed + "ms");
        start = System.currentTimeMillis();
        int peeked = pty.peek(200);
        elapsed = System.currentTimeMillis() - start;
        check(peeked == Terminal.READ_EXPIRED, "timed peek must expire, got " + peeked);
        check(elapsed < 2000, "timed peek took " + elapsed + "ms");
    }

    private static void singleByteCloseRace(final Pty pty) throws Exception {
        final int[] result = new int[] { Integer.MIN_VALUE };
        final Throwable[] failure = new Throwable[1];
        Thread reader = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    result[0] = pty.read(-1);
                } catch (Throwable t) {
                    failure[0] = t;
                }
            }
        });
        reader.setDaemon(true);
        reader.start();
        Thread.sleep(500);
        pty.close();
        reader.join(15000);
        check(!reader.isAlive(), "single-byte infinite read still blocked after close");
        check(failure[0] == null, "single-byte reader threw: " + failure[0]);
        check(result[0] == -1, "single-byte read after close must be EOF, got " + result[0]);
    }

    private static void bulkCloseRace(final Pty pty) throws Exception {
        final InputStream in = pty.getSlaveInput();
        final int[] result = new int[] { Integer.MIN_VALUE };
        final Throwable[] failure = new Throwable[1];
        Thread reader = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    result[0] = in.read(new byte[1024]);
                } catch (Throwable t) {
                    failure[0] = t;
                }
            }
        });
        reader.setDaemon(true);
        reader.start();
        Thread.sleep(500);
        pty.close();
        reader.join(15000);
        check(!reader.isAlive(), "idle bulk read still blocked after close");
        check(failure[0] == null, "bulk reader threw: " + failure[0]);
        check(result[0] == -1, "bulk read after close must be EOF, got " + result[0]);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            failures.add(message);
        }
    }

    private static void fail(String message) {
        failures.add(message);
    }
}
