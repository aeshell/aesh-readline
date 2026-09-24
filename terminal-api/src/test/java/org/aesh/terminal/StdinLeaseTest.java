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
package org.aesh.terminal;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.aesh.terminal.tty.Capability;
import org.aesh.terminal.tty.Size;
import org.junit.Test;

/**
 * Tests for {@link Connection#captureStdin} scoped leases (#291).
 */
public class StdinLeaseTest {

    private static class FakeConnection extends AbstractConnection {
        FakeConnection() {
            eventDecoder = new EventDecoder(new Attributes());
        }

        @Override
        public java.nio.charset.Charset inputEncoding() {
            return java.nio.charset.StandardCharsets.UTF_8;
        }

        @Override
        public java.nio.charset.Charset outputEncoding() {
            return java.nio.charset.StandardCharsets.UTF_8;
        }

        @Override
        public Device device() {
            return null;
        }

        @Override
        public Size size() {
            return new Size(80, 24);
        }

        @Override
        public boolean put(Capability capability, Object... params) {
            return false;
        }

        @Override
        public void openBlocking() {
        }

        @Override
        public void openNonBlocking() {
        }

        @Override
        public void close() {
        }

        @Override
        public boolean supportsAnsi() {
            return false;
        }
    }

    private static Consumer<int[]> recorder(List<int[]> received) {
        return received::add;
    }

    private static int[] codePoints(String text) {
        return text.codePoints().toArray();
    }

    @Test
    public void testLeaseRestoresPreviousHandler() {
        FakeConnection conn = new FakeConnection();
        Consumer<int[]> steady = new ArrayList<int[]>()::add;
        Consumer<int[]> leased = new ArrayList<int[]>()::add;
        conn.setStdinHandler(steady);

        try (StdinLease ignored = conn.captureStdin(leased)) {
            assertSame("Lease handler must be current inside the lease",
                    leased, conn.stdinHandler());
        }
        assertSame("Previous handler instance must be restored",
                steady, conn.stdinHandler());
    }

    @Test
    public void testRestoreIdentity() {
        FakeConnection conn = new FakeConnection();
        Consumer<int[]> steady = new ArrayList<int[]>()::add;
        conn.setStdinHandler(steady);
        Consumer<int[]> leased = new ArrayList<int[]>()::add;
        StdinLease lease = conn.captureStdin(leased);
        assertSame(leased, conn.stdinHandler());
        lease.close();
        assertSame("Close must restore the exact saved instance", steady, conn.stdinHandler());
    }

    @Test
    public void testNestedLeasesRestoreLifo() {
        FakeConnection conn = new FakeConnection();
        Consumer<int[]> steady = new ArrayList<int[]>()::add;
        Consumer<int[]> outer = new ArrayList<int[]>()::add;
        Consumer<int[]> inner = new ArrayList<int[]>()::add;
        conn.setStdinHandler(steady);
        StdinLease outerLease = conn.captureStdin(outer);
        StdinLease innerLease = conn.captureStdin(inner);
        assertSame(inner, conn.stdinHandler());
        innerLease.close();
        assertSame(outer, conn.stdinHandler());
        outerLease.close();
        assertSame(steady, conn.stdinHandler());
    }

    @Test
    public void testDoubleCloseRestoresOnce() {
        FakeConnection conn = new FakeConnection();
        Consumer<int[]> steady = new ArrayList<int[]>()::add;
        conn.setStdinHandler(steady);
        StdinLease lease = conn.captureStdin(new ArrayList<int[]>()::add);
        lease.close();
        lease.close();
        assertSame(steady, conn.stdinHandler());
    }

    @Test
    public void testOutOfOrderCloseRestoresSavedAnyway() {
        // Overlapping leases are last-in-first-out by contract; closing out
        // of order restores the saved handler unconditionally (logged
        // best-effort by the implementation) rather than leaving a stale one.
        FakeConnection conn = new FakeConnection();
        Consumer<int[]> steady = new ArrayList<int[]>()::add;
        Consumer<int[]> outer = new ArrayList<int[]>()::add;
        Consumer<int[]> inner = new ArrayList<int[]>()::add;
        conn.setStdinHandler(steady);
        StdinLease outerLease = conn.captureStdin(outer);
        StdinLease innerLease = conn.captureStdin(inner);
        outerLease.close();
        assertSame(steady, conn.stdinHandler());
        innerLease.close();
    }

    @Test
    public void testRestoreNull() {
        // Teardown compatibility: a lease captured with no handler set must
        // restore null, not substitute anything.
        FakeConnection conn = new FakeConnection();
        assertNull(conn.stdinHandler());
        StdinLease lease = conn.captureStdin(new ArrayList<int[]>()::add);
        lease.close();
        assertNull(conn.stdinHandler());
    }

    @Test
    public void testDispatchFollowsCurrentHandler() {
        FakeConnection conn = new FakeConnection();
        List<int[]> steady = new ArrayList<>();
        List<int[]> leased = new ArrayList<>();
        conn.setStdinHandler(recorder(steady));
        try (StdinLease ignored = conn.captureStdin(recorder(leased))) {
            conn.eventDecoder.accept(codePoints("hi"));
            assertTrue("Input during lease must reach the lease handler",
                    leased.size() == 1 && steady.isEmpty());
        }
        conn.eventDecoder.accept(codePoints("yo"));
        assertTrue("Input after close must reach the restored handler",
                steady.size() == 1 && leased.size() == 1);
    }

    @Test
    public void testQueuedInputFlushesToNewHandler() {
        // Queue/flush semantics the lease relies on: input arriving with no
        // handler queues, and setting a handler delivers it immediately.
        FakeConnection conn = new FakeConnection();
        List<int[]> leased = new ArrayList<>();
        conn.eventDecoder.accept(codePoints("ab"));
        StdinLease lease = conn.captureStdin(recorder(leased));
        try {
            assertTrue("Queued input must flush to the lease handler on install",
                    leased.size() == 1);
        } finally {
            lease.close();
        }
    }
}
