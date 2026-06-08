package org.aesh.readline.history;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.aesh.terminal.utils.Parser;
import org.junit.Test;

/**
 * Tests for history ignore patterns.
 */
public class HistoryIgnoreTest {

    @Test
    public void testIgnoreStartsWithSpace() {
        History history = new InMemoryHistory(10);
        history.setIgnorePatterns(Arrays.asList(" *"));

        history.push(Parser.toCodePoints("normal command"));
        history.push(Parser.toCodePoints(" secret command"));
        history.push(Parser.toCodePoints("another command"));

        assertEquals(2, history.size());
        assertArrayEquals(Parser.toCodePoints("normal command"), history.get(0));
        assertArrayEquals(Parser.toCodePoints("another command"), history.get(1));
    }

    @Test
    public void testIgnoreContainsKeyword() {
        History history = new InMemoryHistory(10);
        history.setIgnorePatterns(Arrays.asList("*password*"));

        history.push(Parser.toCodePoints("login"));
        history.push(Parser.toCodePoints("set password mysecret"));
        history.push(Parser.toCodePoints("logout"));

        assertEquals(2, history.size());
        assertArrayEquals(Parser.toCodePoints("login"), history.get(0));
        assertArrayEquals(Parser.toCodePoints("logout"), history.get(1));
    }

    @Test
    public void testIgnoreExactMatch() {
        History history = new InMemoryHistory(10);
        history.setIgnorePatterns(Arrays.asList("exit"));

        history.push(Parser.toCodePoints("help"));
        history.push(Parser.toCodePoints("exit"));
        history.push(Parser.toCodePoints("exit status"));

        assertEquals(2, history.size());
        assertArrayEquals(Parser.toCodePoints("help"), history.get(0));
        assertArrayEquals(Parser.toCodePoints("exit status"), history.get(1));
    }

    @Test
    public void testMultiplePatterns() {
        History history = new InMemoryHistory(10);
        history.setIgnorePatterns(Arrays.asList(" *", "*password*", "*token*"));

        history.push(Parser.toCodePoints("git status"));
        history.push(Parser.toCodePoints(" hidden command"));
        history.push(Parser.toCodePoints("set password foo"));
        history.push(Parser.toCodePoints("export token=abc"));
        history.push(Parser.toCodePoints("git push"));

        assertEquals(2, history.size());
        assertArrayEquals(Parser.toCodePoints("git status"), history.get(0));
        assertArrayEquals(Parser.toCodePoints("git push"), history.get(1));
    }

    @Test
    public void testNoPatterns() {
        History history = new InMemoryHistory(10);
        // No patterns set — everything should be saved
        history.push(Parser.toCodePoints(" secret"));
        history.push(Parser.toCodePoints("password"));

        assertEquals(2, history.size());
    }

    @Test
    public void testClearPatterns() {
        History history = new InMemoryHistory(10);
        history.setIgnorePatterns(Arrays.asList("*secret*"));

        history.push(Parser.toCodePoints("secret stuff"));
        assertEquals(0, history.size());

        // Clear patterns
        history.setIgnorePatterns(null);
        history.push(Parser.toCodePoints("secret stuff"));
        assertEquals(1, history.size());
    }

    @Test
    public void testWildcardAtStart() {
        History history = new InMemoryHistory(10);
        history.setIgnorePatterns(Arrays.asList("*.tmp"));

        history.push(Parser.toCodePoints("edit file.tmp"));
        history.push(Parser.toCodePoints("edit file.java"));

        assertEquals(1, history.size());
        assertArrayEquals(Parser.toCodePoints("edit file.java"), history.get(0));
    }

    @Test
    public void testWildcardAtEnd() {
        History history = new InMemoryHistory(10);
        history.setIgnorePatterns(Arrays.asList("sudo *"));

        history.push(Parser.toCodePoints("sudo rm -rf /"));
        history.push(Parser.toCodePoints("ls -la"));

        assertEquals(1, history.size());
        assertArrayEquals(Parser.toCodePoints("ls -la"), history.get(0));
    }
}
