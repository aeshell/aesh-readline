/*
 * Fuzzy matching result, ported from fzf.
 *
 * Original: https://github.com/junegunn/fzf/blob/master/src/algo/algo.go
 * License: MIT (https://github.com/junegunn/fzf/blob/master/LICENSE)
 */
package org.aesh.readline.fuzzy;

/**
 * Result of a fuzzy match operation.
 * <p>
 * Contains the score, the start/end indices of the matched substring
 * within the input text, and optionally the positions of each matched
 * character (for match highlighting).
 */
public final class FuzzyResult {

    /** No match found. */
    public static final FuzzyResult NO_MATCH = new FuzzyResult(-1, -1, 0, null);

    /** Start index of the matched region in the input (inclusive). */
    public final int start;

    /** End index of the matched region in the input (exclusive). */
    public final int end;

    /** Match score. Higher is better. 0 or negative means no match. */
    public final int score;

    /**
     * Positions of each matched character in the input.
     * May be null if positions were not requested.
     * When non-null, {@code positions.length == pattern.length}.
     */
    public final int[] positions;

    public FuzzyResult(int start, int end, int score, int[] positions) {
        this.start = start;
        this.end = end;
        this.score = score;
        this.positions = positions;
    }

    /**
     * Returns true if this result represents a match.
     */
    public boolean isMatch() {
        return start >= 0;
    }
}
