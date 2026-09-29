package org.aesh.terminal;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.aesh.terminal.detect.TerminalTheme;
import org.aesh.terminal.io.InputPeeker;
import org.aesh.terminal.tty.MouseEvent;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for lone-ESC disambiguation in EventDecoder.
 * <p>
 * While any sequence filter is active, a chunk ending right after ESC
 * must be held (Alt combination or CSI/OSC may follow) but must not
 * stall forever: with a peeker set, a timeout delivers the standalone
 * Escape. Without a peeker the byte stays held (prior behavior).
 */
public class EventDecoderLoneEscapeTest {

    private EventDecoder decoder;
    private List<int[]> receivedInput;
    private List<Boolean> focusEvents;
    private List<TerminalTheme> themeEvents;

    @Before
    public void setUp() {
        decoder = new EventDecoder();
        receivedInput = new ArrayList<>();
        focusEvents = new ArrayList<>();
        themeEvents = new ArrayList<>();
        decoder.setInputHandler(new Consumer<int[]>() {
            @Override
            public void accept(int[] input) {
                receivedInput.add(input.clone());
            }
        });
    }

    @Test
    public void testLoneEscapeDeliveredWithFocusHandler() {
        decoder.setFocusHandler(new Consumer<Boolean>() {
            @Override
            public void accept(Boolean focused) {
                focusEvents.add(focused);
            }
        });
        decoder.setInputPeeker(new StubPeeker(Terminal.READ_EXPIRED));

        decoder.accept(new int[] { 27 });

        assertEquals(1, receivedInput.size());
        assertArrayEquals(new int[] { 27 }, receivedInput.get(0));
        assertTrue("no focus event for a lone ESC", focusEvents.isEmpty());
    }

    @Test
    public void testLoneEscapeDeliveredWithMouseHandler() {
        decoder.setMouseHandler(new Consumer<MouseEvent>() {
            @Override
            public void accept(MouseEvent event) {
            }
        });
        decoder.setInputPeeker(new StubPeeker(Terminal.READ_EXPIRED));

        decoder.accept(new int[] { 27 });

        assertEquals(1, receivedInput.size());
        assertArrayEquals(new int[] { 27 }, receivedInput.get(0));
    }

    @Test
    public void testLoneEscapeDeliveredWithNoCallbackThemeSubscription() {
        // Theme notifications install a handler even without an
        // application callback; filtering is active all the same.
        decoder.setThemeChangeHandler(new Consumer<TerminalTheme>() {
            @Override
            public void accept(TerminalTheme theme) {
            }
        });
        decoder.setInputPeeker(new StubPeeker(Terminal.READ_EXPIRED));

        decoder.accept(new int[] { 27 });

        assertEquals(1, receivedInput.size());
        assertArrayEquals(new int[] { 27 }, receivedInput.get(0));
        assertTrue("no theme event for a lone ESC", themeEvents.isEmpty());
    }

    @Test
    public void testLoneEscapeThenLetterStaysAlt() {
        decoder.setFocusHandler(new Consumer<Boolean>() {
            @Override
            public void accept(Boolean focused) {
                focusEvents.add(focused);
            }
        });
        StubPeeker peeker = new StubPeeker('a');
        decoder.setInputPeeker(peeker);

        decoder.accept(new int[] { 27 });
        assertTrue("ESC must stay held while input is imminent",
                receivedInput.isEmpty());

        decoder.accept(new int[] { 'a' });
        assertEquals(1, receivedInput.size());
        assertArrayEquals(new int[] { 27, 'a' }, receivedInput.get(0));
        assertEquals("no second peek once bytes resolve the sequence",
                1, peeker.calls);
    }

    @Test
    public void testMidCsiNeverPeeks() {
        decoder.setFocusHandler(new Consumer<Boolean>() {
            @Override
            public void accept(Boolean focused) {
                focusEvents.add(focused);
            }
        });
        StubPeeker peeker = new StubPeeker(Terminal.READ_EXPIRED);
        decoder.setInputPeeker(peeker);

        decoder.accept(new int[] { 27, '[' });
        assertTrue(receivedInput.isEmpty());
        assertEquals("ESC [ is genuinely incomplete, not a lone ESC",
                0, peeker.calls);

        decoder.accept(new int[] { 'I' });
        assertEquals(1, focusEvents.size());
        assertEquals(true, focusEvents.get(0));
        assertTrue(receivedInput.isEmpty());
        assertEquals(0, peeker.calls);
    }

    @Test
    public void testCompleteSequencePassesThroughWithoutPeek() {
        decoder.setFocusHandler(new Consumer<Boolean>() {
            @Override
            public void accept(Boolean focused) {
                focusEvents.add(focused);
            }
        });
        StubPeeker peeker = new StubPeeker(Terminal.READ_EXPIRED);
        decoder.setInputPeeker(peeker);

        decoder.accept(new int[] { 27, '[', 'A' });

        assertEquals(1, receivedInput.size());
        assertArrayEquals(new int[] { 27, '[', 'A' }, receivedInput.get(0));
        assertEquals(0, peeker.calls);
    }

    @Test
    public void testNoPeekerHoldsEscape() {
        decoder.setFocusHandler(new Consumer<Boolean>() {
            @Override
            public void accept(Boolean focused) {
                focusEvents.add(focused);
            }
        });

        decoder.accept(new int[] { 27 });

        assertTrue("without a peeker there is no timeout source; the byte stays held",
                receivedInput.isEmpty());
    }

    @Test
    public void testEofPeekerDeliversEscape() {
        decoder.setFocusHandler(new Consumer<Boolean>() {
            @Override
            public void accept(Boolean focused) {
                focusEvents.add(focused);
            }
        });
        decoder.setInputPeeker(new StubPeeker(-1));

        decoder.accept(new int[] { 27 });

        assertEquals(1, receivedInput.size());
        assertArrayEquals(new int[] { 27 }, receivedInput.get(0));
    }

    @Test
    public void testFailingPeekerDeliversEscape() {
        decoder.setFocusHandler(new Consumer<Boolean>() {
            @Override
            public void accept(Boolean focused) {
                focusEvents.add(focused);
            }
        });
        decoder.setInputPeeker(new StubPeeker(new IOException("peek broken")));

        decoder.accept(new int[] { 27 });

        assertEquals("peeker failure must deliver, not stall",
                1, receivedInput.size());
        assertArrayEquals(new int[] { 27 }, receivedInput.get(0));
    }

    @Test
    public void testEscapeTimeoutForwardedToPeeker() {
        decoder.setFocusHandler(new Consumer<Boolean>() {
            @Override
            public void accept(Boolean focused) {
                focusEvents.add(focused);
            }
        });
        StubPeeker peeker = new StubPeeker(Terminal.READ_EXPIRED);
        decoder.setInputPeeker(peeker);
        decoder.setEscapeTimeout(5);

        decoder.accept(new int[] { 27 });

        assertEquals(5, peeker.lastTimeout);
        assertEquals(1, receivedInput.size());
    }

    /**
     * Canned peeker: answers every peek identically and records use.
     * No timing involved, so the tests stay deterministic.
     */
    private static class StubPeeker implements InputPeeker {
        private final int response;
        private final IOException failure;
        private int calls;
        private long lastTimeout = -1;

        StubPeeker(int response) {
            this.response = response;
            this.failure = null;
        }

        StubPeeker(IOException failure) {
            this.response = 0;
            this.failure = failure;
        }

        @Override
        public int peek(long timeoutMs) throws IOException {
            calls++;
            lastTimeout = timeoutMs;
            if (failure != null) {
                throw failure;
            }
            return response;
        }
    }
}
