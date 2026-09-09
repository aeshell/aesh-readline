package org.aesh.terminal;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

/**
 * Tests for CR LF collapsing in EventDecoder.
 * <p>
 * Cooked-mode line discipline (MSYS2/Cygwin pipes and consoles, pasted CRLF
 * text) delivers CRLF per ENTER. Without collapsing, the leftover LF would
 * complete a second, empty submit. Lone CR and lone LF pass through
 * unchanged.
 */
public class EventDecoderCrLfTest {

    private EventDecoder decoder;
    private List<int[]> receivedInput;

    @Before
    public void setUp() {
        decoder = new EventDecoder();
        receivedInput = new ArrayList<>();
        decoder.setInputHandler(receivedInput::add);
    }

    @Test
    public void testCoalescedCrLfCollapses() {
        decoder.accept(new int[] { 'a', 'b', 13, 10 });
        assertEquals(1, receivedInput.size());
        assertArrayEquals(new int[] { 'a', 'b', 13 }, receivedInput.get(0));
    }

    @Test
    public void testSplitCrLfCollapses() {
        // Trailing CR is emitted immediately (submits promptly); the split
        // LF arriving next is dropped, so no phantom second submit occurs
        decoder.accept(new int[] { 'a', 'b', 13 });
        assertEquals(1, receivedInput.size());
        assertArrayEquals(new int[] { 'a', 'b', 13 }, receivedInput.get(0));
        decoder.accept(new int[] { 10 });
        assertEquals(1, receivedInput.size());
        decoder.accept(new int[] { 'x' });
        assertEquals(2, receivedInput.size());
        assertArrayEquals(new int[] { 'x' }, receivedInput.get(1));
    }

    @Test
    public void testLoneCrFollowedByOther() {
        decoder.accept(new int[] { 13 });
        assertEquals(1, receivedInput.size());
        assertArrayEquals(new int[] { 13 }, receivedInput.get(0));
        decoder.accept(new int[] { 'x' });
        assertEquals(2, receivedInput.size());
        assertArrayEquals(new int[] { 'x' }, receivedInput.get(1));
    }

    @Test
    public void testTrailingCrInChunkSubmitsPromptly() {
        // Regression: a chunk ending with CR must be delivered whole —
        // deferring it would lose the submit when no further input arrives
        decoder.accept(new int[] { 91, 67, '1', '2', 13 });
        assertEquals(1, receivedInput.size());
        assertArrayEquals(new int[] { 91, 67, '1', '2', 13 }, receivedInput.get(0));
    }

    @Test
    public void testLoneLfPassesThrough() {
        decoder.accept(new int[] { 10 });
        assertEquals(1, receivedInput.size());
        assertArrayEquals(new int[] { 10 }, receivedInput.get(0));
    }

    @Test
    public void testCrCrBothKept() {
        decoder.accept(new int[] { 13, 13 });
        assertEquals(1, receivedInput.size());
        assertArrayEquals(new int[] { 13, 13 }, receivedInput.get(0));
        decoder.accept(new int[] { 'x' });
        assertEquals(2, receivedInput.size());
        assertArrayEquals(new int[] { 'x' }, receivedInput.get(1));
    }

    @Test
    public void testLfLfBothKept() {
        decoder.accept(new int[] { 10, 10 });
        assertEquals(1, receivedInput.size());
        assertArrayEquals(new int[] { 10, 10 }, receivedInput.get(0));
    }

    @Test
    public void testCrLfLfKeepsTrailingLf() {
        decoder.accept(new int[] { 13, 10, 10 });
        assertEquals(1, receivedInput.size());
        assertArrayEquals(new int[] { 13, 10 }, receivedInput.get(0));
    }

    @Test
    public void testFlagClearedByInterveningChunk() {
        // A CR-terminated chunk arms the flag, but the very next non-empty
        // chunk resolves it — only an immediately following LF completes a pair
        decoder.accept(new int[] { 13 });
        assertEquals(1, receivedInput.size());
        decoder.accept(new int[] { 'x' });
        assertEquals(2, receivedInput.size());
        assertArrayEquals(new int[] { 'x' }, receivedInput.get(1));
        // Flag is now clear: a later LF passes through as its own submit
        decoder.accept(new int[] { 10 });
        assertEquals(3, receivedInput.size());
        assertArrayEquals(new int[] { 10 }, receivedInput.get(2));
    }

    @Test
    public void testEmptyInputPreservesPendingCr() {
        decoder.accept(new int[] { 13 });
        assertEquals(1, receivedInput.size());
        decoder.accept(new int[0]);
        assertEquals(1, receivedInput.size());
        decoder.accept(new int[] { 10 });
        assertEquals(1, receivedInput.size());
    }
}
