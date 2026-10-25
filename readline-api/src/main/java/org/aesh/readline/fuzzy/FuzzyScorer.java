/*
 * High-level fuzzy scoring API for filtering and ranking lists of entries.
 *
 * Provides deduplication, scoring, sorting, and lazy narrowing for
 * interactive search use cases like fuzzy history search.
 */
package org.aesh.readline.fuzzy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import org.aesh.terminal.utils.Parser;

/**
 * High-level API for fuzzy filtering and ranking of text entries.
 * <p>
 * Use this class to filter a list of history entries (or any text entries)
 * against a fuzzy pattern. Results are sorted by score (descending),
 * with ties broken by recency (most recent first).
 * <p>
 * The scorer supports:
 * <ul>
 * <li>Case-insensitive matching (default)</li>
 * <li>Deduplication of identical entries (most recent kept)</li>
 * <li>Lazy narrowing: when a character is appended to the query,
 * only the previous result set is re-filtered instead of the full list</li>
 * </ul>
 * <p>
 * Not thread-safe — use one instance per search session.
 *
 * @see FuzzyAlgo
 * @see FuzzyScheme
 */
public final class FuzzyScorer {

    private final FuzzyAlgo algo;
    private final boolean caseSensitive;

    /**
     * Create a scorer with the given scheme and case sensitivity.
     *
     * @param scheme the scoring scheme
     * @param caseSensitive whether matching is case-sensitive
     */
    public FuzzyScorer(FuzzyScheme scheme, boolean caseSensitive) {
        this.algo = new FuzzyAlgo(scheme);
        this.caseSensitive = caseSensitive;
    }

    /**
     * Create a case-insensitive scorer with the given scheme.
     *
     * @param scheme the scoring scheme
     */
    public FuzzyScorer(FuzzyScheme scheme) {
        this(scheme, false);
    }

    /**
     * Score a single text entry against a pattern.
     *
     * @param text the text as code points
     * @param pattern the search pattern as code points
     * @param withPos whether to compute match positions
     * @return the match result
     */
    public FuzzyResult score(int[] text, int[] pattern, boolean withPos) {
        if (pattern.length == 0) {
            return new FuzzyResult(0, 0, 0, withPos ? new int[0] : null);
        }

        // Normalize pattern to lowercase if case-insensitive
        int[] normalizedPattern = caseSensitive ? pattern : toLower(pattern);
        return algo.match(caseSensitive, text, normalizedPattern, withPos);
    }

    /**
     * Filter and rank a list of entries against a pattern.
     * <p>
     * Entries are deduplicated (most recent occurrence kept),
     * scored against the pattern, filtered to matches only,
     * and sorted by score descending (ties broken by recency).
     *
     * @param entries the entries to search (most recent last, as from History.getAll())
     * @param pattern the search pattern as code points
     * @param withPos whether to compute match positions for each entry
     * @return sorted list of scored entries (best match first)
     */
    public List<ScoredEntry> scoreAll(List<int[]> entries, int[] pattern, boolean withPos) {
        return scoreAll(entries, null, pattern, withPos);
    }

    /**
     * Filter and rank a list of entries against a pattern, with timestamps.
     *
     * @param entries the entries to search (most recent last, as from History.getAll())
     * @param timestamps parallel list of timestamps (epoch millis), or null
     * @param pattern the search pattern as code points
     * @param withPos whether to compute match positions for each entry
     * @return sorted list of scored entries (best match first)
     */
    public List<ScoredEntry> scoreAll(List<int[]> entries, List<Long> timestamps,
            int[] pattern, boolean withPos) {
        if (entries == null || entries.isEmpty()) {
            return Collections.emptyList();
        }

        // Normalize pattern
        int[] normalizedPattern = caseSensitive ? pattern : toLower(pattern);

        // Deduplicate: keep most recent occurrence of each unique entry
        // Also carry through timestamps
        List<int[]> deduped = new ArrayList<>();
        List<Long> dedupedTimestamps = new ArrayList<>();
        deduplicateWithTimestamps(entries, timestamps, deduped, dedupedTimestamps);

        // Score each entry
        List<ScoredEntry> results = new ArrayList<>();
        for (int i = 0; i < deduped.size(); i++) {
            int[] text = deduped.get(i);
            FuzzyResult result;

            if (normalizedPattern.length == 0) {
                // Empty pattern matches everything with score 0
                result = new FuzzyResult(0, 0, 0, withPos ? new int[0] : null);
            } else {
                result = algo.match(caseSensitive, text, normalizedPattern, withPos);
            }

            if (result.isMatch() || normalizedPattern.length == 0) {
                long ts = dedupedTimestamps.isEmpty() ? -1 : dedupedTimestamps.get(i);
                results.add(new ScoredEntry(i, text, result, ts));
            }
        }

        // Sort by score descending, then by index ascending (most recent first)
        results.sort((a, b) -> {
            int cmp = Integer.compare(b.match.score, a.match.score);
            if (cmp != 0)
                return cmp;
            return Integer.compare(a.index, b.index);
        });

        return results;
    }

    /**
     * Narrow a previous result set by scoring against a longer pattern.
     * <p>
     * This is an optimization for interactive typing: when the user appends
     * a character to the query, we only need to re-score the entries that
     * already matched the shorter pattern.
     *
     * @param previousResults the results from the previous (shorter) pattern
     * @param pattern the new (longer) pattern as code points
     * @param withPos whether to compute match positions
     * @return narrowed and re-sorted results
     */
    public List<ScoredEntry> narrow(List<ScoredEntry> previousResults, int[] pattern, boolean withPos) {
        if (previousResults == null || previousResults.isEmpty()) {
            return Collections.emptyList();
        }

        int[] normalizedPattern = caseSensitive ? pattern : toLower(pattern);

        List<ScoredEntry> results = new ArrayList<>();
        for (ScoredEntry prev : previousResults) {
            FuzzyResult result = algo.match(caseSensitive, prev.text, normalizedPattern, withPos);
            if (result.isMatch()) {
                results.add(new ScoredEntry(prev.index, prev.text, result));
            }
        }

        results.sort((a, b) -> {
            int cmp = Integer.compare(b.match.score, a.match.score);
            if (cmp != 0)
                return cmp;
            return Integer.compare(a.index, b.index);
        });

        return results;
    }

    /**
     * Deduplicate entries, keeping only the most recent occurrence.
     * Input order: oldest first (index 0) to most recent (last index).
     * Output order: most recent first (for display).
     *
     * @param entries the entries to deduplicate (oldest first)
     * @return deduplicated list with most recent entries first
     */
    public static List<int[]> deduplicate(List<int[]> entries) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<int[]> result = new ArrayList<>();

        for (int i = entries.size() - 1; i >= 0; i--) {
            String key = Parser.fromCodePoints(entries.get(i));
            if (seen.add(key)) {
                result.add(entries.get(i));
            }
        }
        return result;
    }

    /**
     * Deduplicate entries with timestamps, keeping most recent occurrence.
     */
    private static void deduplicateWithTimestamps(List<int[]> entries, List<Long> timestamps,
            List<int[]> outEntries, List<Long> outTimestamps) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();

        for (int i = entries.size() - 1; i >= 0; i--) {
            String key = Parser.fromCodePoints(entries.get(i));
            if (seen.add(key)) {
                outEntries.add(entries.get(i));
                if (timestamps != null && i < timestamps.size()) {
                    outTimestamps.add(timestamps.get(i));
                }
            }
        }
    }

    /**
     * Convert code points to lowercase.
     */
    private static int[] toLower(int[] codePoints) {
        int[] result = new int[codePoints.length];
        for (int i = 0; i < codePoints.length; i++) {
            result[i] = Character.toLowerCase(codePoints[i]);
        }
        return result;
    }

    /**
     * A scored entry: an input text with its fuzzy match result and original index.
     */
    public static final class ScoredEntry {
        /** Index in the deduplicated list (0 = most recent). */
        public final int index;
        /** The entry text as code points. */
        public final int[] text;
        /** The fuzzy match result. */
        public final FuzzyResult match;
        /** Timestamp (epoch millis) when the entry was added, or -1 if unknown. */
        public final long timestamp;

        /**
         * Create a scored entry with no timestamp.
         *
         * @param index index in the deduplicated list (0 = most recent)
         * @param text the entry text as code points
         * @param match the fuzzy match result
         */
        public ScoredEntry(int index, int[] text, FuzzyResult match) {
            this(index, text, match, -1);
        }

        /**
         * Create a scored entry with a timestamp.
         *
         * @param index index in the deduplicated list (0 = most recent)
         * @param text the entry text as code points
         * @param match the fuzzy match result
         * @param timestamp epoch millis when the entry was added, or -1 if unknown
         */
        public ScoredEntry(int index, int[] text, FuzzyResult match, long timestamp) {
            this.index = index;
            this.text = text;
            this.match = match;
            this.timestamp = timestamp;
        }
    }
}
