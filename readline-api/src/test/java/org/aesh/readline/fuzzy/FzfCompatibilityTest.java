package org.aesh.readline.fuzzy;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

/**
 * Tests ported from fzf's algo_test.go to verify scoring compatibility.
 * <p>
 * These tests use the DEFAULT scheme (matching fzf's init("default"))
 * and verify exact scores, start/end indices to ensure our port
 * produces identical results to the Go implementation.
 *
 * @see <a href="https://github.com/junegunn/fzf/blob/master/src/algo/algo_test.go">fzf algo_test.go</a>
 */
public class FzfCompatibilityTest {

    // Scoring constants (matching fzf's Go values)
    private static final int scoreMatch = FuzzyScheme.SCORE_MATCH; // 16
    private static final int scoreGapStart = FuzzyScheme.SCORE_GAP_START; // -3
    private static final int scoreGapExtension = FuzzyScheme.SCORE_GAP_EXTENSION; // -1
    private static final int bonusBoundary = FuzzyScheme.BONUS_BOUNDARY; // 8
    private static final int bonusNonWord = FuzzyScheme.BONUS_NON_WORD; // 8
    private static final int bonusCamel123 = FuzzyScheme.BONUS_CAMEL_123; // 7
    private static final int bonusConsecutive = FuzzyScheme.BONUS_CONSECUTIVE; // 4
    private static final int bonusFirstCharMultiplier = FuzzyScheme.BONUS_FIRST_CHAR_MULTIPLIER; // 2

    // Scheme-specific bonuses (DEFAULT scheme)
    private static final int bonusBoundaryWhite = FuzzyScheme.DEFAULT.bonusBoundaryWhite; // 10
    private static final int bonusBoundaryDelimiter = FuzzyScheme.DEFAULT.bonusBoundaryDelimiter; // 9

    private FuzzyAlgo algo;

    @Before
    public void setUp() {
        algo = new FuzzyAlgo(FuzzyScheme.DEFAULT);
    }

    private static int[] cp(String s) {
        return s.codePoints().toArray();
    }

    /**
     * Assert that the fuzzy match produces the expected start, end, and score.
     * Matches fzf's assertMatch helper.
     */
    private void assertMatch(boolean caseSensitive, String input, String pattern,
            int expectedStart, int expectedEnd, int expectedScore) {
        // Normalize pattern to lowercase if case-insensitive (matching fzf behavior)
        String normalizedPattern = caseSensitive ? pattern : pattern.toLowerCase();
        FuzzyResult res = algo.match(caseSensitive, cp(input), cp(normalizedPattern), true);

        int start, end;
        if (!res.isMatch()) {
            start = -1;
            end = -1;
        } else if (res.positions != null && res.positions.length > 0) {
            // Use positions to determine range (matching fzf's test helper)
            int minPos = Integer.MAX_VALUE;
            int maxPos = Integer.MIN_VALUE;
            for (int p : res.positions) {
                minPos = Math.min(minPos, p);
                maxPos = Math.max(maxPos, p);
            }
            start = minPos;
            end = maxPos + 1;
        } else {
            start = res.start;
            end = res.end;
        }

        assertEquals("Start index mismatch for '" + input + "' / '" + pattern + "'",
                expectedStart, start);
        assertEquals("End index mismatch for '" + input + "' / '" + pattern + "'",
                expectedEnd, end);
        assertEquals("Score mismatch for '" + input + "' / '" + pattern + "'",
                expectedScore, res.score);
    }

    // ---- Tests ported from fzf's TestFuzzyMatch ----

    @Test
    public void testFooBarbazOBZ() {
        // "oBZ" in "fooBarbaz1" — case insensitive
        assertMatch(false, "fooBarbaz1", "oBZ", 2, 9,
                scoreMatch * 3 + bonusCamel123 + scoreGapStart + scoreGapExtension * 3);
    }

    @Test
    public void testFooBarBazFBB() {
        // "fbb" in "foo bar baz"
        assertMatch(false, "foo bar baz", "fbb", 0, 9,
                scoreMatch * 3 + bonusBoundaryWhite * bonusFirstCharMultiplier
                        + bonusBoundaryWhite * 2 + 2 * scoreGapStart + 4 * scoreGapExtension);
    }

    @Test
    public void testAutomatorDocumentRdoc() {
        // "rdoc" in "/AutomatorDocument.icns"
        assertMatch(false, "/AutomatorDocument.icns", "rdoc", 9, 13,
                scoreMatch * 4 + bonusCamel123 + bonusConsecutive * 2);
    }

    @Test
    public void testZshcompctlZshc() {
        // "zshc" in "/man1/zshcompctl.1"
        assertMatch(false, "/man1/zshcompctl.1", "zshc", 6, 10,
                scoreMatch * 4 + bonusBoundaryDelimiter * bonusFirstCharMultiplier + bonusBoundaryDelimiter * 3);
    }

    @Test
    public void testOhMyZshCacheZshc() {
        // "zshc" in "/.oh-my-zsh/cache"
        assertMatch(false, "/.oh-my-zsh/cache", "zshc", 8, 13,
                scoreMatch * 4 + bonusBoundary * bonusFirstCharMultiplier + bonusBoundary * 2
                        + scoreGapStart + bonusBoundaryDelimiter);
    }

    @Test
    public void testVimrcExactMatch() {
        // ".vimrc" in ".vimrc" — non-word at start is treated as strong boundary
        assertMatch(false, ".vimrc", ".vimrc", 0, 6,
                scoreMatch * 6 + bonusBoundaryWhite * (bonusFirstCharMultiplier + 5));
    }

    @Test
    public void testVimrcAfterDelimiter() {
        // ".vimrc" in "/.vimrc" — inherits delimiter boundary
        assertMatch(false, "/.vimrc", ".vimrc", 1, 7,
                scoreMatch * 6 + bonusBoundaryDelimiter * (bonusFirstCharMultiplier + 5));
    }

    @Test
    public void testVimrcMidWord() {
        // ".vimrc" in "a.vimrc" — non-word in middle of word
        assertMatch(false, "a.vimrc", ".vimrc", 1, 7,
                scoreMatch * 6 + bonusBoundary * (bonusFirstCharMultiplier + 5));
    }

    @Test
    public void testNumberSequence() {
        // "12356" in "ab0123 456"
        assertMatch(false, "ab0123 456", "12356", 3, 10,
                scoreMatch * 5 + bonusConsecutive * 3 + scoreGapStart + scoreGapExtension);
    }

    @Test
    public void testNumberAfterLetter() {
        // "12356" in "abc123 456"
        assertMatch(false, "abc123 456", "12356", 3, 10,
                scoreMatch * 5 + bonusCamel123 * bonusFirstCharMultiplier + bonusCamel123 * 2
                        + bonusConsecutive + scoreGapStart + scoreGapExtension);
    }

    @Test
    public void testPathDelimiterFBB() {
        // "fbb" in "foo/bar/baz"
        assertMatch(false, "foo/bar/baz", "fbb", 0, 9,
                scoreMatch * 3 + bonusBoundaryWhite * bonusFirstCharMultiplier
                        + bonusBoundaryDelimiter * 2 + 2 * scoreGapStart + 4 * scoreGapExtension);
    }

    @Test
    public void testCamelCaseFBB() {
        // "fbb" in "fooBarBaz"
        assertMatch(false, "fooBarBaz", "fbb", 0, 7,
                scoreMatch * 3 + bonusBoundaryWhite * bonusFirstCharMultiplier
                        + bonusCamel123 * 2 + 2 * scoreGapStart + 2 * scoreGapExtension);
    }

    @Test
    public void testMixedBoundaryFBB() {
        // "fbb" in "foo barbaz"
        assertMatch(false, "foo barbaz", "fbb", 0, 8,
                scoreMatch * 3 + bonusBoundaryWhite * bonusFirstCharMultiplier + bonusBoundaryWhite
                        + scoreGapStart * 2 + scoreGapExtension * 3);
    }

    @Test
    public void testConsecutiveBoundary() {
        // "foob" in "fooBar Baz"
        assertMatch(false, "fooBar Baz", "foob", 0, 4,
                scoreMatch * 4 + bonusBoundaryWhite * bonusFirstCharMultiplier + bonusBoundaryWhite * 3);
    }

    @Test
    public void testMixedCamelNonWord() {
        // "foo-b" in "xFoo-Bar Baz"
        assertMatch(false, "xFoo-Bar Baz", "foo-b", 1, 6,
                scoreMatch * 5 + bonusCamel123 * bonusFirstCharMultiplier + bonusCamel123 * 2
                        + bonusNonWord + bonusBoundary);
    }

    // ---- Case sensitive tests ----

    @Test
    public void testCaseSensitiveOBz() {
        assertMatch(true, "fooBarbaz", "oBz", 2, 9,
                scoreMatch * 3 + bonusCamel123 + scoreGapStart + scoreGapExtension * 3);
    }

    @Test
    public void testCaseSensitiveFBBPath() {
        assertMatch(true, "Foo/Bar/Baz", "FBB", 0, 9,
                scoreMatch * 3 + bonusBoundaryWhite * bonusFirstCharMultiplier + bonusBoundaryDelimiter * 2
                        + scoreGapStart * 2 + scoreGapExtension * 4);
    }

    @Test
    public void testCaseSensitiveFBBCamel() {
        assertMatch(true, "FooBarBaz", "FBB", 0, 7,
                scoreMatch * 3 + bonusBoundaryWhite * bonusFirstCharMultiplier + bonusCamel123 * 2
                        + scoreGapStart * 2 + scoreGapExtension * 2);
    }

    @Test
    public void testCaseSensitiveFooB() {
        assertMatch(true, "FooBar Baz", "FooB", 0, 4,
                scoreMatch * 4 + bonusBoundaryWhite * bonusFirstCharMultiplier + bonusBoundaryWhite * 2
                        + Math.max(bonusCamel123, bonusBoundaryWhite));
    }

    @Test
    public void testConsecutiveBonusUpdated() {
        // "o-ba" in "foo-bar"
        assertMatch(true, "foo-bar", "o-ba", 2, 6,
                scoreMatch * 4 + bonusBoundary * 3);
    }

    // ---- Non-match tests ----

    @Test
    public void testCaseSensitiveNoMatch() {
        assertMatch(true, "fooBarbaz", "oBZ", -1, -1, 0);
    }

    @Test
    public void testCaseSensitiveNoMatchLower() {
        assertMatch(true, "Foo Bar Baz", "fbb", -1, -1, 0);
    }

    @Test
    public void testPatternTooLong() {
        assertMatch(true, "fooBarbaz", "fooBarbazz", -1, -1, 0);
    }

    // ---- Empty pattern ----

    @Test
    public void testEmptyPatternV1() {
        FuzzyResult r = algo.fuzzyMatchV1(true, cp("foobar"), cp(""), false);
        assertTrue(r.isMatch());
        assertEquals(0, r.start);
        assertEquals(0, r.end);
        assertEquals(0, r.score);
    }

    @Test
    public void testEmptyPatternV2() {
        FuzzyResult r = algo.fuzzyMatchV2(true, cp("foobar"), cp(""), false);
        assertTrue(r.isMatch());
        assertEquals(0, r.start);
        assertEquals(0, r.end);
        assertEquals(0, r.score);
    }
}
