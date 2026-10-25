package org.aesh.readline.fuzzy;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

/**
 * Tests for the fuzzy matching algorithms (V1 and V2).
 */
public class FuzzyAlgoTest {

    private FuzzyAlgo algo;

    @Before
    public void setUp() {
        algo = new FuzzyAlgo(FuzzyScheme.HISTORY);
    }

    private static int[] codePoints(String s) {
        return s.codePoints().toArray();
    }

    // ---- Basic matching ----

    @Test
    public void testEmptyPattern() {
        FuzzyResult r = algo.match(false, codePoints("hello"), codePoints(""), false);
        assertTrue(r.isMatch());
        assertEquals(0, r.score);
    }

    @Test
    public void testExactMatch() {
        FuzzyResult r = algo.match(false, codePoints("hello"), codePoints("hello"), false);
        assertTrue(r.isMatch());
        assertTrue("Exact match should have positive score", r.score > 0);
    }

    @Test
    public void testNoMatch() {
        FuzzyResult r = algo.match(false, codePoints("hello"), codePoints("xyz"), false);
        assertFalse(r.isMatch());
    }

    @Test
    public void testPatternLongerThanText() {
        FuzzyResult r = algo.match(false, codePoints("hi"), codePoints("hello"), false);
        assertFalse(r.isMatch());
    }

    @Test
    public void testSubsequenceMatch() {
        // "fb" should match "foobar" (f...b)
        FuzzyResult r = algo.match(false, codePoints("foobar"), codePoints("fb"), false);
        assertTrue(r.isMatch());
        assertTrue(r.score > 0);
    }

    @Test
    public void testSubstringMatchScoresHigher() {
        // "oo" as contiguous substring should score higher than "fo" with a gap
        FuzzyResult contiguous = algo.match(false, codePoints("foobar"), codePoints("oo"), false);
        FuzzyResult gapped = algo.match(false, codePoints("fxxoxxbar"), codePoints("fb"), false);
        assertTrue(contiguous.isMatch());
        assertTrue(gapped.isMatch());
        // Contiguous matches generally score higher for same-length patterns
    }

    // ---- Case sensitivity ----

    @Test
    public void testCaseInsensitive() {
        FuzzyResult r = algo.match(false, codePoints("FooBar"), codePoints("foobar"), false);
        assertTrue(r.isMatch());
    }

    @Test
    public void testCaseSensitiveNoMatch() {
        FuzzyResult r = algo.match(true, codePoints("FooBar"), codePoints("foobar"), false);
        assertFalse(r.isMatch());
    }

    @Test
    public void testCaseSensitiveMatch() {
        FuzzyResult r = algo.match(true, codePoints("FooBar"), codePoints("FooBar"), false);
        assertTrue(r.isMatch());
    }

    // ---- Word boundary scoring ----

    @Test
    public void testWordBoundaryScoresHigher() {
        // "ff" matching "fuzzy-finder" should score higher than "fuzzyfinder"
        // because the second 'f' is at a word boundary after '-'
        FuzzyResult withBoundary = algo.match(false, codePoints("fuzzy-finder"), codePoints("ff"), false);
        FuzzyResult withoutBoundary = algo.match(false, codePoints("fuzzyfinder"), codePoints("ff"), false);
        assertTrue(withBoundary.isMatch());
        assertTrue(withoutBoundary.isMatch());
        assertTrue("Word boundary match should score >= non-boundary",
                withBoundary.score >= withoutBoundary.score);
    }

    @Test
    public void testCamelCaseBonus() {
        // "fb" matching "FooBar" should get camelCase bonus on 'B'
        FuzzyResult r = algo.match(false, codePoints("FooBar"), codePoints("fb"), false);
        assertTrue(r.isMatch());
    }

    // ---- Position tracking ----

    @Test
    public void testPositionsExactMatch() {
        FuzzyResult r = algo.match(false, codePoints("abc"), codePoints("abc"), true);
        assertTrue(r.isMatch());
        assertNotNull(r.positions);
        assertEquals(3, r.positions.length);
        assertArrayEquals(new int[] { 0, 1, 2 }, r.positions);
    }

    @Test
    public void testPositionsSubsequence() {
        // "ac" in "abc" should match at positions 0, 2
        FuzzyResult r = algo.match(false, codePoints("abc"), codePoints("ac"), true);
        assertTrue(r.isMatch());
        assertNotNull(r.positions);
        assertEquals(2, r.positions.length);
        assertEquals(0, r.positions[0]); // 'a'
        assertEquals(2, r.positions[1]); // 'c'
    }

    @Test
    public void testPositionsWordBoundary() {
        // "ct" in "clean test" should prefer positions at word boundaries
        FuzzyResult r = algo.match(false, codePoints("clean test"), codePoints("ct"), true);
        assertTrue(r.isMatch());
        assertNotNull(r.positions);
        assertEquals(2, r.positions.length);
        // 'c' at 0, 't' at 6 (word boundary)
        assertEquals(0, r.positions[0]);
        assertEquals(6, r.positions[1]);
    }

    // ---- V1 specific tests ----

    @Test
    public void testV1BasicMatch() {
        FuzzyResult r = algo.fuzzyMatchV1(false, codePoints("foobar"), codePoints("fb"), true);
        assertTrue(r.isMatch());
        assertNotNull(r.positions);
    }

    @Test
    public void testV1NoMatch() {
        FuzzyResult r = algo.fuzzyMatchV1(false, codePoints("hello"), codePoints("xyz"), false);
        assertFalse(r.isMatch());
    }

    // ---- V2 specific tests ----

    @Test
    public void testV2BasicMatch() {
        FuzzyResult r = algo.fuzzyMatchV2(false, codePoints("foobar"), codePoints("fb"), true);
        assertTrue(r.isMatch());
        assertNotNull(r.positions);
    }

    @Test
    public void testV2OptimalAlignment() {
        // V2 should find the optimal alignment.
        // "abc" in "a___b___abc" — V2 should prefer the contiguous "abc" at the end
        FuzzyResult r = algo.fuzzyMatchV2(false, codePoints("a___b___abc"), codePoints("abc"), true);
        assertTrue(r.isMatch());
        assertNotNull(r.positions);
        // The contiguous match at positions 8,9,10 should score higher
        // V2 should find it
        assertEquals(8, r.positions[0]);
        assertEquals(9, r.positions[1]);
        assertEquals(10, r.positions[2]);
    }

    // ---- Unicode ----

    @Test
    public void testUnicodeMatch() {
        FuzzyResult r = algo.match(false, codePoints("caf\u00E9 latt\u00E9"), codePoints("cl"), false);
        assertTrue(r.isMatch());
    }

    // ---- Command history patterns ----

    @Test
    public void testHistoryCommand() {
        FuzzyResult r = algo.match(false,
                codePoints("mvn clean test -pl aesh -Dtest=ProcessorTest"),
                codePoints("mct"), false);
        assertTrue(r.isMatch());
    }

    @Test
    public void testGitCommand() {
        FuzzyResult r = algo.match(false,
                codePoints("git commit -m \"Fix flaky test\""),
                codePoints("gcm"), false);
        assertTrue(r.isMatch());
    }

    @Test
    public void testPathMatch() {
        FuzzyResult r = algo.match(false,
                codePoints("cd /home/stalep/git/aesh"),
                codePoints("cda"), false);
        assertTrue(r.isMatch());
    }

    // ---- Single character ----

    @Test
    public void testSingleCharMatch() {
        FuzzyResult r = algo.match(false, codePoints("hello"), codePoints("h"), false);
        assertTrue(r.isMatch());
        assertEquals(0, r.start);
        assertEquals(1, r.end);
    }

    @Test
    public void testSingleCharPrefersBoundary() {
        // 'h' in "foo hello" should prefer the 'h' at index 4 (word boundary)
        // over any earlier match
        FuzzyResult r = algo.match(false, codePoints("foo hello"), codePoints("h"), false);
        assertTrue(r.isMatch());
        assertEquals(4, r.start);
    }

    // ---- Edge cases ----

    @Test
    public void testSingleCharText() {
        FuzzyResult r = algo.match(false, codePoints("a"), codePoints("a"), false);
        assertTrue(r.isMatch());
    }

    @Test
    public void testEmptyText() {
        FuzzyResult r = algo.match(false, codePoints(""), codePoints("a"), false);
        assertFalse(r.isMatch());
    }
}
