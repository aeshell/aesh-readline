package org.aesh.terminal.tty.split;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

/**
 * Tests for ScrollbackBuffer — a circular buffer for screen region scrollback.
 */
public class ScrollbackBufferTest {

    @Test
    public void testEmptyBuffer() {
        ScrollbackBuffer buf = new ScrollbackBuffer(10);
        assertEquals(0, buf.size());
        assertTrue(buf.getLastLines(5).isEmpty());
    }

    @Test
    public void testAddAndRetrieve() {
        ScrollbackBuffer buf = new ScrollbackBuffer(10);
        buf.addLine("line 1");
        buf.addLine("line 2");
        buf.addLine("line 3");

        assertEquals(3, buf.size());
        List<String> lines = buf.getLastLines(3);
        assertEquals(3, lines.size());
        assertEquals("line 1", lines.get(0));
        assertEquals("line 2", lines.get(1));
        assertEquals("line 3", lines.get(2));
    }

    @Test
    public void testGetFewerThanStored() {
        ScrollbackBuffer buf = new ScrollbackBuffer(10);
        buf.addLine("a");
        buf.addLine("b");
        buf.addLine("c");
        buf.addLine("d");

        List<String> lines = buf.getLastLines(2);
        assertEquals(2, lines.size());
        assertEquals("c", lines.get(0));
        assertEquals("d", lines.get(1));
    }

    @Test
    public void testGetMoreThanStored() {
        ScrollbackBuffer buf = new ScrollbackBuffer(10);
        buf.addLine("only");

        List<String> lines = buf.getLastLines(5);
        assertEquals(1, lines.size());
        assertEquals("only", lines.get(0));
    }

    @Test
    public void testCircularOverflow() {
        ScrollbackBuffer buf = new ScrollbackBuffer(3);
        buf.addLine("a");
        buf.addLine("b");
        buf.addLine("c");
        assertEquals(3, buf.size());

        // Adding a 4th line should drop "a"
        buf.addLine("d");
        assertEquals(3, buf.size());
        List<String> lines = buf.getLastLines(3);
        assertEquals("b", lines.get(0));
        assertEquals("c", lines.get(1));
        assertEquals("d", lines.get(2));
    }

    @Test
    public void testCircularMultipleOverflows() {
        ScrollbackBuffer buf = new ScrollbackBuffer(3);
        for (int i = 1; i <= 10; i++) {
            buf.addLine("line " + i);
        }
        assertEquals(3, buf.size());
        List<String> lines = buf.getLastLines(3);
        assertEquals("line 8", lines.get(0));
        assertEquals("line 9", lines.get(1));
        assertEquals("line 10", lines.get(2));
    }

    @Test
    public void testClear() {
        ScrollbackBuffer buf = new ScrollbackBuffer(10);
        buf.addLine("a");
        buf.addLine("b");
        buf.addLine("c");
        assertEquals(3, buf.size());

        buf.clear();
        assertEquals(0, buf.size());
        assertTrue(buf.getLastLines(5).isEmpty());

        // Should work after clear
        buf.addLine("new");
        assertEquals(1, buf.size());
        assertEquals("new", buf.getLastLines(1).get(0));
    }

    @Test
    public void testCapacityOne() {
        ScrollbackBuffer buf = new ScrollbackBuffer(1);
        buf.addLine("first");
        assertEquals(1, buf.size());
        assertEquals("first", buf.getLastLines(1).get(0));

        buf.addLine("second");
        assertEquals(1, buf.size());
        assertEquals("second", buf.getLastLines(1).get(0));
    }

    @Test
    public void testGetLastLinesZero() {
        ScrollbackBuffer buf = new ScrollbackBuffer(10);
        buf.addLine("a");
        List<String> lines = buf.getLastLines(0);
        assertTrue(lines.isEmpty());
    }

    @Test
    public void testExactCapacity() {
        ScrollbackBuffer buf = new ScrollbackBuffer(5);
        for (int i = 0; i < 5; i++) {
            buf.addLine("line " + i);
        }
        assertEquals(5, buf.size());
        List<String> lines = buf.getLastLines(5);
        assertEquals(5, lines.size());
        for (int i = 0; i < 5; i++) {
            assertEquals("line " + i, lines.get(i));
        }
    }

    @Test
    public void testClearAfterOverflow() {
        ScrollbackBuffer buf = new ScrollbackBuffer(3);
        for (int i = 0; i < 10; i++) {
            buf.addLine("line " + i);
        }
        buf.clear();
        assertEquals(0, buf.size());

        buf.addLine("after clear");
        assertEquals(1, buf.size());
        assertEquals("after clear", buf.getLastLines(1).get(0));
    }

    @Test
    public void testThreadSafety() throws InterruptedException {
        ScrollbackBuffer buf = new ScrollbackBuffer(100);
        int writerCount = 4;
        int linesPerWriter = 250;
        CountDownLatch latch = new CountDownLatch(writerCount);

        ExecutorService executor = Executors.newFixedThreadPool(writerCount);
        for (int w = 0; w < writerCount; w++) {
            final int writer = w;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < linesPerWriter; i++) {
                        buf.addLine("w" + writer + "-" + i);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // Buffer should have exactly capacity lines
        assertEquals(100, buf.size());
        // All lines should be non-null
        List<String> lines = buf.getLastLines(100);
        assertEquals(100, lines.size());
        for (String line : lines) {
            assertTrue("Line should not be null", line != null);
            assertTrue("Line should start with 'w'", line.startsWith("w"));
        }
    }
}
