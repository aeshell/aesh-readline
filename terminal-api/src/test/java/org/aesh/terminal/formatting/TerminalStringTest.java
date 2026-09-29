package org.aesh.terminal.formatting;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.aesh.terminal.utils.ANSI;
import org.junit.Test;

public class TerminalStringTest {

    @Test
    public void testTerminalString() {
        TerminalString string = new TerminalString("foo");

        assertFalse(string.containSpaces());
        assertFalse(string.isFormatted());
        assertEquals("foo", string.getCharacters());

        string = new TerminalString("foo bar", new TerminalColor(Color.BLACK, Color.WHITE));
        assertTrue(string.containSpaces());
        assertTrue(string.isFormatted());
        assertEquals("foo bar", string.getCharacters());
        string.switchSpacesToEscapedSpaces();
        assertEquals("foo\\ bar", string.getCharacters());
        assertEquals(ANSI.START + ";30;47mfoo\\ bar" + ANSI.RESET, string.toString());

        string = new TerminalString("foo bar", true);
        assertTrue(string.containSpaces());
        assertFalse(string.isFormatted());
        assertEquals("foo bar", string.getCharacters());

        assertEquals(0, string.getANSILength());
    }

    @Test
    public void testHyperlinkConstructor() {
        String url = "https://example.com";
        TerminalString ts = new TerminalString("click me", url,
                new TerminalColor(Color.BLUE, Color.DEFAULT), new TerminalTextStyle());

        assertEquals("click me", ts.getCharacters());
        assertEquals(url, ts.getHyperlinkUrl());
    }

    @Test
    public void testHyperlinkToString() {
        String url = "https://example.com";
        TerminalString ts = new TerminalString("click me", url,
                new TerminalColor(), new TerminalTextStyle());

        String result = ts.toString();
        String expectedStart = ANSI.buildHyperlinkStart(url, null);
        String expectedEnd = ANSI.buildHyperlinkEnd();

        assertTrue("toString should start with hyperlink open",
                result.startsWith(expectedStart));
        assertTrue("toString should end with hyperlink close",
                result.endsWith(expectedEnd));
        assertTrue("toString should contain the text",
                result.contains("click me"));
    }

    @Test
    public void testNoHyperlinkUrl() {
        TerminalString ts = new TerminalString("plain text");
        assertNull("Default hyperlinkUrl should be null", ts.getHyperlinkUrl());

        String result = ts.toString();
        assertFalse("No hyperlink codes in output",
                result.contains(ANSI.buildHyperlinkEnd()));
    }

    @Test
    public void testSetHyperlinkUrl() {
        TerminalString ts = new TerminalString("text");
        assertNull(ts.getHyperlinkUrl());

        ts.setHyperlinkUrl("https://example.com");
        assertEquals("https://example.com", ts.getHyperlinkUrl());

        String result = ts.toString();
        assertTrue("After setting URL, toString should include hyperlink",
                result.contains(ANSI.buildHyperlinkStart("https://example.com", null)));
    }

    @Test
    public void testHyperlinkANSILength() {
        TerminalString plain = new TerminalString("text");
        int plainLength = plain.getANSILength();

        TerminalString linked = new TerminalString("text", "https://example.com",
                new TerminalColor(), new TerminalTextStyle());
        int linkedLength = linked.getANSILength();

        assertTrue("Hyperlinked ANSI length should be greater than plain",
                linkedLength > plainLength);

        int hyperlinkOverhead = ANSI.buildHyperlinkStart("https://example.com", null).length()
                + ANSI.buildHyperlinkEnd().length();
        assertEquals("Hyperlink should add exact overhead",
                plainLength + hyperlinkOverhead, linkedLength);
    }

    @Test
    public void testCloneRenderingAttributesPreservesHyperlink() {
        String url = "https://example.com";
        TerminalString original = new TerminalString("original", url,
                new TerminalColor(Color.RED, Color.DEFAULT), new TerminalTextStyle());

        TerminalString cloned = original.cloneRenderingAttributes("cloned");

        assertEquals("Cloned should have different text", "cloned", cloned.getCharacters());
        assertEquals("Cloned should preserve hyperlink URL", url, cloned.getHyperlinkUrl());
    }

    @Test
    public void testIgnoreRenderingWithHyperlink() {
        TerminalString ts = new TerminalString("text", true);
        ts.setHyperlinkUrl("https://example.com");

        // When ignoreRendering is true, toString should return plain text
        assertEquals("text", ts.toString());
        assertEquals(0, ts.getANSILength());
    }

    private static TerminalTextStyle boldStyle() {
        TerminalTextStyle style = new TerminalTextStyle();
        style.setBold(true);
        return style;
    }

    @Test
    public void testUnrenderedNotEqualToColored() {
        TerminalString plain = new TerminalString("x", true);
        TerminalString colored = new TerminalString("x",
                new TerminalColor(Color.RED, Color.DEFAULT), new TerminalTextStyle());

        // The reported defect resolved symmetrically: neither direction matches.
        assertFalse(plain.equals(colored));
        assertFalse(colored.equals(plain));
    }

    @Test
    public void testEqualityIsTransitive() {
        TerminalString first = new TerminalString("x",
                new TerminalColor(Color.RED, Color.DEFAULT), boldStyle());
        TerminalString second = new TerminalString("x",
                new TerminalColor(Color.RED, Color.DEFAULT), boldStyle());
        TerminalString third = new TerminalString("x",
                new TerminalColor(Color.RED, Color.DEFAULT), boldStyle());

        assertTrue(first.equals(second));
        assertTrue(second.equals(third));
        assertTrue("transitivity must hold across separately built values",
                first.equals(third));
        assertEquals(first.hashCode(), third.hashCode());

        TerminalString unrendered = new TerminalString("x", true);
        assertFalse(first.equals(unrendered));
        assertFalse(unrendered.equals(first));
    }

    @Test
    public void testEqualStylesByValue() {
        TerminalString first = new TerminalString("x",
                new TerminalColor(), boldStyle());
        TerminalString second = new TerminalString("x",
                new TerminalColor(), boldStyle());

        assertTrue("separately built identical styles must compare equal",
                first.equals(second));
        assertTrue(second.equals(first));
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    public void testDifferentStyleNotEqual() {
        TerminalString plain = new TerminalString("x",
                new TerminalColor(), new TerminalTextStyle());
        TerminalString bold = new TerminalString("x",
                new TerminalColor(), boldStyle());

        assertFalse(plain.equals(bold));
        assertFalse(bold.equals(plain));
    }

    @Test
    public void testHyperlinkParticipates() {
        TerminalString linked = new TerminalString("x", "https://a.example",
                new TerminalColor(), new TerminalTextStyle());
        TerminalString otherLink = new TerminalString("x", "https://b.example",
                new TerminalColor(), new TerminalTextStyle());
        TerminalString noLink = new TerminalString("x",
                new TerminalColor(), new TerminalTextStyle());

        assertFalse(linked.equals(otherLink));
        assertFalse(otherLink.equals(linked));
        assertFalse(linked.equals(noLink));
        assertFalse(noLink.equals(linked));
        assertTrue(noLink.equals(new TerminalString("x",
                new TerminalColor(), new TerminalTextStyle())));
    }

    @Test
    public void testHashCollectionsAcrossInstances() {
        TerminalString first = new TerminalString("x",
                new TerminalColor(Color.RED, Color.DEFAULT), boldStyle());
        TerminalString second = new TerminalString("x",
                new TerminalColor(Color.RED, Color.DEFAULT), boldStyle());

        Set<TerminalString> set = new HashSet<>();
        set.add(first);
        assertTrue(set.contains(second));

        Map<TerminalString, String> map = new HashMap<>();
        map.put(first, "found");
        assertEquals("found", map.get(second));
    }

    @Test
    public void testEqualsBasics() {
        TerminalString string = new TerminalString("x");
        assertTrue(string.equals(string));
        assertFalse(string.equals(null));
        assertFalse(string.equals("x"));
        assertFalse(string.equals(new TerminalString("y")));
    }
}
