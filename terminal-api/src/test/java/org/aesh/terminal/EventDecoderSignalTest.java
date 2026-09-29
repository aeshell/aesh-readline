package org.aesh.terminal;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.aesh.terminal.detect.TerminalTheme;
import org.aesh.terminal.tty.MouseEvent;
import org.aesh.terminal.tty.Signal;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for signal extraction in EventDecoder.
 */
public class EventDecoderSignalTest {

    private EventDecoder decoder;
    private List<Signal> signals;
    private List<int[]> receivedInput;

    @Before
    public void setUp() {
        decoder = new EventDecoder();
        signals = new ArrayList<>();
        receivedInput = new ArrayList<>();
        decoder.setSignalHandler(signals::add);
        decoder.setInputHandler(receivedInput::add);
    }

    @Test
    public void testIntSignal() {
        // Ctrl+C = byte 3
        decoder.accept(new int[] { 3 });
        assertEquals(1, signals.size());
        assertEquals(Signal.INT, signals.get(0));
        assertEquals(0, receivedInput.size());
    }

    @Test
    public void testQuitSignal() {
        // Ctrl+\ = byte 28
        decoder.accept(new int[] { 28 });
        assertEquals(1, signals.size());
        assertEquals(Signal.QUIT, signals.get(0));
        assertEquals(0, receivedInput.size());
    }

    @Test
    public void testEofSignal() {
        // Ctrl+D = byte 4
        decoder.accept(new int[] { 4 });
        assertEquals(1, signals.size());
        assertEquals(Signal.EOF, signals.get(0));
        assertEquals(0, receivedInput.size());
    }

    @Test
    public void testSuspSignal() {
        // Ctrl+Z = byte 26
        decoder.accept(new int[] { 26 });
        assertEquals(1, signals.size());
        assertEquals(Signal.SUSP, signals.get(0));
        assertEquals(0, receivedInput.size());
    }

    @Test
    public void testSignalInMiddleOfInput() {
        // "ab" + Ctrl+C + "cd"
        decoder.accept(new int[] { 'a', 'b', 3, 'c', 'd' });
        assertEquals(1, signals.size());
        assertEquals(Signal.INT, signals.get(0));
        // Input before and after the signal should be delivered
        assertEquals(2, receivedInput.size());
        assertEquals(2, receivedInput.get(0).length); // "ab"
        assertEquals(2, receivedInput.get(1).length); // "cd"
    }

    @Test
    public void testQuitSignalInMiddleOfInput() {
        // "hello" + Ctrl+\ + "world"
        decoder.accept(new int[] { 'h', 'e', 'l', 'l', 'o', 28, 'w', 'o', 'r', 'l', 'd' });
        assertEquals(1, signals.size());
        assertEquals(Signal.QUIT, signals.get(0));
        assertEquals(2, receivedInput.size());
        assertEquals(5, receivedInput.get(0).length); // "hello"
        assertEquals(5, receivedInput.get(1).length); // "world"
    }

    @Test
    public void testMultipleSignals() {
        // Ctrl+C then Ctrl+\
        decoder.accept(new int[] { 3, 28 });
        assertEquals(2, signals.size());
        assertEquals(Signal.INT, signals.get(0));
        assertEquals(Signal.QUIT, signals.get(1));
    }

    @Test
    public void testNoSignalHandler() {
        // Without a signal handler, signal bytes pass through as input
        decoder.setSignalHandler(null);
        decoder.accept(new int[] { 3, 28, 4, 26 });
        assertEquals(0, signals.size());
        assertEquals(1, receivedInput.size());
        assertEquals(4, receivedInput.get(0).length);
    }

    @Test
    public void testCustomQuitFromAttributes() {
        // Create decoder with custom attributes where VQUIT = 30
        Attributes attr = new Attributes();
        attr.setControlChar(Attributes.ControlChar.VINTR, 3);
        attr.setControlChar(Attributes.ControlChar.VQUIT, 30);
        attr.setControlChar(Attributes.ControlChar.VEOF, 4);
        attr.setControlChar(Attributes.ControlChar.VSUSP, 26);

        EventDecoder customDecoder = new EventDecoder(attr);
        List<Signal> customSignals = new ArrayList<>();
        customDecoder.setSignalHandler(customSignals::add);
        customDecoder.setInputHandler(data -> {
        });

        // Default Ctrl+\ (28) should NOT trigger QUIT
        customDecoder.accept(new int[] { 28 });
        assertEquals(0, customSignals.size());

        // Custom quit char (30) should trigger QUIT
        customDecoder.accept(new int[] { 30 });
        assertEquals(1, customSignals.size());
        assertEquals(Signal.QUIT, customSignals.get(0));
    }

    @Test
    public void testRegularInputPassesThrough() {
        decoder.accept(new int[] { 'h', 'e', 'l', 'l', 'o' });
        assertEquals(0, signals.size());
        assertEquals(1, receivedInput.size());
        assertEquals(5, receivedInput.get(0).length);
    }

    @Test
    public void testThemeDsrBeforeSignalIsFiltered() {
        List<TerminalTheme> themeEvents = new ArrayList<>();
        decoder.setThemeChangeHandler(themeEvents::add);

        // CSI ? 997 ; 1 n (dark) followed by Ctrl+C in one chunk
        decoder.accept(new int[] { 27, '[', '?', '9', '9', '7', ';', '1', 'n', 3 });

        assertEquals(1, themeEvents.size());
        assertEquals(TerminalTheme.DARK, themeEvents.get(0));
        assertEquals(1, signals.size());
        assertEquals(Signal.INT, signals.get(0));
        assertEquals("the DSR must be consumed, not delivered raw",
                0, receivedInput.size());
    }

    @Test
    public void testFocusBeforeSignalIsFiltered() {
        List<Boolean> focusEvents = new ArrayList<>();
        decoder.setFocusHandler(focusEvents::add);

        decoder.accept(new int[] { 27, '[', 'I', 3 });

        assertEquals(1, focusEvents.size());
        assertEquals(true, focusEvents.get(0));
        assertEquals(1, signals.size());
        assertEquals(Signal.INT, signals.get(0));
        assertEquals(0, receivedInput.size());
    }

    @Test
    public void testMouseBeforeSignalIsFiltered() {
        List<MouseEvent> mouseEvents = new ArrayList<>();
        decoder.setMouseHandler(mouseEvents::add);

        // SGR press at (10,20) followed by Ctrl+C in one chunk
        decoder.accept(new int[] {
                27, '[', '<', '0', ';', '1', '0', ';', '2', '0', 'M', 3 });

        assertEquals(1, mouseEvents.size());
        assertEquals(1, signals.size());
        assertEquals(Signal.INT, signals.get(0));
        assertEquals(0, receivedInput.size());
    }

    @Test
    public void testTextAroundMultipleSignalsKeepsOrder() {
        decoder.accept(new int[] { 'a', 3, 'b', 26, 'c' });

        assertEquals(2, signals.size());
        assertEquals(Signal.INT, signals.get(0));
        assertEquals(Signal.SUSP, signals.get(1));
        assertEquals(3, receivedInput.size());
        assertEquals('a', receivedInput.get(0)[0]);
        assertEquals('b', receivedInput.get(1)[0]);
        assertEquals('c', receivedInput.get(2)[0]);
    }

    @Test
    public void testPreSignalSegmentQueuedWhenHandlerAbsent() {
        EventDecoder unhandled = new EventDecoder();
        List<Signal> unhandledSignals = new ArrayList<>();
        unhandled.setSignalHandler(unhandledSignals::add);

        unhandled.accept(new int[] { 'x', 3, 'y' });

        assertEquals(1, unhandledSignals.size());
        assertEquals(Signal.INT, unhandledSignals.get(0));

        List<int[]> drained = new ArrayList<>();
        unhandled.setInputHandler(drained::add);

        assertEquals("both sides of the signal must have queued",
                2, drained.size());
        assertEquals('x', drained.get(0)[0]);
        assertEquals('y', drained.get(1)[0]);
    }
}
