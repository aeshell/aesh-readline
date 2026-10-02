package org.aesh.readline.action;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.ArrayList;
import java.util.List;

import org.aesh.terminal.Key;
import org.aesh.terminal.KeyAction;
import org.junit.Test;

/**
 * Direct unit tests for CRLF collapsing in {@link ActionDecoder}.
 * <p>
 * One Windows ENTER ({@code CR LF}) must yield one submit even when fed
 * straight to this decoder, while lone {@code CR}, lone {@code LF},
 * {@code CR CR} and {@code LF LF} keep their existing behavior.
 */
public class ActionDecoderCrlfTest {

    private static boolean isSubmit(KeyAction action) {
        return action == Key.ENTER || action == Key.ENTER_2
                || action == Key.CTRL_M || action == Key.CTRL_J;
    }

    private static List<KeyAction> drain(ActionDecoder decoder) {
        List<KeyAction> actions = new ArrayList<>();
        while (decoder.hasNext()) {
            actions.add(decoder.next());
        }
        return actions;
    }

    private static long submits(List<KeyAction> actions) {
        return actions.stream().filter(ActionDecoderCrlfTest::isSubmit).count();
    }

    @Test
    public void sameChunkCrlfIsOneSubmit() {
        ActionDecoder decoder = new ActionDecoder();
        decoder.add(new int[] { 'a', 13, 10 });
        List<KeyAction> actions = drain(decoder);
        assertEquals(2, actions.size());
        assertEquals(1, submits(actions));
    }

    @Test
    public void splitChunkCrlfIsOneSubmit() {
        ActionDecoder decoder = new ActionDecoder();
        decoder.add(new int[] { 'a', 13 });
        List<KeyAction> first = drain(decoder);
        assertEquals(1, submits(first));
        decoder.add(new int[] { 10 });
        assertFalse(drain(decoder).stream().anyMatch(ActionDecoderCrlfTest::isSubmit));
    }

    @Test
    public void splitChunkSingleCodePoints() {
        ActionDecoder decoder = new ActionDecoder();
        decoder.add('a');
        decoder.add(13);
        assertEquals(1, submits(drain(decoder)));
        decoder.add(10);
        assertFalse(drain(decoder).stream().anyMatch(ActionDecoderCrlfTest::isSubmit));
    }

    @Test
    public void loneCrAndLoneLfSubmit() {
        ActionDecoder cr = new ActionDecoder();
        cr.add(new int[] { 13 });
        assertEquals(1, submits(drain(cr)));

        ActionDecoder lf = new ActionDecoder();
        lf.add(new int[] { 10 });
        assertEquals(1, submits(drain(lf)));
    }

    @Test
    public void doubledLineEndingsSubmitTwice() {
        ActionDecoder crlf = new ActionDecoder();
        crlf.add(new int[] { 13, 10, 13, 10 });
        assertEquals(2, submits(drain(crlf)));

        ActionDecoder crcr = new ActionDecoder();
        crcr.add(new int[] { 13, 13 });
        assertEquals(2, submits(drain(crcr)));

        ActionDecoder lflf = new ActionDecoder();
        lflf.add(new int[] { 10, 10 });
        assertEquals(2, submits(drain(lflf)));
    }

    @Test
    public void textWithoutCrPassesThrough() {
        ActionDecoder decoder = new ActionDecoder();
        decoder.add(new int[] { 'h', 'i' });
        List<KeyAction> actions = drain(decoder);
        assertEquals(2, actions.size());
        assertEquals(0, submits(actions));
    }
}
