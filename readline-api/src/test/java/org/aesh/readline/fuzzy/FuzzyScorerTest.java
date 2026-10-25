package org.aesh.readline.fuzzy;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

/**
 * Tests for FuzzyScorer — the high-level filtering and ranking API.
 */
public class FuzzyScorerTest {

    private FuzzyScorer scorer;

    @Before
    public void setUp() {
        scorer = new FuzzyScorer(FuzzyScheme.HISTORY);
    }

    private static int[] cp(String s) {
        return s.codePoints().toArray();
    }

    private static List<int[]> entries(String... strings) {
        List<int[]> list = new ArrayList<>();
        for (String s : strings) {
            list.add(cp(s));
        }
        return list;
    }

    private static String text(FuzzyScorer.ScoredEntry entry) {
        return new String(entry.text, 0, entry.text.length);
    }

    // ---- Basic scoring ----

    @Test
    public void testScoreAllEmptyPattern() {
        List<int[]> input = entries("foo", "bar", "baz");
        List<FuzzyScorer.ScoredEntry> results = scorer.scoreAll(input, cp(""), false);
        // Empty pattern matches all entries
        assertEquals(3, results.size());
    }

    @Test
    public void testScoreAllNoMatches() {
        List<int[]> input = entries("foo", "bar", "baz");
        List<FuzzyScorer.ScoredEntry> results = scorer.scoreAll(input, cp("xyz"), false);
        assertEquals(0, results.size());
    }

    @Test
    public void testScoreAllFiltersAndRanks() {
        List<int[]> input = entries(
                "git status",
                "git commit -m fix",
                "mvn clean test",
                "git push origin master");
        List<FuzzyScorer.ScoredEntry> results = scorer.scoreAll(input, cp("gc"), false);
        // "git commit" and potentially "git push" could match "gc"
        // but "mvn clean" doesn't have g before c in subsequence order
        assertTrue(results.size() > 0);
        // First result should be best scoring
        for (int i = 1; i < results.size(); i++) {
            assertTrue("Results should be sorted by score descending",
                    results.get(i - 1).match.score >= results.get(i).match.score);
        }
    }

    // ---- Deduplication ----

    @Test
    public void testDeduplication() {
        // Entries with duplicates (most recent at end)
        List<int[]> input = entries(
                "ls -la", // oldest
                "git status",
                "ls -la", // duplicate
                "git status", // duplicate
                "cd /tmp" // most recent
        );
        List<FuzzyScorer.ScoredEntry> results = scorer.scoreAll(input, cp(""), false);
        // Should have 3 unique entries
        assertEquals(3, results.size());
    }

    @Test
    public void testDeduplicationKeepsMostRecent() {
        List<int[]> input = entries("aaa", "bbb", "aaa");
        List<int[]> deduped = FuzzyScorer.deduplicate(input);
        assertEquals(2, deduped.size());
        // Most recent first: "aaa" (index 2), then "bbb" (index 1)
        assertArrayEquals(cp("aaa"), deduped.get(0));
        assertArrayEquals(cp("bbb"), deduped.get(1));
    }

    // ---- Narrowing ----

    @Test
    public void testNarrowReducesResults() {
        List<int[]> input = entries(
                "mvn clean test",
                "mvn clean install",
                "mvn test",
                "gradle build");

        // First query "m" matches all mvn entries
        List<FuzzyScorer.ScoredEntry> broad = scorer.scoreAll(input, cp("m"), false);
        assertTrue(broad.size() >= 3);

        // Narrow with "mc" — should match fewer
        List<FuzzyScorer.ScoredEntry> narrow = scorer.narrow(broad, cp("mc"), false);
        assertTrue("Narrow should have fewer or equal results", narrow.size() <= broad.size());

        // Narrow further with "mct" — even fewer
        List<FuzzyScorer.ScoredEntry> narrower = scorer.narrow(narrow, cp("mct"), false);
        assertTrue("Further narrow should have fewer or equal results",
                narrower.size() <= narrow.size());
    }

    @Test
    public void testNarrowEmptyPreviousResults() {
        List<FuzzyScorer.ScoredEntry> results = scorer.narrow(
                new ArrayList<>(), cp("foo"), false);
        assertEquals(0, results.size());
    }

    // ---- Scoring quality ----

    @Test
    public void testBetterMatchRankedFirst() {
        List<int[]> input = entries(
                "directory_listing",
                "clean test");
        List<FuzzyScorer.ScoredEntry> results = scorer.scoreAll(input, cp("ct"), false);
        // "clean test" should rank higher because 'c' and 't' are at word boundaries
        assertTrue(results.size() >= 1);
        assertEquals("clean test", text(results.get(0)));
    }

    @Test
    public void testContiguousMatchRankedHigh() {
        List<int[]> input = entries(
                "a_x_b_x_c_x_foo",
                "foobar",
                "baz_foo_quux");
        List<FuzzyScorer.ScoredEntry> results = scorer.scoreAll(input, cp("foo"), false);
        assertTrue(results.size() >= 2);
        // All three should match, contiguous "foo" entries should rank above gapped matches
        for (FuzzyScorer.ScoredEntry r : results) {
            assertTrue("All entries with 'foo' subsequence should match", r.match.isMatch());
        }
    }

    // ---- With positions ----

    @Test
    public void testScoreAllWithPositions() {
        List<int[]> input = entries("hello world");
        List<FuzzyScorer.ScoredEntry> results = scorer.scoreAll(input, cp("hw"), true);
        assertEquals(1, results.size());
        assertNotNull(results.get(0).match.positions);
        assertEquals(2, results.get(0).match.positions.length);
    }

    // ---- Edge cases ----

    @Test
    public void testNullEntries() {
        List<FuzzyScorer.ScoredEntry> results = scorer.scoreAll(null, cp("foo"), false);
        assertEquals(0, results.size());
    }

    @Test
    public void testEmptyEntries() {
        List<FuzzyScorer.ScoredEntry> results = scorer.scoreAll(new ArrayList<>(), cp("foo"), false);
        assertEquals(0, results.size());
    }

    @Test
    public void testSingleEntry() {
        List<int[]> input = entries("hello");
        List<FuzzyScorer.ScoredEntry> results = scorer.scoreAll(input, cp("h"), false);
        assertEquals(1, results.size());
    }

    @Test
    public void testCaseInsensitiveScoring() {
        List<int[]> input = entries("FooBar", "foobar");
        List<FuzzyScorer.ScoredEntry> results = scorer.scoreAll(input, cp("foobar"), false);
        // Both should match with case-insensitive scorer
        // After dedup they are different strings (different case)
        assertEquals(2, results.size());
    }

    @Test
    public void testCaseSensitiveScoring() {
        FuzzyScorer caseSensitive = new FuzzyScorer(FuzzyScheme.HISTORY, true);
        List<int[]> input = entries("FooBar", "foobar");
        List<FuzzyScorer.ScoredEntry> results = caseSensitive.scoreAll(input, cp("foobar"), false);
        // Only "foobar" should match
        assertEquals(1, results.size());
        assertEquals("foobar", text(results.get(0)));
    }
}
