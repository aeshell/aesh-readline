package org.aesh.readline.action;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import org.aesh.terminal.Key;
import org.aesh.terminal.KeyAction;
import org.aesh.terminal.Terminal;
import org.junit.Test;

/**
 * Tests for ActionDecoder escape sequence timeout disambiguation.
 * <p>
 * ESC (27) is both a direct binding ({@link Key#ESC}) and a prefix of longer
 * sequences (ESC [ A = UP, ESC [ B = DOWN, etc.). When an InputPeeker is set,
 * the decoder uses peek(timeout) to disambiguate: if more input arrives within
 * the timeout, it waits for the longer sequence; if the timeout expires, it
 * returns the short binding (Key.ESC).
 */
public class ActionDecoderEscapeTimeoutTest {

    /**
     * Without a peeker, a lone ESC should return Key.ESC immediately
     * (the direct binding wins, no disambiguation).
     */
    @Test
    public void testLoneEscWithoutPeeker() {
        ActionDecoder decoder = new ActionDecoder();
        decoder.add(new int[] { 27 }); // ESC
        assertTrue("Lone ESC without peeker should produce Key.ESC",
                decoder.hasNext());
        KeyAction action = decoder.next();
        assertEquals(Key.ESC, action);
    }

    /**
     * With a peeker that times out (returns READ_EXPIRED), a lone ESC
     * should also return Key.ESC — the timeout confirms it's a bare ESC press.
     */
    @Test
    public void testLoneEscWithTimeoutPeeker() {
        ActionDecoder decoder = new ActionDecoder();
        decoder.setInputPeeker(timeout -> Terminal.READ_EXPIRED);

        decoder.add(new int[] { 27 }); // ESC
        assertTrue("Lone ESC with timeout peeker should produce Key.ESC",
                decoder.hasNext());
        KeyAction action = decoder.next();
        assertEquals(Key.ESC, action);
    }

    /**
     * With a peeker that returns a byte (more input coming), a lone ESC
     * should wait for more input — it's likely the start of an escape sequence.
     */
    @Test
    public void testLoneEscWithDataPeeker() {
        ActionDecoder decoder = new ActionDecoder();
        // Peeker returns '[' (91) — indicating more data is coming
        decoder.setInputPeeker(timeout -> 91);

        decoder.add(new int[] { 27 }); // ESC
        assertFalse("Lone ESC with data peeker should wait for more input",
                decoder.hasNext());
    }

    /**
     * A complete escape sequence (ESC [ A = Up arrow) should be decoded
     * normally regardless of whether a peeker is set.
     */
    @Test
    public void testCompleteEscapeSequenceWithPeeker() {
        ActionDecoder decoder = new ActionDecoder();
        decoder.setInputPeeker(timeout -> Terminal.READ_EXPIRED);

        decoder.add(Key.UP.getKeyValues());
        assertTrue(decoder.hasNext());
        KeyAction action = decoder.next();
        assertNotNull(action);
        assertEquals(Key.UP, action);
    }

    /**
     * A complete escape sequence should decode correctly without a peeker too.
     */
    @Test
    public void testCompleteEscapeSequenceWithoutPeeker() {
        ActionDecoder decoder = new ActionDecoder();
        decoder.add(Key.UP.getKeyValues());
        assertTrue(decoder.hasNext());
        KeyAction action = decoder.next();
        assertEquals(Key.UP, action);
    }

    /**
     * The peeker should be called with the configured escape timeout.
     */
    @Test
    public void testPeekerCalledWithConfiguredTimeout() {
        ActionDecoder decoder = new ActionDecoder();
        decoder.setEscapeTimeout(75);

        AtomicInteger capturedTimeout = new AtomicInteger(-1);
        decoder.setInputPeeker(timeout -> {
            capturedTimeout.set((int) timeout);
            return Terminal.READ_EXPIRED;
        });

        decoder.add(new int[] { 27 }); // ESC
        decoder.hasNext(); // triggers parse() which calls peeker
        assertEquals(75, capturedTimeout.get());
    }

    /**
     * Setting the peeker to null should revert to legacy behavior:
     * ESC returns immediately as Key.ESC without any peek check.
     */
    @Test
    public void testClearPeeker() {
        ActionDecoder decoder = new ActionDecoder();
        AtomicInteger peekCount = new AtomicInteger(0);
        decoder.setInputPeeker(timeout -> {
            peekCount.incrementAndGet();
            return Terminal.READ_EXPIRED;
        });

        // With peeker: lone ESC triggers peek then produces Key.ESC
        decoder.add(new int[] { 27 });
        assertTrue(decoder.hasNext());
        decoder.next(); // consume
        assertTrue("Peeker should have been called at least once", peekCount.get() >= 1);

        // Clear peeker
        decoder.setInputPeeker(null);

        // Without peeker: lone ESC produces Key.ESC without peek
        peekCount.set(0);
        decoder.add(new int[] { 27 });
        assertTrue(decoder.hasNext());
        decoder.next();
        assertEquals(0, peekCount.get());
    }

    /**
     * If the peeker throws IOException, the decoder should handle it
     * gracefully and return the short binding (Key.ESC).
     */
    @Test
    public void testPeekerIOException() {
        ActionDecoder decoder = new ActionDecoder();
        decoder.setInputPeeker(timeout -> {
            throw new IOException("simulated I/O error");
        });

        decoder.add(new int[] { 27 }); // ESC
        assertTrue("IOException from peeker should return Key.ESC",
                decoder.hasNext());
        KeyAction action = decoder.next();
        assertEquals(Key.ESC, action);
    }

    /**
     * If the peeker returns EOF (-1), the decoder should return the short
     * binding (Key.ESC) — EOF means no more data will arrive.
     */
    @Test
    public void testPeekerEOF() {
        ActionDecoder decoder = new ActionDecoder();
        decoder.setInputPeeker(timeout -> -1);

        decoder.add(new int[] { 27 }); // ESC
        assertTrue("EOF from peeker should return Key.ESC",
                decoder.hasNext());
        KeyAction action = decoder.next();
        assertEquals(Key.ESC, action);
    }

    /**
     * Non-ESC single characters should not trigger the peeker at all.
     * Regular characters like 'a' are not prefixes of any escape sequence
     * (they have a direct match but no prefix).
     */
    @Test
    public void testNonEscCharDoesNotTriggerPeeker() {
        ActionDecoder decoder = new ActionDecoder();
        AtomicInteger peekCount = new AtomicInteger(0);
        decoder.setInputPeeker(timeout -> {
            peekCount.incrementAndGet();
            return Terminal.READ_EXPIRED;
        });

        decoder.add(new int[] { 97 }); // 'a'
        assertTrue(decoder.hasNext());
        KeyAction action = decoder.next();
        assertNotNull(action);
        assertEquals(97, action.getCodePointAt(0));
        assertEquals(0, peekCount.get());
    }

    /**
     * Ctrl+C (code 3) is a single-byte binding, not a prefix.
     * It should match directly without triggering the peeker.
     */
    @Test
    public void testCtrlCDoesNotTriggerPeeker() {
        ActionDecoder decoder = new ActionDecoder();
        AtomicInteger peekCount = new AtomicInteger(0);
        decoder.setInputPeeker(timeout -> {
            peekCount.incrementAndGet();
            return Terminal.READ_EXPIRED;
        });

        decoder.add(new int[] { 3 }); // Ctrl+C
        assertTrue(decoder.hasNext());
        KeyAction action = decoder.next();
        assertNotNull(action);
        assertEquals(Key.CTRL_C, action);
        assertEquals(0, peekCount.get());
    }

    /**
     * After resolving a bare ESC via timeout, subsequent input should
     * be decoded independently.
     */
    @Test
    public void testBareEscFollowedByNormalInput() {
        ActionDecoder decoder = new ActionDecoder();
        decoder.setInputPeeker(timeout -> Terminal.READ_EXPIRED);

        // First: bare ESC
        decoder.add(new int[] { 27 });
        assertTrue(decoder.hasNext());
        KeyAction esc = decoder.next();
        assertEquals(Key.ESC, esc);

        // Then: normal character 'x'
        decoder.add(new int[] { 120 });
        assertTrue(decoder.hasNext());
        KeyAction x = decoder.next();
        assertEquals(120, x.getCodePointAt(0));
    }

    /**
     * ESC arriving in one chunk, peeker says more data coming, then
     * '[' 'A' arrives in the next chunk — should produce Key.UP.
     */
    @Test
    public void testEscFollowedBySequenceInNextChunk() {
        ActionDecoder decoder = new ActionDecoder();
        // Peeker returns 91 ('[') — data is coming
        decoder.setInputPeeker(timeout -> 91);

        // First chunk: just ESC
        decoder.add(new int[] { 27 });
        assertFalse("Should wait for more input when peeker says data coming",
                decoder.hasNext());

        // Second chunk: [ A
        decoder.add(new int[] { 91, 65 });
        assertTrue(decoder.hasNext());
        KeyAction action = decoder.next();
        assertEquals(Key.UP, action);
    }

    /**
     * Default escape timeout should be reasonable (not zero, not too long).
     */
    @Test
    public void testDefaultEscapeTimeout() {
        ActionDecoder decoder = new ActionDecoder();
        AtomicInteger capturedTimeout = new AtomicInteger(-1);
        decoder.setInputPeeker(timeout -> {
            capturedTimeout.set((int) timeout);
            return Terminal.READ_EXPIRED;
        });

        decoder.add(new int[] { 27 });
        decoder.hasNext();
        assertTrue("Default timeout should be > 0", capturedTimeout.get() > 0);
        assertTrue("Default timeout should be <= 100ms", capturedTimeout.get() <= 100);
    }
}
