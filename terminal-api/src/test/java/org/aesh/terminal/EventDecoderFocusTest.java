package org.aesh.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

/**
 * Tests for focus event filtering in EventDecoder.
 */
public class EventDecoderFocusTest {

    private EventDecoder decoder;
    private List<Boolean> focusEvents;
    private List<int[]> receivedInput;

    @Before
    public void setUp() {
        decoder = new EventDecoder();
        focusEvents = new ArrayList<>();
        receivedInput = new ArrayList<>();
        decoder.setInputHandler(receivedInput::add);
    }

    @Test
    public void testFocusInEvent() {
        decoder.setFocusHandler(focusEvents::add);
        // ESC [ I = focus in
        decoder.accept(new int[] { 27, '[', 'I' });
        assertEquals(1, focusEvents.size());
        assertEquals(true, focusEvents.get(0));
        assertEquals(0, receivedInput.size());
    }

    @Test
    public void testFocusOutEvent() {
        decoder.setFocusHandler(focusEvents::add);
        // ESC [ O = focus out
        decoder.accept(new int[] { 27, '[', 'O' });
        assertEquals(1, focusEvents.size());
        assertEquals(false, focusEvents.get(0));
        assertEquals(0, receivedInput.size());
    }

    @Test
    public void testFocusEventsFilteredFromInput() {
        decoder.setFocusHandler(focusEvents::add);
        // Mix of focus events and regular input
        decoder.accept(new int[] { 'a', 27, '[', 'I', 'b', 27, '[', 'O', 'c' });
        assertEquals(2, focusEvents.size());
        assertEquals(true, focusEvents.get(0));
        assertEquals(false, focusEvents.get(1));
        // Regular input should pass through
        assertEquals(1, receivedInput.size());
        int[] input = receivedInput.get(0);
        assertEquals(3, input.length);
        assertEquals('a', input[0]);
        assertEquals('b', input[1]);
        assertEquals('c', input[2]);
    }

    @Test
    public void testNoFocusHandlerPassesThrough() {
        // No focus handler set — sequences pass through as input
        decoder.accept(new int[] { 27, '[', 'I' });
        assertEquals(0, focusEvents.size());
        assertEquals(1, receivedInput.size());
    }

    @Test
    public void testFocusHandlerSetToNull() {
        decoder.setFocusHandler(focusEvents::add);
        decoder.accept(new int[] { 27, '[', 'I' });
        assertEquals(1, focusEvents.size());

        // Disable focus handling
        decoder.setFocusHandler(null);
        decoder.accept(new int[] { 27, '[', 'O' });
        // Should NOT add to focusEvents
        assertEquals(1, focusEvents.size());
        // Should pass through as input
        assertEquals(1, receivedInput.size());
    }

    @Test
    public void testOtherCsiSequencesNotAffected() {
        decoder.setFocusHandler(focusEvents::add);
        // ESC [ A = cursor up — should NOT be treated as focus
        decoder.accept(new int[] { 27, '[', 'A' });
        assertEquals(0, focusEvents.size());
        assertEquals(1, receivedInput.size());
    }

    @Test
    public void testIncompleteSequencePassesThrough() {
        decoder.setFocusHandler(focusEvents::add);
        // Just ESC [ without the final char
        decoder.accept(new int[] { 27, '[' });
        assertEquals(0, focusEvents.size());
        assertEquals(1, receivedInput.size());
    }

    @Test
    public void testMultipleFocusEvents() {
        decoder.setFocusHandler(focusEvents::add);
        // Rapid focus in/out/in
        decoder.accept(new int[] { 27, '[', 'I', 27, '[', 'O', 27, '[', 'I' });
        assertEquals(3, focusEvents.size());
        assertEquals(true, focusEvents.get(0));
        assertEquals(false, focusEvents.get(1));
        assertEquals(true, focusEvents.get(2));
        assertEquals(0, receivedInput.size());
    }

    @Test
    public void testGetFocusHandler() {
        assertNull(decoder.getFocusHandler());
        decoder.setFocusHandler(focusEvents::add);
        // Handler should be set (not null)
        assert decoder.getFocusHandler() != null;
    }
}
