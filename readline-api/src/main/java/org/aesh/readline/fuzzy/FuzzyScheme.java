/*
 * Fuzzy matching scoring scheme, ported from fzf.
 *
 * Original: https://github.com/junegunn/fzf/blob/master/src/algo/algo.go
 * License: MIT (https://github.com/junegunn/fzf/blob/master/LICENSE)
 */
package org.aesh.readline.fuzzy;

/**
 * Scoring constants for fuzzy matching.
 * <p>
 * Three preset schemes are provided matching fzf's {@code --scheme} option:
 * <ul>
 * <li>{@link #DEFAULT} — General-purpose scoring</li>
 * <li>{@link #PATH} — Optimized for file paths (delimiter bonus for path separators)</li>
 * <li>{@link #HISTORY} — Optimized for command history (equalized boundary bonuses)</li>
 * </ul>
 * <p>
 * The scoring parameters are tuned so that the boundary bonus is cancelled
 * when the gap between matching characters grows beyond ~8 characters
 * (approximately the average word length).
 *
 * @see <a href="https://github.com/junegunn/fzf/blob/master/src/algo/algo.go">fzf algo.go</a>
 */
public final class FuzzyScheme {

    // ---- Fixed scoring constants (same across all schemes) ----

    /** Score awarded for each matching character. */
    public static final short SCORE_MATCH = 16;

    /** Penalty for starting a gap (non-matching characters between matches). */
    public static final short SCORE_GAP_START = -3;

    /** Penalty for extending a gap (each additional non-matching character). */
    public static final short SCORE_GAP_EXTENSION = -1;

    /**
     * Bonus for matching at a word boundary (after non-word/delimiter character).
     * Set so that the bonus is cancelled when the gap exceeds ~8 characters.
     */
    public static final short BONUS_BOUNDARY = SCORE_MATCH / 2; // 8

    /** Bonus for matching a non-word character. */
    public static final short BONUS_NON_WORD = SCORE_MATCH / 2; // 8

    /**
     * Bonus for camelCase transitions (lower->UPPER) and number boundaries.
     * Slightly less than boundary bonus since camelCase doesn't have single-char gaps.
     */
    public static final short BONUS_CAMEL_123 = BONUS_BOUNDARY + SCORE_GAP_EXTENSION; // 7

    /**
     * Minimum bonus for consecutive matching characters.
     * Ensures consecutive chunks are ranked higher than gapped matches.
     */
    public static final short BONUS_CONSECUTIVE = (short) -(SCORE_GAP_START + SCORE_GAP_EXTENSION); // 4

    /**
     * Multiplier for the first character's bonus.
     * The first typed character is more significant — users typically
     * start with the most important character.
     */
    public static final short BONUS_FIRST_CHAR_MULTIPLIER = 2;

    // ---- Scheme-specific constants ----

    /** Bonus for word boundary after whitespace (scheme-dependent). */
    public final short bonusBoundaryWhite;

    /** Bonus for word boundary after a delimiter (scheme-dependent). */
    public final short bonusBoundaryDelimiter;

    /**
     * The character class assumed before the first character.
     * WHITE for default/history (beginning of input acts like a word boundary),
     * DELIMITER for path scheme (beginning acts like after a path separator).
     */
    public final int initialCharClass;

    private FuzzyScheme(short bonusBoundaryWhite, short bonusBoundaryDelimiter, int initialCharClass) {
        this.bonusBoundaryWhite = bonusBoundaryWhite;
        this.bonusBoundaryDelimiter = bonusBoundaryDelimiter;
        this.initialCharClass = initialCharClass;
    }

    /**
     * Default scheme — general-purpose fuzzy matching.
     * Whitespace boundaries get a small extra bonus over delimiter boundaries.
     */
    public static final FuzzyScheme DEFAULT = new FuzzyScheme(
            (short) (BONUS_BOUNDARY + 2), // bonusBoundaryWhite = 10
            (short) (BONUS_BOUNDARY + 1), // bonusBoundaryDelimiter = 9
            CharClass.WHITE // initialCharClass
    );

    /**
     * Path scheme — optimized for file path matching.
     * Whitespace and delimiter boundaries are equalized.
     * Path separators get a slight edge.
     * Beginning of input is treated as after a delimiter.
     */
    public static final FuzzyScheme PATH = new FuzzyScheme(
            BONUS_BOUNDARY, // bonusBoundaryWhite = 8
            (short) (BONUS_BOUNDARY + 1), // bonusBoundaryDelimiter = 9
            CharClass.DELIMITER // initialCharClass
    );

    /**
     * History scheme — optimized for command history search.
     * All boundary types get equal bonus since whitespace and delimiters
     * are equally important in command strings.
     */
    public static final FuzzyScheme HISTORY = new FuzzyScheme(
            BONUS_BOUNDARY, // bonusBoundaryWhite = 8
            BONUS_BOUNDARY, // bonusBoundaryDelimiter = 8
            CharClass.WHITE // initialCharClass
    );
}
